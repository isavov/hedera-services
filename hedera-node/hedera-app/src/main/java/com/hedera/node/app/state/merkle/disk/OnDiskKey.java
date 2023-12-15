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

package com.hedera.node.app.state.merkle.disk;

import static com.hedera.node.app.state.merkle.StateUtils.readFromStream;
import static com.hedera.node.app.state.merkle.StateUtils.writeToStream;

import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * An implementation of {@link VirtualKey} for Hedera applications.
 *
 * <p>The {@link OnDiskKey} is actually a wrapper for the "real" key, which is some business logic
 * object of type {@code K}. For example, the "real" key may be {@code AccountID}, but it must be
 * wrapped by an {@link OnDiskKey} to adapt it for use by the {@link VirtualMap}.
 *
 * <p>The {@code AccountID} itself is not directly serializable, and therefore a {@link Codec} is
 * provided to handle all serialization needs for the "real" key. The {@link Codec} is used to
 * convert the "real" key into bytes for hashing, saving to disk via the {@link VirtualMap}, reading
 * from disk, reconnect, and for state saving.
 *
 * @param <K> The type of key
 */
public final class OnDiskKey<K> implements VirtualKey {
    @Deprecated(forRemoval = true)
    private static final long CLASS_ID = 0x2929238293892373L;
    /** The metadata */
    private final StateMetadata<K, ?> md;
    /** The {@link Codec} used for handling serialization for the "real" key. */
    private final Codec<K> codec;
    /** The "real" key, such as AccountID. */
    private K key;

    // Default constructor provided for ConstructableRegistry, TO BE REMOVED ASAP
    @Deprecated(forRemoval = true)
    public OnDiskKey() {
        md = null;
        codec = null;
    }

    /**
     * Creates a new OnDiskKey. Used by {@link OnDiskKeySerializer}.
     *
     * @param md The state metadata
     */
    public OnDiskKey(final StateMetadata<K, ?> md) {
        this.md = md;
        this.codec = md.stateDefinition().keyCodec();
    }

    /**
     * Creates a new OnDiskKey.
     *
     * @param md The state metadata
     * @param key The "real" key
     */
    public OnDiskKey(final StateMetadata<K, ?> md, @NonNull final K key) {
        this(md);
        this.key = Objects.requireNonNull(key);
    }

    @NonNull
    public K getKey() {
        return key;
    }

    /** Writes the "real" key to the given stream. {@inheritDoc} */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        writeToStream(out, codec, key);
    }

    @Override
    public void serialize(@NonNull final ByteBuffer byteBuffer) throws IOException {
        serializeReturningWrittenBytes(byteBuffer);
    }

    public int serializeReturningWrittenBytes(@NonNull ByteBuffer byteBuffer) throws IOException {
        int initPos = byteBuffer.position();
        final var output = BufferedData.wrap(byteBuffer);
        output.skip(Integer.BYTES);
        codec.write(key, output);
        final var pos = output.position();
        output.position(initPos);
        output.writeInt((int) (pos - initPos - Integer.BYTES));
        output.position(pos);
        return (int) (pos - initPos);
    }

    @Override
    public void deserialize(@NonNull final ByteBuffer byteBuffer, int ignored) throws IOException {
        final var buff = BufferedData.wrap(byteBuffer);
        final var len = buff.readInt();
        final var pos = buff.position();
        final var oldLimit = buff.limit();
        buff.limit(pos + len);
        key = codec.parse(buff);
        buff.limit(oldLimit);
    }

    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, int ignored) throws IOException {
        key = readFromStream(in, codec);
    }

    @Override
    public long getClassId() {
        // SHOULD NOT ALLOW md TO BE NULL, but ConstructableRegistry has foiled me.
        return md == null ? CLASS_ID : md.onDiskKeyClassId();
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OnDiskKey<?> onDiskKey)) return false;
        return Objects.equals(key, onDiskKey.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "OnDiskKey{" + "key=" + key + '}';
    }
}
