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

package com.swirlds.platform.components;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STATE_TO_DISK;
import static com.swirlds.platform.state.signed.StateToDiskReason.FIRST_ROUND_AFTER_GENESIS;
import static com.swirlds.platform.state.signed.StateToDiskReason.FREEZE_STATE;
import static com.swirlds.platform.state.signed.StateToDiskReason.PERIODIC_SNAPSHOT;
import static com.swirlds.platform.state.signed.StateToDiskReason.RECONNECT;

import com.swirlds.base.function.BooleanFunction;
import com.swirlds.common.config.StateConfig;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.StateToDiskReason;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Controls which signed states should be written to disk based on input from other components
 */
public class SavedStateController {
    private static final Logger logger = LogManager.getLogger(SavedStateController.class);
    /**
     * The timestamp of the signed state that was most recently written to disk, or null if no timestamp was recently
     * written to disk.
     */
    private Instant previousSavedStateTimestamp;
    /** the state config */
    private final StateConfig stateConfig;
    /** a function that writes a signed state to disk asynchronously */
    private final BooleanFunction<ReservedSignedState> stateWrite;

    /**
     * Create a new SavedStateController
     *
     * @param stateConfig the state config
     * @param stateWrite  a function that writes a signed state to disk asynchronously
     */
    public SavedStateController(
            @NonNull final StateConfig stateConfig, @NonNull final BooleanFunction<ReservedSignedState> stateWrite) {
        this.stateConfig = Objects.requireNonNull(stateConfig);
        this.stateWrite = Objects.requireNonNull(stateWrite);
    }

    /**
     * Determine if a signed state should be written to disk. If the state should be written, the state will be passed
     * on to the writer to be written asynchronously.
     *
     * @param signedState the signed state in question
     */
    public synchronized void maybeSaveState(@NonNull final SignedState signedState) {

        final StateToDiskReason reason = shouldSaveToDisk(signedState, previousSavedStateTimestamp);

        if (reason != null) {
            saveToDisk(signedState.reserve("saving to disk"), reason);
        }
        // if a null reason is returned, then there isn't anything to do, since the state shouldn't be saved
    }

    /**
     * Notifies the controller that a signed state was received from another node during reconnect. The controller saves
     * its timestamp and pass it on to be written to disk.
     *
     * @param signedState the signed state that was received from another node during reconnect
     */
    public synchronized void reconnectStateReceived(@NonNull final SignedState signedState) {
        saveToDisk(signedState.reserve("saving to disk after reconnect"), RECONNECT);
    }

    /**
     * This should be called at boot time when a signed state is read from the disk.
     *
     * @param signedState the signed state that was read from file at boot time
     */
    public synchronized void registerSignedStateFromDisk(@NonNull final SignedState signedState) {
        previousSavedStateTimestamp = signedState.getConsensusTimestamp();
    }

    private void saveToDisk(@NonNull final ReservedSignedState state, @NonNull final StateToDiskReason reason) {
        final SignedState signedState = state.get();
        logger.info(
                STATE_TO_DISK.getMarker(),
                "Signed state from round {} created, will eventually be written to disk, for reason: {}",
                signedState.getRound(),
                reason);

        previousSavedStateTimestamp = signedState.getConsensusTimestamp();
        signedState.markAsStateToSave(reason);
        final boolean accepted = stateWrite.apply(state);

        if (!accepted) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Unable to save signed state to disk for round {} due to backlog of "
                            + "operations in the SignedStateManager task queue.",
                    signedState.getRound());

            state.close();
        }
    }

    /**
     * Determines whether a signed state should eventually be written to disk
     * <p>
     * If it is determined that the state should be written to disk, this method returns the reason why
     * <p>
     * If it is determined that the state shouldn't be written to disk, then this method returns null
     *
     * @param signedState       the state in question
     * @param previousTimestamp the timestamp of the previous state that was saved to disk, or null if no previous state
     *                          was saved to disk
     * @return the reason why the state should be written to disk, or null if it shouldn't be written to disk
     */
    @Nullable
    private StateToDiskReason shouldSaveToDisk(
            @NonNull final SignedState signedState, @Nullable final Instant previousTimestamp) {

        if (signedState.isFreezeState()) {
            // the state right before a freeze should be written to disk
            return FREEZE_STATE;
        }

        final int saveStatePeriod = stateConfig.saveStatePeriod();
        if (saveStatePeriod <= 0) {
            // periodic state saving is disabled
            return null;
        }

        // FUTURE WORK: writing genesis state to disk is currently disabled if the saveStatePeriod is 0.
        // This is for testing purposes, to have a method of disabling state saving for tests.
        // Once a feature to disable all state saving has been added, this block should be moved in front of the
        // saveStatePeriod <=0 block, so that saveStatePeriod doesn't impact the saving of genesis state.
        if (previousTimestamp == null) {
            // the first round should be saved
            return FIRST_ROUND_AFTER_GENESIS;
        }

        if ((signedState.getConsensusTimestamp().getEpochSecond() / saveStatePeriod)
                > (previousTimestamp.getEpochSecond() / saveStatePeriod)) {
            return PERIODIC_SNAPSHOT;
        } else {
            // the period hasn't yet elapsed
            return null;
        }
    }
}
