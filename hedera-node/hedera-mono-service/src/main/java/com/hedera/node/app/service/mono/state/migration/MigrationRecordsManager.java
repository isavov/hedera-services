/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.migration;

import static com.hedera.node.app.service.mono.context.properties.PropertyNames.AUTO_RENEW_GRANT_FREE_RENEWALS;
import static com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt.SUCCESS_LITERAL;
import static com.hedera.node.app.service.mono.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.node.app.service.mono.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.node.app.service.mono.state.EntityCreator.NO_CUSTOM_FEES;
import static com.hedera.node.app.service.mono.state.initialization.BackedSystemAccountsCreator.FUNDING_ACCOUNT_EXPIRY;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.context.properties.PropertyNames;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt;
import com.hedera.node.app.service.mono.records.ConsensusTimeTracker;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.initialization.BackedSystemAccountsCreator;
import com.hedera.node.app.service.mono.state.initialization.BlocklistAccountCreator;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.submerkle.CurrencyAdjustments;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for externalizing any state changes that happened during migration via child records,
 * and then marking the work done via {@link MerkleNetworkContext#markMigrationRecordsStreamed()}.
 *
 * <p>For example, in release v0.24.1 we created two new accounts {@code 0.0.800} and {@code
 * 0.0.801} to receive staking reward funds. Without synthetic {@code CryptoCreate} records in the
 * record stream, mirror nodes wouldn't know about these new staking accounts. (Note on a network
 * reset, we will <i>also</i> stream these two synthetic creations for mirror node consumption.)
 */
@Singleton
public class MigrationRecordsManager {

    static final String AUTO_RENEW_MEMO_TPL = "Contract 0.0.%d was renewed during 0.30.0 upgrade; new expiry is %d";
    private static final Logger log = LogManager.getLogger(MigrationRecordsManager.class);
    private static final Key immutableKey =
            Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build();
    private static final String STAKING_MEMO = "Release 0.24.1 migration record";
    private static final String TREASURY_CLONE_MEMO = "Synthetic zero-balance treasury clone";
    private static final String SYSTEM_ACCOUNT_CREATION_MEMO = "Synthetic system creation";
    private static final String BLOCKED_ACCOUNT_CREATION_MEMO = "Synthetic blocked account creation";

    private final EntityCreator creator;
    private final BackedSystemAccountsCreator systemAccountsCreator;
    private final SigImpactHistorian sigImpactHistorian;
    private final RecordsHistorian recordsHistorian;
    private final Supplier<MerkleNetworkContext> networkCtx;
    private final ConsensusTimeTracker consensusTimeTracker;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final Supplier<AccountStorageAdapter> accounts;
    private final HederaAccountNumbers accountNumbers;
    private final BootstrapProperties bootstrapProperties;
    private final BlocklistAccountCreator blocklistAccountCreator;
    private Supplier<SideEffectsTracker> sideEffectsFactory = SideEffectsTracker::new;

    @Inject
    public MigrationRecordsManager(
            final EntityCreator creator,
            final BackedSystemAccountsCreator systemAccountsCreator,
            final SigImpactHistorian sigImpactHistorian,
            final RecordsHistorian recordsHistorian,
            final Supplier<MerkleNetworkContext> networkCtx,
            final ConsensusTimeTracker consensusTimeTracker,
            final Supplier<AccountStorageAdapter> accounts,
            final SyntheticTxnFactory syntheticTxnFactory,
            final HederaAccountNumbers accountNumbers,
            final BootstrapProperties bootstrapProperties,
            final BlocklistAccountCreator blocklistAccountCreator) {
        this.systemAccountsCreator = systemAccountsCreator;
        this.sigImpactHistorian = sigImpactHistorian;
        this.recordsHistorian = recordsHistorian;
        this.networkCtx = networkCtx;
        this.consensusTimeTracker = consensusTimeTracker;
        this.creator = creator;
        this.accounts = accounts;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.accountNumbers = accountNumbers;
        this.bootstrapProperties = bootstrapProperties;
        this.blocklistAccountCreator = blocklistAccountCreator;
    }

    /**
     * If appropriate, publish the migration records for this upgrade. Only needs to be called once
     * per restart, but that call must be made from {@code handleTransaction} inside an active
     * {@link TransactionContext} (because the record running hash is in state).
     */
    public void publishMigrationRecords(final Instant now) {
        final var curNetworkCtx = networkCtx.get();
        if (!consensusTimeTracker.unlimitedPreceding() || curNetworkCtx.areMigrationRecordsStreamed()) {
            return;
        }

        // Publish synthetic creation records for system and staking reward accounts ONLY at true network genesis,
        // when the consensus time of the last handled txn is null...if we publish these unconditionally, then some
        // genesis reconnect scenarios risk an ISS
        if (curNetworkCtx.consensusTimeOfLastHandledTxn() == null) {
            // If we just started from genesis, we'll have records of actually creating the system accounts. But if
            // we're only replaying events from round one, those system accounts were already created. So call this
            // to ensure we'll publish the same synthetic records.
            systemAccountsCreator.ensureSynthRecordsPresentOnFirstEverTransaction();
            final var implicitAutoRenewPeriod = FUNDING_ACCOUNT_EXPIRY - now.getEpochSecond();
            // Always publish records for any staking fund accounts created
            publishStakingFundAccountsCreated(
                    systemAccountsCreator.getStakingFundAccountsCreated(), implicitAutoRenewPeriod);
            // Always publish records for any treasury clones that needed to be created
            publishAccountsCreated(
                    systemAccountsCreator.getTreasuryClonesCreated(), now, TREASURY_CLONE_MEMO, "treasury clone");
            // Always publish records for any system accounts created at genesis start up
            publishAccountsCreated(
                    systemAccountsCreator.getSystemAccountsCreated(),
                    now,
                    SYSTEM_ACCOUNT_CREATION_MEMO,
                    "system creation");
        } else if (grantingFreeAutoRenewals()) {
            publishContractFreeAutoRenewalRecords();
        }

        if (bootstrapProperties.getBooleanProperty(PropertyNames.ACCOUNTS_BLOCKLIST_ENABLED)) {
            publishAccountsCreated(
                    blocklistAccountCreator.getBlockedAccountsCreated(), now, BLOCKED_ACCOUNT_CREATION_MEMO, "blocked");
        }

        curNetworkCtx.markMigrationRecordsStreamed();
        systemAccountsCreator.forgetCreations();
        blocklistAccountCreator.forgetCreatedBlockedAccounts();
    }

