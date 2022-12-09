/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.contracts.sources;

import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.keys.ActivationTest;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKeyList;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import java.util.function.BiPredicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;

@Singleton
public class TxnAwareEvmSigsVerifier implements EvmSigsVerifier {
    private final ActivationTest activationTest;
    private final TransactionContext txnCtx;
    private final BiPredicate<JKey, TransactionSignature> cryptoValidity;

    @Inject
    public TxnAwareEvmSigsVerifier(
            final ActivationTest activationTest,
            final TransactionContext txnCtx,
            final BiPredicate<JKey, TransactionSignature> cryptoValidity) {
        this.txnCtx = txnCtx;
        this.activationTest = activationTest;
        this.cryptoValidity = cryptoValidity;
    }

    @Override
    public boolean hasActiveKey(
            final boolean isDelegateCall,
            @NonNull final Address accountAddress,
            @NonNull final Address activeContract,
            @NonNull final WorldLedgers worldLedgers) {
        final var accountId = EntityIdUtils.accountIdFromEvmAddress(accountAddress);
        validateTrue(worldLedgers.accounts().exists(accountId), INVALID_ACCOUNT_ID);

        if (accountAddress.equals(activeContract)) {
            return true;
        }

        final var accountKey = (JKey) worldLedgers.accounts().get(accountId, KEY);
        return accountKey != null
                && isActiveInFrame(
                        accountKey, isDelegateCall, activeContract, worldLedgers.aliases());
    }

    @Override
    public boolean hasActiveSupplyKey(
            final boolean isDelegateCall,
            @NonNull final Address tokenAddress,
            @NonNull final Address activeContract,
            @NonNull final WorldLedgers worldLedgers) {
        final var tokenId = EntityIdUtils.tokenIdFromEvmAddress(tokenAddress);
        validateTrue(worldLedgers.tokens().exists(tokenId), INVALID_TOKEN_ID);

        final var supplyKey = (JKey) worldLedgers.tokens().get(tokenId, TokenProperty.SUPPLY_KEY);
        validateTrue(supplyKey != null, TOKEN_HAS_NO_SUPPLY_KEY);

        return isActiveInFrame(supplyKey, isDelegateCall, activeContract, worldLedgers.aliases());
    }

    @Override
    public boolean hasActiveKycKey(
            final boolean isDelegateCall,
            @NonNull final Address tokenAddress,
            @NonNull final Address activeContract,
            @NonNull final WorldLedgers worldLedgers) {
        final var tokenId = EntityIdUtils.tokenIdFromEvmAddress(tokenAddress);
        validateTrue(worldLedgers.tokens().exists(tokenId), INVALID_TOKEN_ID);

        final var kycKey = (JKey) worldLedgers.tokens().get(tokenId, TokenProperty.KYC_KEY);
        validateTrue(kycKey != null, TOKEN_HAS_NO_KYC_KEY);

        return isActiveInFrame(kycKey, isDelegateCall, activeContract, worldLedgers.aliases());
    }

    @Override
    public boolean hasActivePauseKey(
            final boolean isDelegateCall,
            @NonNull final Address tokenAddress,
            @NonNull final Address activeContract,
            @NonNull final WorldLedgers worldLedgers) {
        final var tokenId = EntityIdUtils.tokenIdFromEvmAddress(tokenAddress);
        validateTrue(worldLedgers.tokens().exists(tokenId), INVALID_TOKEN_ID);

        final var pauseKey = (JKey) worldLedgers.tokens().get(tokenId, TokenProperty.PAUSE_KEY);
        validateTrue(pauseKey != null, TOKEN_HAS_NO_PAUSE_KEY);

        return isActiveInFrame(pauseKey, isDelegateCall, activeContract, worldLedgers.aliases());
    }

    @Override
    public boolean hasActiveWipeKey(
            final boolean isDelegateCall,
            @NonNull final Address tokenAddress,
            @NonNull final Address activeContract,
            @NonNull final WorldLedgers worldLedgers) {
        final var tokenId = EntityIdUtils.tokenIdFromEvmAddress(tokenAddress);
        validateTrue(worldLedgers.tokens().exists(tokenId), INVALID_TOKEN_ID);

        final var wipeKey = (JKey) worldLedgers.tokens().get(tokenId, TokenProperty.WIPE_KEY);
        validateTrue(wipeKey != null, TOKEN_HAS_NO_WIPE_KEY);

        return isActiveInFrame(wipeKey, isDelegateCall, activeContract, worldLedgers.aliases());
    }

