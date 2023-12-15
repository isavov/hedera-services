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

package com.hedera.node.app.service.mono.records;

import static com.hedera.node.app.service.mono.state.submerkle.TxnId.USER_TRANSACTION_NONCE;
import static com.hedera.node.app.service.mono.utils.MiscUtils.nonNegativeNanosOffset;
import static com.hedera.node.app.service.mono.utils.MiscUtils.synthAccessorFor;
import static com.hedera.node.app.service.mono.utils.MiscUtils.synthWithRecordTxnId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.exceptions.ResourceLimitException;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.submerkle.TxnId;
import com.hedera.node.app.service.mono.stream.RecordStreamObject;
import com.hedera.node.app.service.mono.throttling.FunctionalityThrottling;
import com.hedera.node.app.service.mono.throttling.annotations.HandleThrottle;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.*;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Provides a {@link RecordsHistorian} using the natural collaborators. */
@Singleton
public class TxnAwareRecordsHistorian implements RecordsHistorian {
    private static final Logger log = LogManager.getLogger(TxnAwareRecordsHistorian.class);
    public static final int DEFAULT_SOURCE_ID = 0;

    private final RecordCache recordCache;
    private final TransactionContext txnCtx;
    private final ConsensusTimeTracker consensusTimeTracker;
    private final FunctionalityThrottling handleThrottling;
    private final List<RecordStreamObject> precedingChildStreamObjs = new ArrayList<>();
    private final List<RecordStreamObject> followingChildStreamObjs = new ArrayList<>();
    private final List<InProgressChildRecord> precedingChildRecords = new ArrayList<>();
    private final List<InProgressChildRecord> followingChildRecords = new ArrayList<>();

    private int nextNonce = USER_TRANSACTION_NONCE + 1;
    private int nextSourceId = 1;
    private EntityCreator creator;

    private RecordStreamObject topLevelStreamObj;

    @Inject
    public TxnAwareRecordsHistorian(
            RecordCache recordCache,
            TransactionContext txnCtx,
            ConsensusTimeTracker consensusTimeTracker,
            @HandleThrottle final FunctionalityThrottling handleThrottling) {
        this.txnCtx = txnCtx;
        this.recordCache = recordCache;
        this.consensusTimeTracker = consensusTimeTracker;
        this.handleThrottling = handleThrottling;
    }

    @Override
    public Instant nextFollowingChildConsensusTime() {
        if (!consensusTimeTracker.isAllowableFollowingOffset(1L + followingChildRecords.size())) {
            log.error(
                    "Cannot create more following child consensus times! currentCount={}" + " consensusTimeTracker={}",
                    followingChildRecords.size(),
                    consensusTimeTracker);
            throw new IllegalStateException("Cannot create more following child consensus times!");
        }

        return txnCtx.consensusTime().plusNanos(1L + followingChildRecords.size());
    }

    @Override
    public TxnId computeNextSystemTransactionId() {
        if (topLevelStreamObj == null) {
            throw new IllegalStateException("Top-level record is missing, cannot compute a system transaction id");
        }
        final var parentId = topLevelStreamObj.getExpirableTransactionRecord().getTxnId();
        return parentId.unscheduledWithNonce(nextNonce++);
    }

    @Override
    public boolean nextSystemTransactionIdIsUnknown() {
        return topLevelStreamObj == null;
    }

    @Override
    public RecordStreamObject getTopLevelRecord() {
        return topLevelStreamObj;
    }

    @Override
    public void setCreator(EntityCreator creator) {
        this.creator = creator;
    }

    @Override
    public void saveExpirableTransactionRecords() {
        final var consensusNow = txnCtx.consensusTime();
        final var topLevel = txnCtx.recordSoFar();
        final var accessor = txnCtx.accessor();
        List<TransactionSidecarRecord.Builder> sidecars;
        if (txnCtx.sidecars().isEmpty()) {
            sidecars = Collections.emptyList();
        } else {
            sidecars = new ArrayList<>(txnCtx.sidecars());
            timestampSidecars(sidecars, consensusNow);
        }

        final var numChildren = (short) (precedingChildRecords.size() + followingChildRecords.size());
        finalizeChildRecords(consensusNow, topLevel);
        final var topLevelRecord = topLevel.setNumChildRecords(numChildren).build();
        topLevelStreamObj =
                new RecordStreamObject(topLevelRecord, accessor.getSignedTxnWrapper(), consensusNow, sidecars);

        final var effPayer = txnCtx.effectivePayer();
        final var submittingMember = txnCtx.submittingSwirldsMember();

        save(precedingChildStreamObjs, effPayer, submittingMember);
        save(topLevelRecord, effPayer, accessor.getTxnId(), submittingMember, consensusNow.getEpochSecond());
        save(followingChildStreamObjs, effPayer, submittingMember);

        consensusTimeTracker.setActualFollowingRecordsCount(followingChildStreamObjs.size());
    }

