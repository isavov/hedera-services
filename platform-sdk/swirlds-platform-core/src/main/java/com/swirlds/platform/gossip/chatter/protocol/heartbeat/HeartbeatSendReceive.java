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

package com.swirlds.platform.gossip.chatter.protocol.heartbeat;

import com.swirlds.base.time.Time;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.gossip.chatter.protocol.MessageHandler;
import com.swirlds.platform.gossip.chatter.protocol.MessageProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.function.BiConsumer;

/**
 * Sends and receives chatter heartbeats
 */
public class HeartbeatSendReceive implements MessageProvider, MessageHandler<HeartbeatMessage> {
    private final HeartbeatResponder responder;
    private final HeartbeatSender sender;

    /**
     * @param time
     * 		provides a point in time in nanoseconds, should only be used to measure relative time (from one point to
     * 		another), not absolute time (wall clock time)
     * @param peerId
     * 		the ID of the peer we are pinging
     * @param pingConsumer
     * 		consumes the ping time the heartbeat measures. accepts the ID of the peer and the number of
     * 		nanoseconds it took for the peer to respond
     * @param heartbeatInterval
     * 		the interval at which to send heartbeats
     */
    public HeartbeatSendReceive(
            @NonNull final Time time,
            @NonNull final NodeId peerId,
            @NonNull final BiConsumer<NodeId, Long> pingConsumer,
            @NonNull final Duration heartbeatInterval) {
        this.responder = new HeartbeatResponder();
        // Checks for NonNull are performed by the HeartbeatSender constructor.
        this.sender = new HeartbeatSender(peerId, pingConsumer, heartbeatInterval, time);
    }

    @Override
    public SelfSerializable getMessage() {
        // responses first
        final SelfSerializable response = responder.getMessage();
        if (response != null) {
            return response;
        }

        return sender.getMessage();
    }

    @Override
    public void handleMessage(final HeartbeatMessage message) {
        if (message.isResponse()) {
            // they are responding to our ping
            sender.handleMessage(message);
        } else {
            // they are pinging us
            responder.handleMessage(message);
        }
    }

    @Override
    public void clear() {
        responder.clear();
        sender.clear();
    }

    /**
     * @see HeartbeatSender#getLastRoundTripNanos()
     */
    public Long getLastRoundTripNanos() {
        return sender.getLastRoundTripNanos();
    }
}