    private void publishStakingFundAccountsCreated(
            final List<HederaAccount> stakingFundAccountsCreated, final long implicitAutoRenewPeriod) {
        stakingFundAccountsCreated.forEach(account ->
                publishSyntheticCreationForStakingFund(EntityNum.fromLong(account.number()), implicitAutoRenewPeriod));
    }

    private void publishAccountsCreated(
            final List<HederaAccount> createdAccounts, final Instant now, final String memo, final String description) {
        createdAccounts.forEach(account -> publishSyntheticCreation(
                EntityNum.fromInt(account.number()),
                account.getExpiry() - now.getEpochSecond(),
                account.isReceiverSigRequired(),
                account.isDeclinedReward(),
                asKeyUnchecked(account.getAccountKey()),
                account.getMemo(),
                memo,
                description,
                account.getBalance(),
                account.getAlias()));
    }

    private boolean grantingFreeAutoRenewals() {
        return bootstrapProperties.getBooleanProperty(AUTO_RENEW_GRANT_FREE_RENEWALS);
    }

    private void publishSyntheticCreationForStakingFund(final EntityNum num, final long autoRenewPeriod) {
        publishSyntheticCreation(
                num,
                autoRenewPeriod,
                false,
                false,
                immutableKey,
                EMPTY_MEMO,
                STAKING_MEMO,
                "staking fund",
                0L, // since 0.0.800 and 0.0.801 are created with zero balance
                ByteString.EMPTY);
    }

    @SuppressWarnings("java:S107")
    private void publishSyntheticCreation(
            final EntityNum num,
            final long autoRenewPeriod,
            final boolean receiverSigRequired,
            final boolean declineReward,
            final Key key,
            final String accountMemo,
            final String recordMemo,
            final String description,
            final long balance,
            final ByteString alias) {
        final var tracker = sideEffectsFactory.get();
        tracker.trackAutoCreation(num.toGrpcAccountId());
        final var synthBody =
                synthCreation(autoRenewPeriod, key, accountMemo, receiverSigRequired, declineReward, balance, alias);
        final var synthRecord = creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, tracker, recordMemo);
        // show the balance of any accounts whose balance is not zero for mirror node
        // reconciliation.
        if (balance != 0) {
            synthRecord.setHbarAdjustments(
                    CurrencyAdjustments.fromChanges(new long[] {balance}, new long[] {num.longValue()}));
        }

        recordsHistorian.trackPrecedingChildRecord(DEFAULT_SOURCE_ID, synthBody, synthRecord);
        sigImpactHistorian.markEntityChanged(num.longValue());
        log.info("Published synthetic CryptoCreate for {} account 0.0.{}", description, num.longValue());
    }

    private TransactionBody.Builder synthCreation(
            final long autoRenewPeriod,
            final Key key,
            final String memo,
            final boolean receiverSigRequired,
            final boolean declineReward,
            final long balance,
            final ByteString alias) {
        final var txnBody = CryptoCreateTransactionBody.newBuilder()
                .setKey(key)
                .setMemo(memo)
                .setDeclineReward(declineReward)
                .setReceiverSigRequired(receiverSigRequired)
                .setInitialBalance(balance)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod));

        if (alias != null && !alias.isEmpty()) {
            txnBody.setAlias(alias);
        }

        return TransactionBody.newBuilder().setCryptoCreateAccount(txnBody.build());
    }

    private void publishContractFreeAutoRenewalRecords() {
        accounts.get().forEach((id, account) -> {
            if (account.isSmartContract() && !account.isDeleted()) {
                final var contractNum = id.toEntityId();
                final var newExpiry = account.getExpiry();

                final var syntheticSuccessReceipt =
                        TxnReceipt.newBuilder().setStatus(SUCCESS_LITERAL).build();
                final var synthBody = syntheticTxnFactory.synthContractAutoRenew(contractNum.asNum(), newExpiry);
                final var memo = String.format(AUTO_RENEW_MEMO_TPL, contractNum.num(), newExpiry);
                final var synthRecord =
                        ExpirableTxnRecord.newBuilder().setMemo(memo).setReceipt(syntheticSuccessReceipt);

                recordsHistorian.trackPrecedingChildRecord(DEFAULT_SOURCE_ID, synthBody, synthRecord);
                log.debug("Published synthetic ContractUpdate for contract 0.0.{}", contractNum.num());
            }
        });
    }

    @VisibleForTesting
    void setSideEffectsFactory(final Supplier<SideEffectsTracker> sideEffectsFactory) {
        this.sideEffectsFactory = sideEffectsFactory;
    }
}