    @Override
    public boolean hasFollowingChildRecords() {
        return !followingChildStreamObjs.isEmpty();
    }

    @Override
    public boolean hasPrecedingChildRecords() {
        return !precedingChildStreamObjs.isEmpty();
    }

    @Override
    public List<RecordStreamObject> getFollowingChildRecords() {
        return followingChildStreamObjs;
    }

    @Override
    public List<RecordStreamObject> getPrecedingChildRecords() {
        return precedingChildStreamObjs;
    }

    @Override
    public boolean hasThrottleCapacityForChildTransactions() {
        @Nullable List<DeterministicThrottle.UsageSnapshot> snapshotsIfNeeded = null;
        var isAllowed = true;
        for (int i = 0, n = followingChildRecords.size(); i < n && isAllowed; i++) {
            final var childRecord = followingChildRecords.get(i);
            if (childRecord.recordBuilder().isPendingSuccess()) {
                final var synthChild = childRecord.syntheticBody();
                // We want to skip any synthetic contract creations or calls, since they don't
                // make sense to throttle here:
                //   (1) A synthetic ContractCreateInstance is already throttled by gas and,
                //   optionally, the CryptoCreate throttle, if contracts.enforceCreationThrottle=true
                //   (2) A synthetic ContractCall represents a read-only precompile, which
                //   again is not relevant for the consensus transaction throttles
                if (synthChild.hasContractCreateInstance() || synthChild.hasContractCall()) {
                    continue;
                }
                if (snapshotsIfNeeded == null) {
                    snapshotsIfNeeded = handleThrottling.getUsageSnapshots();
                }
                final var synthAccessor = synthAccessorFor(synthChild);
                if (handleThrottling.shouldThrottleTxn(synthAccessor)) {
                    isAllowed = false;
                }
            }
        }
        if (!isAllowed) {
            handleThrottling.resetUsageThrottlesTo(snapshotsIfNeeded);
        }
        return isAllowed;
    }

    @Override
    public int nextChildRecordSourceId() {
        return nextSourceId++;
    }

    @Override
    public void trackFollowingChildRecord(
            final int sourceId,
            final TransactionBody.Builder syntheticBody,
            final ExpirableTxnRecord.Builder recordSoFar,
            final List<TransactionSidecarRecord.Builder> sidecars) {
        revertIfNot(consensusTimeTracker.isAllowableFollowingOffset(followingChildRecords.size() + 1L));
        final var inProgress = new InProgressChildRecord(sourceId, syntheticBody, recordSoFar, sidecars);
        followingChildRecords.add(inProgress);
    }

    @Override
    public boolean canTrackPrecedingChildRecords(final int n) {
        return consensusTimeTracker.isAllowablePrecedingOffset(precedingChildRecords.size() + (long) n);
    }

    @Override
    public void trackPrecedingChildRecord(
            final int sourceId,
            final TransactionBody.Builder syntheticTxn,
            final ExpirableTxnRecord.Builder recordSoFar) {
        revertIfNot(consensusTimeTracker.isAllowablePrecedingOffset(precedingChildRecords.size() + 1L));
        final var inProgress = new InProgressChildRecord(sourceId, syntheticTxn, recordSoFar, Collections.emptyList());
        precedingChildRecords.add(inProgress);
    }

    @Override
    public void revertChildRecordsFromSource(final int sourceId) {
        revert(sourceId, precedingChildRecords);
        revert(sourceId, followingChildRecords);
    }

    @Override
    public void clearHistory() {
        precedingChildRecords.clear();
        precedingChildStreamObjs.clear();
        followingChildRecords.clear();
        followingChildStreamObjs.clear();

        topLevelStreamObj = null;
        nextNonce = USER_TRANSACTION_NONCE + 1;
        nextSourceId = 1;
    }

    @Override
    public void customizeSuccessor(
            final Predicate<InProgressChildRecord> matcher, final Consumer<InProgressChildRecord> customizer) {
        for (final var inProgress : followingChildRecords) {
            if (matcher.test(inProgress)) {
                customizer.accept(inProgress);
                return;
            }
        }
    }

