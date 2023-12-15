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

package com.swirlds.platform.test.chatter.simulator;

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Used by the {@link EventTracker} to track the propagation of a single event.
 */
public class TrackedEvent {

    private final NodeId creatorId;
    private final Instant creationTime;

    private final Set<NodeId> nodesThatHaveEvent = ConcurrentHashMap.newKeySet();
    private final Queue<Duration> propagationTimes = new ConcurrentLinkedQueue<>();

    /**
     * Create a new tracked event.
     *
     * @param creatorId
     * 		the node that created the event
     * @param creationTime
     * 		the creation time of the event
     */
    public TrackedEvent(@NonNull final NodeId creatorId, @NonNull final Instant creationTime) {
        this.creatorId = Objects.requireNonNull(creatorId, "creatorId must not be null");
        this.creationTime = Objects.requireNonNull(creationTime, "creationTime must not be null");
        nodesThatHaveEvent.add(creatorId);
    }

    /**
     * This method should be called each time the event is received by a node.
     *
     * @param nodeId
     * 		the ID of the node that received the event
     * @param timestamp
     * 		the time when the event was received
     */
    public void registerEventPropagation(@NonNull final NodeId nodeId, @NonNull final Instant timestamp) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        if (nodesThatHaveEvent.add(nodeId)) {
            propagationTimes.add(Duration.between(creationTime, timestamp));
        }
    }

    /**
     * Get the total number of nodes that received this event
     *
     * @return the number of nodes that received this event
     */
    public int getNodeCount() {
        return nodesThatHaveEvent.size();
    }

    /**
     * Get the ID of this event's creator.
     */
    @NonNull
    public NodeId getCreatorId() {
        return creatorId;
    }

    /**
     * Get the creation time of this event.
     */
    @NonNull
    public Instant getCreationTime() {
        return creationTime;
    }

    /**
     * Get a list of event propagation times.
     */
    @NonNull
    public Queue<Duration> getPropagationTimes() {
        return propagationTimes;
    }
}
