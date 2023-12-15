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

package com.swirlds.platform.metrics;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_3;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_14_7;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_15_3;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_16_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_5_3;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_8_1;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;

import com.swirlds.base.units.UnitConstants;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.extensions.CountPerSecond;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphSynchronizer;
import com.swirlds.platform.gossip.shadowgraph.SyncResult;
import com.swirlds.platform.gossip.shadowgraph.SyncTiming;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.stats.AverageAndMax;
import com.swirlds.platform.stats.AverageAndMaxTimeStat;
import com.swirlds.platform.stats.AverageStat;
import com.swirlds.platform.stats.AverageTimeStat;
import com.swirlds.platform.stats.MaxStat;
import com.swirlds.platform.system.PlatformStatNames;
import java.time.temporal.ChronoUnit;

/**
 * Interface to update relevant sync statistics
 */
public class SyncMetrics {
    private static final RunningAverageMetric.Config PERMITS_AVAILABLE_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "syncPermitsAvailable")
            .withDescription("number of sync permits available")
            .withFormat(FORMAT_16_2);
    private final RunningAverageMetric permitsAvailable;

    private static final RunningAverageMetric.Config AVG_BYTES_PER_SEC_SYNC_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "bytes/sec_sync")
            .withDescription("average number of bytes per second transferred during a sync")
            .withFormat(FORMAT_16_2);
    private final RunningAverageMetric avgBytesPerSecSync;

    private static final CountPerSecond.Config CALL_SYNCS_PER_SECOND_CONFIG = new CountPerSecond.Config(
                    PLATFORM_CATEGORY, "sync/secC")
            .withDescription("(call syncs) syncs completed per second initiated by this member")
            .withFormat(FORMAT_14_7);
    private final CountPerSecond callSyncsPerSecond;

    private static final CountPerSecond.Config REC_SYNCS_PER_SECOND_CONFIG = new CountPerSecond.Config(
                    PLATFORM_CATEGORY, "sync/secR")
            .withDescription("(receive syncs) syncs completed per second initiated by other member")
            .withFormat(FORMAT_14_7);
    private final CountPerSecond recSyncsPerSecond;

    private static final RunningAverageMetric.Config TIPS_PER_SYNC_CONFIG = new RunningAverageMetric.Config(
                    INTERNAL_CATEGORY, PlatformStatNames.TIPS_PER_SYNC)
            .withDescription("the average number of tips per sync at the start of each sync")
            .withFormat(FORMAT_15_3);

    private static final CountPerSecond.Config INCOMING_SYNC_REQUESTS_CONFIG = new CountPerSecond.Config(
                    PLATFORM_CATEGORY, "incomingSyncRequests/sec")
            .withDescription("Incoming sync requests received per second")
            .withFormat(FORMAT_14_7);
    private final CountPerSecond incomingSyncRequestsPerSec;

    private static final CountPerSecond.Config ACCEPTED_SYNC_REQUESTS_CONFIG = new CountPerSecond.Config(
                    PLATFORM_CATEGORY, "acceptedSyncRequests/sec")
            .withDescription("Incoming sync requests accepted per second")
            .withFormat(FORMAT_14_7);
    private final CountPerSecond acceptedSyncRequestsPerSec;

    private static final CountPerSecond.Config OPPORTUNITIES_TO_INITIATE_SYNC_CONFIG = new CountPerSecond.Config(
                    PLATFORM_CATEGORY, "opportunitiesToInitiateSync/sec")
            .withDescription("Opportunities to initiate an outgoing sync per second")
            .withFormat(FORMAT_14_7);
    private final CountPerSecond opportunitiesToInitiateSyncPerSec;

    private static final CountPerSecond.Config OUTGOING_SYNC_REQUESTS_CONFIG = new CountPerSecond.Config(
                    PLATFORM_CATEGORY, "outgoingSyncRequests/sec")
            .withDescription("Outgoing sync requests sent per second")
            .withFormat(FORMAT_14_7);
    private final CountPerSecond outgoingSyncRequestsPerSec;

    private static final CountPerSecond.Config SYNCS_PER_SECOND_CONFIG = new CountPerSecond.Config(
                    PLATFORM_CATEGORY, "syncs/sec")
            .withDescription("Total number of syncs completed per second")
            .withFormat(FORMAT_14_7);
    private final CountPerSecond syncsPerSec;

    private final RunningAverageMetric tipsPerSync;

    private final AverageStat syncGenerationDiff;
    private final AverageStat eventRecRate;
    private final AverageTimeStat avgSyncDuration1;
    private final AverageTimeStat avgSyncDuration2;
    private final AverageTimeStat avgSyncDuration3;
    private final AverageTimeStat avgSyncDuration4;
    private final AverageTimeStat avgSyncDuration5;
    private final AverageAndMaxTimeStat avgSyncDuration;
    private final AverageStat knownSetSize;
    private final AverageAndMax avgEventsPerSyncSent;
    private final AverageAndMax avgEventsPerSyncRec;
    private final MaxStat multiTipsPerSync;
    private final AverageStat gensWaitingForExpiry;

    /**
     * Constructor of {@code SyncMetrics}
     *
     * @param metrics
     * 		a reference to the metrics-system
     * @throws IllegalArgumentException
     * 		if {@code metrics} is {@code null}
     */
    public SyncMetrics(final Metrics metrics) {
        avgBytesPerSecSync = metrics.getOrCreate(AVG_BYTES_PER_SEC_SYNC_CONFIG);
        callSyncsPerSecond = new CountPerSecond(metrics, CALL_SYNCS_PER_SECOND_CONFIG);
        recSyncsPerSecond = new CountPerSecond(metrics, REC_SYNCS_PER_SECOND_CONFIG);
        tipsPerSync = metrics.getOrCreate(TIPS_PER_SYNC_CONFIG);

        incomingSyncRequestsPerSec = new CountPerSecond(metrics, INCOMING_SYNC_REQUESTS_CONFIG);
        acceptedSyncRequestsPerSec = new CountPerSecond(metrics, ACCEPTED_SYNC_REQUESTS_CONFIG);
        opportunitiesToInitiateSyncPerSec = new CountPerSecond(metrics, OPPORTUNITIES_TO_INITIATE_SYNC_CONFIG);
        outgoingSyncRequestsPerSec = new CountPerSecond(metrics, OUTGOING_SYNC_REQUESTS_CONFIG);
        syncsPerSec = new CountPerSecond(metrics, SYNCS_PER_SECOND_CONFIG);

        avgSyncDuration = new AverageAndMaxTimeStat(
                metrics,
                ChronoUnit.SECONDS,
                INTERNAL_CATEGORY,
                "sec/sync",
                "duration of average successful sync (in seconds)");

        avgEventsPerSyncSent = new AverageAndMax(
                metrics, PLATFORM_CATEGORY, "ev/syncS", "number of events sent per successful sync", FORMAT_8_1);
        avgEventsPerSyncRec = new AverageAndMax(
                metrics, PLATFORM_CATEGORY, "ev/syncR", "number of events received per successful sync", FORMAT_8_1);

        syncGenerationDiff = new AverageStat(
                metrics,
                INTERNAL_CATEGORY,
                "syncGenDiff",
                "number of generation ahead (positive) or behind (negative) when syncing",
                FORMAT_8_1,
                AverageStat.WEIGHT_VOLATILE);
        eventRecRate = new AverageStat(
                metrics,
                INTERNAL_CATEGORY,
                "eventRecRate",
                "the rate at which we receive and enqueue events in ev/sec",
                FORMAT_8_1,
                AverageStat.WEIGHT_VOLATILE);

        avgSyncDuration1 = new AverageTimeStat(
                metrics,
                ChronoUnit.SECONDS,
                INTERNAL_CATEGORY,
                "sec/sync1",
                "duration of step 1 of average successful sync (in seconds)");
        avgSyncDuration2 = new AverageTimeStat(
                metrics,
                ChronoUnit.SECONDS,
                INTERNAL_CATEGORY,
                "sec/sync2",
                "duration of step 2 of average successful sync (in seconds)");
        avgSyncDuration3 = new AverageTimeStat(
                metrics,
                ChronoUnit.SECONDS,
                INTERNAL_CATEGORY,
                "sec/sync3",
                "duration of step 3 of average successful sync (in seconds)");
        avgSyncDuration4 = new AverageTimeStat(
                metrics,
                ChronoUnit.SECONDS,
                INTERNAL_CATEGORY,
                "sec/sync4",
                "duration of step 4 of average successful sync (in seconds)");
        avgSyncDuration5 = new AverageTimeStat(
                metrics,
                ChronoUnit.SECONDS,
                INTERNAL_CATEGORY,
                "sec/sync5",
                "duration of step 5 of average successful sync (in seconds)");

        knownSetSize = new AverageStat(
                metrics,
                PLATFORM_CATEGORY,
                "knownSetSize",
                "the average size of the known set during a sync",
                FORMAT_10_3,
                AverageStat.WEIGHT_VOLATILE);

        multiTipsPerSync = new MaxStat(
                metrics,
                PLATFORM_CATEGORY,
                PlatformStatNames.MULTI_TIPS_PER_SYNC,
                "the number of creators that have more than one tip at the start of each sync",
                "%5d");
        gensWaitingForExpiry = new AverageStat(
                metrics,
                PLATFORM_CATEGORY,
                PlatformStatNames.GENS_WAITING_FOR_EXPIRY,
                "the average number of generations waiting to be expired",
                FORMAT_5_3,
                AverageStat.WEIGHT_VOLATILE);

        permitsAvailable = metrics.getOrCreate(PERMITS_AVAILABLE_CONFIG);
    }

    /**
     * Supplies the generation numbers of a sync for statistics
     *
     * @param self
     * 		generations of our graph at the start of the sync
     * @param other
     * 		generations of their graph at the start of the sync
     */
    public void generations(final GraphGenerations self, final GraphGenerations other) {
        syncGenerationDiff.update(self.getMaxRoundGeneration() - other.getMaxRoundGeneration());
    }

    /**
     * Supplies information about the rate of receiving events when all events are read
     *
     * @param nanosStart
     * 		The {@link System#nanoTime()} when we started receiving events
     * @param numberReceived
     * 		the number of events received
     */
    public void eventsReceived(final long nanosStart, final int numberReceived) {
        if (numberReceived == 0) {
            return;
        }
        final double nanos = ((double) System.nanoTime()) - nanosStart;
        final double seconds = nanos / ChronoUnit.SECONDS.getDuration().toNanos();
        eventRecRate.update(Math.round(numberReceived / seconds));
    }

    /**
     * Record all stats related to sync timing
     *
     * @param timing
     * 		object that holds the timing data
     * @param conn
     * 		the sync connections
     */
    public void recordSyncTiming(final SyncTiming timing, final Connection conn) {
        avgSyncDuration1.update(timing.getTimePoint(0), timing.getTimePoint(1));
        avgSyncDuration2.update(timing.getTimePoint(1), timing.getTimePoint(2));
        avgSyncDuration3.update(timing.getTimePoint(2), timing.getTimePoint(3));
        avgSyncDuration4.update(timing.getTimePoint(3), timing.getTimePoint(4));
        avgSyncDuration5.update(timing.getTimePoint(4), timing.getTimePoint(5));

        avgSyncDuration.update(timing.getTimePoint(0), timing.getTimePoint(5));
        final double syncDurationSec = timing.getPointDiff(5, 0) * UnitConstants.NANOSECONDS_TO_SECONDS;
        final double speed = Math.max(
                        conn.getDis().getSyncByteCounter().getCount(),
                        conn.getDos().getSyncByteCounter().getCount())
                / syncDurationSec;

        // set the bytes/sec speed of the sync currently measured
        avgBytesPerSecSync.update(speed);
    }

    /**
     * Records the size of the known set during a sync. This is the most compute intensive part of the sync, so this is
     * useful information to validate sync performance.
     *
     * @param knownSetSize
     * 		the size of the known set
     */
    public void knownSetSize(final int knownSetSize) {
        this.knownSetSize.update(knownSetSize);
    }

    /**
     * Notifies the stats that a sync is done
     *
     * @param info
     * 		information about the sync that occurred
     */
    public void syncDone(final SyncResult info) {
        if (info.isCaller()) {
            callSyncsPerSecond.count();
        } else {
            recSyncsPerSecond.count();
        }
        syncsPerSec.count();

        avgEventsPerSyncSent.update(info.getEventsWritten());
        avgEventsPerSyncRec.update(info.getEventsRead());
    }

    /**
     * Called by {@link ShadowGraphSynchronizer} to update the {@code tips/sync} statistic with the number of creators
     * that have more than one {@code sendTip} in the current synchronization.
     *
     * @param multiTipCount
     * 		the number of creators in the current synchronization that have more than one sending tip.
     */
    public void updateMultiTipsPerSync(final int multiTipCount) {
        multiTipsPerSync.update(multiTipCount);
    }

    /**
     * Called by {@link ShadowGraphSynchronizer} to update the {@code tips/sync} statistic with the number of {@code
     * sendTips} in the current synchronization.
     *
     * @param tipCount
     * 		the number of sending tips in the current synchronization.
     */
    public void updateTipsPerSync(final int tipCount) {
        tipsPerSync.update(tipCount);
    }

    /**
     * Called by {@link ShadowGraph} to update the number of generations that should
     * be expired but can't be yet due to reservations.
     *
     * @param numGenerations
     * 		the new number of generations
     */
    public void updateGensWaitingForExpiry(final long numGenerations) {
        gensWaitingForExpiry.update(numGenerations);
    }

    /**
     * Updates the number of permits available for syncs
     *
     * @param permits the number of permits available
     */
    public void updateSyncPermitsAvailable(final int permits) {
        permitsAvailable.update(permits);
    }

    /**
     * Indicate that a request to sync has been received
     */
    public void incomingSyncRequestReceived() {
        incomingSyncRequestsPerSec.count();
    }

    /**
     * Indicate that a request to sync has been accepted
     */
    public void acceptedSyncRequest() {
        acceptedSyncRequestsPerSec.count();
    }

    /**
     * Indicate that there was an opportunity to sync with a peer. The protocol may or may not take the opportunity
     */
    public void opportunityToInitiateSync() {
        opportunitiesToInitiateSyncPerSec.count();
    }

    /**
     * Indicate that a request to sync has been sent
     */
    public void outgoingSyncRequestSent() {
        outgoingSyncRequestsPerSec.count();
    }
}