    private void revert(final int sourceId, final List<InProgressChildRecord> childRecords) {
        for (final var inProgress : childRecords) {
            if (inProgress.sourceId() == sourceId) {
                inProgress.recordBuilder().revert();
            }
        }
    }

    private void finalizeChildRecords(final Instant consensusNow, final ExpirableTxnRecord.Builder topLevel) {
        finalizeChildrenVerbosely(-1, precedingChildRecords, precedingChildStreamObjs, consensusNow, topLevel);
        finalizeChildrenVerbosely(+1, followingChildRecords, followingChildStreamObjs, consensusNow, topLevel);
    }

    private void finalizeChildrenVerbosely(
            final int sigNum,
            final List<InProgressChildRecord> childRecords,
            final List<RecordStreamObject> recordObjs,
            final Instant consensusNow,
            final ExpirableTxnRecord.Builder topLevel) {
        final var parentId = topLevel.getTxnId();
        int nextNanoOffset = 1;
        for (int i = 0, n = childRecords.size(); i < n; i++) {
            final var inProgress = childRecords.get(i);
            final var child = inProgress.recordBuilder();
            if (child.shouldNotBeExternalized()) {
                // Intentionally don't increment nextNanoOffset here
                continue;
            }
            topLevel.excludeHbarChangesFrom(child);

            // The child's transactionId is determined by the next nonce
            child.setTxnId(parentId.withNonce(nextNonce++));
            // But the consensus time is determined by the # of records already
            // externalized (in either the preceding or following "direction")
            final var childConsTime = nonNegativeNanosOffset(consensusNow, sigNum * nextNanoOffset++);
            child.setConsensusTime(RichInstant.fromJava(childConsTime));
            /* Mirror node team prefers we only set a parent consensus time for records that FOLLOW
             * the top-level transaction. This might change for future use cases. */
            if (sigNum > 0) {
                child.setParentConsensusTime(consensusNow);
            }

            final var synthTxn = synthWithRecordTxnId(inProgress.syntheticBody(), child);
            final var sidecars = inProgress.sidecars();
            timestampSidecars(sidecars, childConsTime);
            if (sigNum > 0) {
                recordObjs.add(new RecordStreamObject(child.build(), synthTxn, childConsTime, sidecars));
            } else {
                // With multiple preceding child records, we add them to the stream in reverse order
                // of
                // creation so that their consensus timestamps will appear in chronological order
                recordObjs.add(0, new RecordStreamObject(child.build(), synthTxn, childConsTime));
            }
        }
    }

    private void save(
            final List<RecordStreamObject> baseRecords, final AccountID effPayer, final long submittingMember) {
        for (final var baseRecord : baseRecords) {
            final var childRecord = baseRecord.getExpirableTransactionRecord();
            save(
                    childRecord,
                    effPayer,
                    childRecord.getTxnId().toGrpc(),
                    submittingMember,
                    childRecord.getConsensusSecond());
        }
    }

    private void save(
            final ExpirableTxnRecord baseRecord,
            AccountID effPayer,
            final TransactionID txnId,
            final long submittingMember,
            final long consensusSecond) {
        if (txnId.getScheduled() && txnCtx.isPayerSigKnownActive()) {
            // Give the record of executing this scheduled transaction to the ScheduleCreate payer account,
            // since the override payer account (if any) is not trivial to derive from just the record; and
            // the record is all the information we will have when purging it at expiry
            effPayer = txnId.getAccountID();
        }
        final var expiringRecord = creator.saveExpiringRecord(effPayer, baseRecord, consensusSecond, submittingMember);
        recordCache.setPostConsensus(txnId, baseRecord.getEnumStatus(), expiringRecord);
    }

    public static void timestampSidecars(
            final List<TransactionSidecarRecord.Builder> sidecars, final Instant txnTimestamp) {
        final var commonTimestamp = MiscUtils.asTimestamp(txnTimestamp);
        for (final var sidecar : sidecars) {
            sidecar.setConsensusTimestamp(commonTimestamp);
        }
    }

    private void revertIfNot(final boolean allowable) {
        if (!allowable) {
            precedingChildRecords.forEach(rec -> rec.recordBuilder().revert());
            followingChildRecords.forEach(rec -> rec.recordBuilder().revert());
            throw new ResourceLimitException(MAX_CHILD_RECORDS_EXCEEDED);
        }
    }

    @VisibleForTesting
    List<InProgressChildRecord> precedingChildRecords() {
        return precedingChildRecords;
    }

    @VisibleForTesting
    List<InProgressChildRecord> followingChildRecords() {
        return followingChildRecords;
    }
}