    @Override
    public boolean hasActiveFreezeKey(
            final boolean isDelegateCall,
            @NonNull final Address tokenAddress,
            @NonNull final Address activeContract,
            @NonNull final WorldLedgers worldLedgers) {
        final var tokenId = EntityIdUtils.tokenIdFromEvmAddress(tokenAddress);
        validateTrue(worldLedgers.tokens().exists(tokenId), INVALID_TOKEN_ID);

        final var freezeKey = (JKey) worldLedgers.tokens().get(tokenId, TokenProperty.FREEZE_KEY);
        validateTrue(freezeKey != null, TOKEN_HAS_NO_FREEZE_KEY);

        return isActiveInFrame(freezeKey, isDelegateCall, activeContract, worldLedgers.aliases());
    }

    @Override
    public boolean hasActiveAdminKey(
            final boolean isDelegateCall,
            @NonNull final Address tokenAddress,
            @NonNull final Address activeContract,
            @NonNull final WorldLedgers worldLedgers) {
        final var tokenId = EntityIdUtils.tokenIdFromEvmAddress(tokenAddress);
        validateTrue(worldLedgers.tokens().exists(tokenId), INVALID_TOKEN_ID);

        final var adminKey = (JKey) worldLedgers.tokens().get(tokenId, TokenProperty.ADMIN_KEY);
        validateTrue(adminKey != null, TOKEN_IS_IMMUTABLE);

        return isActiveInFrame(adminKey, isDelegateCall, activeContract, worldLedgers.aliases());
    }

    @Override
    public boolean hasActiveKeyOrNoReceiverSigReq(
            final boolean isDelegateCall,
            @NonNull final Address target,
            @NonNull final Address activeContract,
            @NonNull final WorldLedgers worldLedgers) {
        final var accountId = EntityIdUtils.accountIdFromEvmAddress(target);
        if (txnCtx.activePayer().equals(accountId)) {
            return true;
        }
        final var requiredKey = receiverSigKeyIfAnyOf(accountId, worldLedgers);
        return requiredKey
                .map(
                        key ->
                                isActiveInFrame(
                                        key,
                                        isDelegateCall,
                                        activeContract,
                                        worldLedgers.aliases()))
                .orElse(true);
    }

    @Override
    public boolean cryptoKeyIsActive(final JKey key) {
        return key != null && isCryptoKeyActiveInFrame(key);
    }

    private boolean isCryptoKeyActiveInFrame(final JKey key) {
        final var pkToCryptoSigsFn = txnCtx.swirldsTxnAccessor().getRationalizedPkToCryptoSigFn();
        return activationTest.test(key, pkToCryptoSigsFn, cryptoValidity);
    }

    private boolean isActiveInFrame(
            final JKey key,
            final boolean isDelegateCall,
            final Address activeContract,
            final ContractAliases aliases) {
        if (key instanceof JKeyList keyList && keyList.isEmpty()) {
            // An empty key list is a sentinel for immutability
            return false;
        }
        final var pkToCryptoSigsFn = txnCtx.swirldsTxnAccessor().getRationalizedPkToCryptoSigFn();
        return activationTest.test(
                key, pkToCryptoSigsFn, validityTestFor(isDelegateCall, activeContract, aliases));
    }

    BiPredicate<JKey, TransactionSignature> validityTestFor(
            final boolean isDelegateCall,
            final Address activeContract,
            final ContractAliases aliases) {
        // Note that when this observer is used directly above in isActiveInFrame(), it will be
        // called  with each primitive key in the top-level Hedera key of interest, along with
        // that key's verified cryptographic signature (if any was available in the sigMap)
        return (key, sig) -> {
            if (key.hasDelegatableContractId() || key.hasDelegatableContractAlias()) {
                final var controllingId =
                        key.hasDelegatableContractId()
                                ? key.getDelegatableContractIdKey().getContractID()
                                : key.getDelegatableContractAliasKey().getContractID();
                final var controllingContract = aliases.currentAddress(controllingId);
                return controllingContract.equals(activeContract);
            } else if (key.hasContractID() || key.hasContractAlias()) {
                final var controllingId =
                        key.hasContractID()
                                ? key.getContractIDKey().getContractID()
                                : key.getContractAliasKey().getContractID();
                final var controllingContract = aliases.currentAddress(controllingId);
                return !isDelegateCall && controllingContract.equals(activeContract);
            } else {
                // Otherwise, apply the standard cryptographic validity test
                return cryptoValidity.test(key, sig);
            }
        };
    }

    private Optional<JKey> receiverSigKeyIfAnyOf(
            final AccountID id, final WorldLedgers worldLedgers) {
        final var accounts = worldLedgers.accounts();
        if (accounts == null) {
            // This must be a static call, hence cannot contain value and cannot require a signature
            return Optional.empty();
        }
        return isReceiverSigExempt(id, accounts)
                ? Optional.empty()
                : Optional.ofNullable((JKey) accounts.get(id, KEY));
    }

    private boolean isReceiverSigExempt(
            final AccountID id,
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts) {
        return !accounts.contains(id) || !(boolean) accounts.get(id, IS_RECEIVER_SIG_REQUIRED);
    }
}
