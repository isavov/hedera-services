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

package com.swirlds.platform.wiring;

import com.swirlds.platform.wiring.components.EventCreationManagerWiring;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Responsible for coordinating the clearing of the platform wiring objects.
 */
public class PlatformCoordinator {
    private final InternalEventValidatorWiring internalEventValidatorWiring;
    private final EventDeduplicatorWiring eventDeduplicatorWiring;
    private final EventSignatureValidatorWiring eventSignatureValidatorWiring;
    private final OrphanBufferWiring orphanBufferWiring;
    private final InOrderLinkerWiring inOrderLinkerWiring;
    private final LinkedEventIntakeWiring linkedEventIntakeWiring;
    private final EventCreationManagerWiring eventCreationManagerWiring;

    /**
     * Constructor
     *
     * @param internalEventValidatorWiring  the internal event validator wiring
     * @param eventDeduplicatorWiring       the event deduplicator wiring
     * @param eventSignatureValidatorWiring the event signature validator wiring
     * @param orphanBufferWiring            the orphan buffer wiring
     * @param inOrderLinkerWiring           the in order linker wiring
     * @param linkedEventIntakeWiring       the linked event intake wiring
     * @param eventCreationManagerWiring    the event creation manager wiring
     */
    public PlatformCoordinator(
            @NonNull final InternalEventValidatorWiring internalEventValidatorWiring,
            @NonNull final EventDeduplicatorWiring eventDeduplicatorWiring,
            @NonNull final EventSignatureValidatorWiring eventSignatureValidatorWiring,
            @NonNull final OrphanBufferWiring orphanBufferWiring,
            @NonNull final InOrderLinkerWiring inOrderLinkerWiring,
            @NonNull final LinkedEventIntakeWiring linkedEventIntakeWiring,
            @NonNull final EventCreationManagerWiring eventCreationManagerWiring) {

        this.internalEventValidatorWiring = Objects.requireNonNull(internalEventValidatorWiring);
        this.eventDeduplicatorWiring = Objects.requireNonNull(eventDeduplicatorWiring);
        this.eventSignatureValidatorWiring = Objects.requireNonNull(eventSignatureValidatorWiring);
        this.orphanBufferWiring = Objects.requireNonNull(orphanBufferWiring);
        this.inOrderLinkerWiring = Objects.requireNonNull(inOrderLinkerWiring);
        this.linkedEventIntakeWiring = Objects.requireNonNull(linkedEventIntakeWiring);
        this.eventCreationManagerWiring = Objects.requireNonNull(eventCreationManagerWiring);
    }

    /**
     * Flushes the intake pipeline
     */
    public void flushIntakePipeline() {
        internalEventValidatorWiring.flushRunnable().run();
        eventDeduplicatorWiring.flushRunnable().run();
        eventSignatureValidatorWiring.flushRunnable().run();
        orphanBufferWiring.flushRunnable().run();
        eventCreationManagerWiring.flush();
        inOrderLinkerWiring.flushRunnable().run();
        linkedEventIntakeWiring.flushRunnable().run();
    }

    /**
     * Safely clears the intake pipeline
     * <p>
     * Future work: this method should be expanded to coordinate the clearing of the entire system
     */
    public void clear() {
        // Phase 1: pause
        // Pause the linked event intake and event creator, to prevent any new events from making it through the intake
        // pipeline.
        linkedEventIntakeWiring.pauseInput().inject(true);
        eventCreationManagerWiring.pauseInput().inject(true);
        linkedEventIntakeWiring.flushRunnable().run();
        eventCreationManagerWiring.flush();

        // Phase 2: flush
        // Flush everything remaining in the intake pipeline out into the void.
        flushIntakePipeline();

        // Phase 3: clear
        // Data is no longer moving through the system. clear all the internal data structures in the wiring objects.
        eventDeduplicatorWiring.clearInput().inject(new ClearTrigger());
        eventDeduplicatorWiring.flushRunnable().run();
        orphanBufferWiring.clearInput().inject(new ClearTrigger());
        orphanBufferWiring.flushRunnable().run();
        inOrderLinkerWiring.clearInput().inject(new ClearTrigger());
        inOrderLinkerWiring.flushRunnable().run();

        // Phase 4: unpause
        // Once everything has been flushed out of the system, it's safe to unpause event intake and creation.
        linkedEventIntakeWiring.pauseInput().inject(false);
        eventCreationManagerWiring.pauseInput().inject(false);
    }
}
