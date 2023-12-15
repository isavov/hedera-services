/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state.merkle.disk;

import com.hedera.node.app.state.merkle.StateMetadata;
import com.swirlds.merkledb.serialize.ValueSerializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * An implementation of {@link ValueSerializer}, required by the {@link
 * com.swirlds.virtualmap.VirtualMap} for creating new {@link OnDiskValue}s.
 *
 * @param <V> The type of the value in the virtual map
 */
public final class OnDiskValueSerializer<V> implements ValueSerializer<OnDiskValue<V>> {

    private static final long CLASS_ID = 0x3992113882234886L;

    private static final long DATA_VERSION = 1;
    private final StateMetadata<?, V> md;

    // guesstimate of the typical size of a serialized value
    private static final int TYPICAL_SIZE = 1024;

    // Default constructor provided for ConstructableRegistry, TO BE REMOVED ASAP
    @Deprecated(forRemoval = true)
    public OnDiskValueSerializer() {
        md = null;
    }

    /**
     * Create a new instance. This is created at registration time, it doesn't need to serialize
     * anything to disk.
     */
    public OnDiskValueSerializer(@NonNull final StateMetadata<?, V> md) {
        this.md = Objects.requireNonNull(md);
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        // SHOULD NOT ALLOW md TO BE NULL, but ConstructableRegistry has foiled me.
        return md == null ? CLASS_ID : md.onDiskValueSerializerClassId();
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return 1;
    }

    /** {@inheritDoc} */
    @Override
    public int serialize(OnDiskValue<V> value, ByteBuffer byteBuffer) throws IOException {
        return value.serializeReturningWrittenBytes(byteBuffer);
    }

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getTypicalSerializedSize() {
        return TYPICAL_SIZE;
    }

    @Override
    public OnDiskValue<V> deserialize(ByteBuffer byteBuffer, long dataVersion) throws IOException {
        final OnDiskValue<V> value = new OnDiskValue<>(md);
        value.deserialize(byteBuffer, (int) dataVersion);
        return value;
    }
}
