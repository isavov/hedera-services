/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.mono.state.initialization;

import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_BLOCKLIST_PATH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BOOTSTRAP_SYSTEM_ENTITY_EXPIRY;
import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.node.app.service.mono.config.AccountNumbers;
import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;

/**
 * Encapsulates the logic for reading blocked accounts from file and creating them.
 */
@Singleton
public class BlocklistAccountCreator {
    private static final Logger log = LogManager.getLogger(BlocklistAccountCreator.class);
    private final String blocklistResourcePath;
    private final Supplier<HederaAccount> accountSupplier;
    private final EntityIdSource ids;
    private final BackingStore<AccountID, HederaAccount> accounts;
    private final Supplier<JEd25519Key> genesisKeySource;
    private final PropertySource properties;
    private final AliasManager aliasManager;
    private JKey genesisKey;
    private final List<HederaAccount> accountsCreated = new ArrayList<>();
    private AccountNumbers accountNumbers;
    private static String DEFAULT_BLOCKLIST_RESOURCE = "evm-addresses-blocklist.csv";

    @Inject
    public BlocklistAccountCreator(
            final @NonNull Supplier<HederaAccount> accountSupplier,
            final @NonNull EntityIdSource ids,
            final @NonNull BackingStore<AccountID, HederaAccount> accounts,
            final @NonNull Supplier<JEd25519Key> genesisKeySource,
            final @NonNull @CompositeProps PropertySource properties,
            final @NonNull AliasManager aliasManager,
            final @NonNull AccountNumbers accountNumbers) {
        this.blocklistResourcePath = properties.getStringProperty(ACCOUNTS_BLOCKLIST_PATH);
        this.accountSupplier = Objects.requireNonNull(accountSupplier);
        this.ids = Objects.requireNonNull(ids);
        this.accounts = Objects.requireNonNull(accounts);
        this.genesisKeySource = Objects.requireNonNull(genesisKeySource);
        this.properties = Objects.requireNonNull(properties);
        this.aliasManager = Objects.requireNonNull(aliasManager);
        this.accountNumbers = Objects.requireNonNull(accountNumbers);
    }

    /**
     * Makes sure that all blocked accounts contained in the blocklist resource are present in state, and creates them if necessary.
     */
    public void createMissingAccounts() {
        final List<String> fileLines = readFileLines();
        if (fileLines.isEmpty()) return;

        final List<BlockedInfo> blocklist = parseBlockList(fileLines);

        if (!blocklist.isEmpty()) {
            final var blockedToCreate = blocklist.stream()
                    .filter(blockedAccount ->
                            aliasManager.lookupIdBy(blockedAccount.evmAddress).equals(MISSING_NUM))
                    .collect(Collectors.toSet());

            for (final var blockedInfo : blockedToCreate) {
                final var newId = ids.newAccountId(); // get the next available new account ID
                final var account = blockedAccountWith(blockedInfo);
                accounts.put(newId, account); // add the account with the corresponding newId to state
                accountsCreated.add(account); // add the account to the list of accounts created by this class
                aliasManager.link(
                        blockedInfo.evmAddress,
                        EntityNum.fromAccountId(newId)); // link the EVM address alias to the new account ID
            }
        }
    }

    private List<String> readFileLines() {
        try {
            return readPrivateKeyBlocklistFromPath(blocklistResourcePath);
        } catch (BlocklistNotFoundException e) {
            log.error("Failed to read blocklist file {}", blocklistResourcePath, e);
        }

        try {
            return readPrivateKeyBlocklistFromResource(DEFAULT_BLOCKLIST_RESOURCE);
        } catch (BlocklistNotFoundException e) {
            log.error("Failed to read blocklist resource {}", DEFAULT_BLOCKLIST_RESOURCE, e);
        }

        return Collections.emptyList();
    }

    private List<BlockedInfo> parseBlockList(final List<String> fileLines) {
        final List<BlockedInfo> blocklist;
        try {
            final var columnHeaderLine = fileLines.get(0);
            final var blocklistLines = fileLines.subList(1, fileLines.size());
            final var columnCount = columnHeaderLine.split(",").length;
            blocklist = blocklistLines.stream()
                    .map(line -> parseCSVLine(line, columnCount))
                    .toList();
        } catch (IllegalArgumentException iae) {
            log.error("Failed to parse blocklist", iae);
            return Collections.emptyList();
        }
        return blocklist;
    }

    @NonNull
    private List<String> readPrivateKeyBlocklistFromPath(String blocklistFilePath) throws BlocklistNotFoundException {
        log.info("Bootstrapping blocklist from '{}'", blocklistFilePath);
        try (var externalResourceStream = Files.newInputStream(Paths.get(blocklistFilePath));
                var externalResourceReader = new BufferedReader(new InputStreamReader(externalResourceStream))) {
            return externalResourceReader.lines().toList();
        } catch (final IOException e) {
            throw new BlocklistNotFoundException(
                    String.format("Could not read external properties file %s", blocklistFilePath), e);
        }
    }

