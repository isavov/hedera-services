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

package com.swirlds.platform.event;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.gossip.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.system.events.BaseEvent;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

/**
 * A class used to hold information about an event transferred through gossip
 */
public class GossipEvent implements BaseEvent, ChatterEvent {
    private static final long CLASS_ID = 0xfe16b46795bfb8dcL;
    private static final int MAX_SIG_LENGTH = 384;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int REMOVED_ROUND = 2;
        /**
         * Event serialization changes
         *
         * @since 0.46.0
         */
        public static final int BIRTH_ROUND = 3;
    }

    private int serializedVersion = ClassVersion.BIRTH_ROUND;
    private BaseEventHashedData hashedData;
    private BaseEventUnhashedData unhashedData;
    private EventDescriptor descriptor;
    private Instant timeReceived;

    /**
     * The id of the node which sent us this event
     * <p>
     * The sender ID of an event should not be serialized when an event is serialized, and it should not affect the hash
     * of the event in any way.
     */
    private NodeId senderId;

    @SuppressWarnings("unused") // needed for RuntimeConstructable
    public GossipEvent() {}

    /**
     * @param hashedData   the hashed data for the event
     * @param unhashedData the unhashed data for the event
     */
    public GossipEvent(final BaseEventHashedData hashedData, final BaseEventUnhashedData unhashedData) {
        this.hashedData = hashedData;
        this.unhashedData = unhashedData;
        // remove update of other parent event descriptor after 0.46.0 hits mainnet.
        unhashedData.updateOtherParentEventDescriptor(hashedData);
        this.timeReceived = Instant.now();
        this.senderId = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        if (serializedVersion < ClassVersion.BIRTH_ROUND) {
            out.writeSerializable(hashedData, false);
            out.writeSerializable(unhashedData, false);
        } else {
            out.writeSerializable(hashedData, false);
            out.writeByteArray(unhashedData.getSignature());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        serializedVersion = version;
        if (version < ClassVersion.BIRTH_ROUND) {
            hashedData = in.readSerializable(false, BaseEventHashedData::new);
            unhashedData = in.readSerializable(false, BaseEventUnhashedData::new);
        } else {
            hashedData = in.readSerializable(false, BaseEventHashedData::new);
            final byte[] signature = in.readByteArray(MAX_SIG_LENGTH);
            unhashedData = new BaseEventUnhashedData(null, signature);
        }
        // remove update of other parent event descriptor after 0.46.0 hits mainnet.
        unhashedData.updateOtherParentEventDescriptor(hashedData);
        timeReceived = Instant.now();
    }

    /**
     * Get the hashed data for the event.
     */
    @Override
    public BaseEventHashedData getHashedData() {
        return hashedData;
    }

    /**
     * Get the unhashed data for the event.
     */
    @Override
    public BaseEventUnhashedData getUnhashedData() {
        return unhashedData;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventDescriptor getDescriptor() {
        if (descriptor == null) {
            throw new IllegalStateException("Can not get descriptor until event has been hashed");
        }
        return descriptor;
    }

    /**
     * Build the descriptor of this event. This cannot be done when the event is first instantiated, it needs to be
     * hashed before the descriptor can be built.
     *
     * @throws IllegalStateException if the descriptor has already been built
     */
    public void buildDescriptor() {
        if (descriptor != null) {
            throw new IllegalStateException("Descriptor has already been built");
        }

        this.descriptor = hashedData.createEventDescriptor();
    }

    @Override
    public long getGeneration() {
        return hashedData.getGeneration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Instant getTimeReceived() {
        return timeReceived;
    }

    /**
     * Get the id of the node which sent us this event
     *
     * @return the id of the node which sent us this event
     */
    @Nullable
    public NodeId getSenderId() {
        return senderId;
    }

    /**
     * Set the id of the node which sent us this event
     *
     * @param senderId the id of the node which sent us this event
     */
    public void setSenderId(@NonNull final NodeId senderId) {
        this.senderId = senderId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return serializedVersion;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.REMOVED_ROUND;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return EventStrings.toMediumString(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final GossipEvent that = (GossipEvent) o;
        return Objects.equals(getHashedData(), that.getHashedData());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return hashedData.getHash().hashCode();
    }
}
