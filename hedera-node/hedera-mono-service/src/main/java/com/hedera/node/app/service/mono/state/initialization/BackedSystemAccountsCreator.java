/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.mono.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BOOTSTRAP_SYSTEM_ENTITY_EXPIRY;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_NUM_SYSTEM_ACCOUNTS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static com.hedera.node.app.service.mono.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;

import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.exceptions.NegativeAccountBalanceException;
import com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class BackedSystemAccountsCreator implements SystemAccountsCreator {

    private static final Logger log = LogManager.getLogger(BackedSystemAccountsCreator.class);

    public static final long FUNDING_ACCOUNT_EXPIRY = 33197904000L;
    private static final int ZERO_BALANCE = 0;

    private final HederaAccountNumbers accountNums;
    private final PropertySource properties;
    private final Supplier<JEd25519Key> genesisKeySource;
    private final TreasuryCloner treasuryCloner;
    private final Supplier<HederaAccount> accountSupplier;

    private JKey genesisKey;
    private final List<HederaAccount> systemAccountsCreated = new ArrayList<>();
    private final List<HederaAccount> stakingFundAccountsCreated = new ArrayList<>();

    @Inject
    public BackedSystemAccountsCreator(
            final HederaAccountNumbers accountNums,
            final @CompositeProps PropertySource properties,
            final Supplier<JEd25519Key> genesisKeySource,
            final Supplier<HederaAccount> accountSupplier,
            final TreasuryCloner treasuryCloner) {
        this.accountNums = accountNums;
        this.properties = properties;
        this.genesisKeySource = genesisKeySource;
        this.accountSupplier = accountSupplier;
        this.treasuryCloner = treasuryCloner;
    }

    /** {@inheritDoc} */
    @Override
    public void ensureSystemAccounts(
            final BackingStore<AccountID, HederaAccount> accounts, final AddressBook addressBook) {
        internalEnsureSystemAccounts(accounts);
    }

    @Override
    public void ensureSynthRecordsPresentOnFirstEverTransaction() {
        // Called during the first-ever handleTransaction(); if we don't have a record of creating
        // system accounts, then we're doing a event replay from a saved round one state, and need
        // to ensure we have pending synthetic records ready to export
        if (systemAccountsCreated.isEmpty()) {
            // Pass null here so we don't actually create any accounts, but just ensure we have
            // "prepared" synthetic records of all creations that happen during genesis init
            internalEnsureSystemAccounts(null);
        }
    }

    private void internalEnsureSystemAccounts(final @Nullable BackingStore<AccountID, HederaAccount> accounts) {
        final long systemAccounts = properties.getIntProperty(LEDGER_NUM_SYSTEM_ACCOUNTS);
        final long expiry = properties.getLongProperty(BOOTSTRAP_SYSTEM_ENTITY_EXPIRY);
        final long tinyBarFloat = properties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT);
        final var shouldSkipExtantAndCreateMissing = accounts != null;

        for (long num = 1; num <= systemAccounts; num++) {
            final var id = STATIC_PROPERTIES.scopedAccountWith(num);
            if (shouldSkipExtantAndCreateMissing && accounts.contains(id)) {
                continue;
            }
            final HederaAccount account;
            if (num == accountNums.treasury()) {
                account = accountWith(tinyBarFloat, expiry);
            } else {
                account = accountWith(ZERO_BALANCE, expiry);
            }
            if (shouldSkipExtantAndCreateMissing) {
                accounts.put(id, account);
            } else {
                account.setEntityNum(EntityNum.fromLong(num));
            }
            systemAccountsCreated.add(account);
        }

        final var stakingRewardAccountNum = accountNums.stakingRewardAccount();
        final var stakingRewardAccountId = STATIC_PROPERTIES.scopedAccountWith(stakingRewardAccountNum);
        final var nodeRewardAccountNum = accountNums.nodeRewardAccount();
        final var nodeRewardAccountId = STATIC_PROPERTIES.scopedAccountWith(nodeRewardAccountNum);
        final var stakingFundAccounts = List.of(stakingRewardAccountId, nodeRewardAccountId);
        for (final var id : stakingFundAccounts) {
            final var stakingFundAccount = accountSupplier.get();
            customizeAsStakingFund(stakingFundAccount);
            if (shouldSkipExtantAndCreateMissing) {
                if (!accounts.contains(id)) {
                    accounts.put(id, stakingFundAccount);
                    stakingFundAccountsCreated.add(stakingFundAccount);
                }
            } else {
                stakingFundAccount.setEntityNum(EntityNum.fromLong(id.getAccountNum()));
                stakingFundAccountsCreated.add(stakingFundAccount);
            }
        }
        for (long num = 900; num <= 1000; num++) {
            final var id = STATIC_PROPERTIES.scopedAccountWith(num);
            final var account = accountWith(ZERO_BALANCE, expiry);
            if (shouldSkipExtantAndCreateMissing) {
                if (!accounts.contains(id)) {
                    accounts.put(id, account);
                    systemAccountsCreated.add(account);
                }
            } else {
                account.setEntityNum(EntityNum.fromLong(num));
                systemAccountsCreated.add(account);
            }
        }

        treasuryCloner.ensureTreasuryClonesExist(shouldSkipExtantAndCreateMissing);
    }

    public static void customizeAsStakingFund(final HederaAccount account) {
        account.setExpiry(FUNDING_ACCOUNT_EXPIRY);
        account.setAccountKey(EMPTY_KEY);
        account.setSmartContract(false);
        account.setReceiverSigRequired(false);
        account.setMaxAutomaticAssociations(0);
    }

    private HederaAccount accountWith(final long balance, final long expiry) {
        final var account = new HederaAccountCustomizer()
                .isReceiverSigRequired(false)
                .isDeleted(false)
                .expiry(expiry)
                .memo("")
                .isSmartContract(false)
                .key(getGenesisKey())
                .autoRenewPeriod(expiry)
                .customizing(accountSupplier.get());
        try {
            account.setBalance(balance);
        } catch (final NegativeAccountBalanceException e) {
            throw new IllegalStateException(e);
        }
        return account;
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

    public List<HederaAccount> getSystemAccountsCreated() {
        return systemAccountsCreated;
    }

    public List<HederaAccount> getStakingFundAccountsCreated() {
        return stakingFundAccountsCreated;
    }

    public List<HederaAccount> getTreasuryClonesCreated() {
        return treasuryCloner.getClonesCreated();
    }

    public void forgetCreations() {
        treasuryCloner.forgetCreatedClones();
        systemAccountsCreated.clear();
        stakingFundAccountsCreated.clear();
    }
}