    @NonNull
    private List<String> readPrivateKeyBlocklistFromResource(String blocklistResource)
            throws BlocklistNotFoundException {
        log.info("Bootstrapping blocklist from resource '{}'", blocklistResource);
        try (final var inputStream = getClass().getClassLoader().getResourceAsStream(blocklistResource)) {
            if (inputStream != null) {
                final var reader = new BufferedReader(new InputStreamReader(inputStream));
                return reader.lines().toList();
            } else {
                log.warn("Could not find resource {}.", blocklistResource);
                return Collections.emptyList();
            }
        } catch (final IOException e) {
            throw new BlocklistNotFoundException(
                    String.format("Could not read from resource %s", blocklistResource), e);
        }
    }

    /**
     * Parses a line from the blocklist resource and returns blocked account info record.
     *
     * The line should have the following format:
     * <private key>,<memo>
     *     where <private key> is a hex-encoded private key
     *     and <memo> is a memo for the blocked account
     *     and both values are comma-separated.
     *
     * The resulting blocked account info record contains the EVM address derived from the private key, and the memo.
     *
     * @param line line from the blocklist resource
     * @param columnCount number of comma-separated values in a line
     * @return blocked account info record
     */
    @NonNull
    private BlockedInfo parseCSVLine(final @NonNull String line, int columnCount) {
        final var parts = line.split(",", -1);
        if (parts.length != columnCount) {
            throw new IllegalArgumentException("Invalid line in blocklist resource: " + line);
        }

        final byte[] privateKeyBytes;
        try {
            privateKeyBytes = HexFormat.of().parseHex(parts[0]);
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("Failed to decode line " + line, iae);
        }

        final var publicKeyBytes = ecdsaPrivateToPublicKey(privateKeyBytes);
        final var evmAddressBytes = EthSigsUtils.recoverAddressFromPubKey(publicKeyBytes);
        return new BlockedInfo(ByteString.copyFrom(evmAddressBytes), parts[1]);
    }

    /**
     * Creates a blocked Hedera account with the given memo and EVM address.
     * A blocked account has receiverSigRequired flag set to true, key set to the genesis key, and balance set to 0.
     *
     * @param blockedInfo record containing EVM address and memo for the blocked account
     * @return a Hedera account with the given memo and EVM address
     */
    @NonNull
    private HederaAccount blockedAccountWith(final @NonNull BlockedInfo blockedInfo) {
        final var expiry = properties.getLongProperty(BOOTSTRAP_SYSTEM_ENTITY_EXPIRY);
        var customizer = new HederaAccountCustomizer()
                .isReceiverSigRequired(true)
                .isDeclinedReward(true)
                .isDeleted(false)
                .expiry(expiry)
                .isSmartContract(false)
                .key(getGenesisKey())
                .autoRenewPeriod(expiry)
                .alias(blockedInfo.evmAddress);

        if (!blockedInfo.memo.isEmpty()) {
            customizer.memo(blockedInfo.memo);
        }

        return customizer.customizing(accountSupplier.get());
    }

    private JKey getGenesisKey() {
        if (genesisKey == null) {
            // Traditionally the genesis key has been a key list, keep that way to avoid breaking
            // any clients
            genesisKey = asFcKeyUnchecked(Key.newBuilder()
                    .setKeyList(KeyList.newBuilder().addKeys(asKeyUnchecked(genesisKeySource.get())))
                    .build());
        }
        return genesisKey;
    }

    /**
     * Derives the ECDSA public key bytes from the given ECDSA private key bytes.
     *
     * @param privateKeyBytes ECDSA private key bytes
     * @return ECDSA public key bytes
     */
    private byte[] ecdsaPrivateToPublicKey(byte[] privateKeyBytes) {
        final var ecdsaSecp256K1Curve = SECNamedCurves.getByName("secp256k1");
        final var ecdsaSecp256K1Domain = new ECDomainParameters(
                ecdsaSecp256K1Curve.getCurve(),
                ecdsaSecp256K1Curve.getG(),
                ecdsaSecp256K1Curve.getN(),
                ecdsaSecp256K1Curve.getH());
        final var privateKeyData = new BigInteger(1, privateKeyBytes);
        var q = ecdsaSecp256K1Domain.getG().multiply(privateKeyData);
        var publicParams = new ECPublicKeyParameters(q, ecdsaSecp256K1Domain);
        return publicParams.getQ().getEncoded(true);
    }

    /**
     * Returns a list of {@link HederaAccount}, denoting all the blocked accounts created by a previous call to {@link BlocklistAccountCreator#createMissingAccounts()}.
     *
     * @return a list of blocked accounts created during the current run of the node
     */
    public List<HederaAccount> getBlockedAccountsCreated() {
        return accountsCreated;
    }

    /**
     * Clears the list of blocked accounts created by a previous call to {@link BlocklistAccountCreator#createMissingAccounts()}
     */
    public void forgetCreatedBlockedAccounts() {
        accountsCreated.clear();
    }

    /**
     * @param evmAddress the EVM address of the blocked account
     * @param memo      the memo of the blocked account
     */
    record BlockedInfo(@NonNull ByteString evmAddress, @NonNull String memo) {}
}
