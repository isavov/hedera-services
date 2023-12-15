/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter.protocol.heartbeat;

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;
import org.junit.jupiter.api.Assertions;

/**
 * Used by {@link HeartbeatTest} to verify that ping has been updated correctly
 */
class PingChecker {
    private final NodeId expectedPeerId;
    private Duration expectedPing;
    private int numUpdates;

    public PingChecker(@NonNull final NodeId expectedPeerId) {
        this.expectedPeerId = Objects.requireNonNull(expectedPeerId, "expectedPeerId must not be null");
        clear();
    }

    public void setExpectedPing(final Duration expectedPing) {
        this.expectedPing = expectedPing;
    }

    public void checkPing(final NodeId nodeId, final long pingNanos) {
        numUpdates++;
        Assertions.assertEquals(nodeId, expectedPeerId);
        Assertions.assertEquals(expectedPing.toNanos(), pingNanos);
    }

    public void assertNumUpdates(final int expected) {
        Assertions.assertEquals(expected, numUpdates);
    }

    public void clear() {
        numUpdates = 0;
        expectedPing = Duration.ZERO;
    }
}
