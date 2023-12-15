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

package com.swirlds.merkledb.files.hashmap;

import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.virtualmap.VirtualKey;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Serializer for writing buckets into a DataFile.
 *
 * @param <K> The map key type stored in the buckets
 */
public class BucketSerializer<K extends VirtualKey> implements DataItemSerializer<Bucket<K>> {
    /** Bucket pool used by this serializer */
    private final ReusableBucketPool<K> reusableBucketPool;

    /**
     * How many of the low-order bytes in the serialization version are devoted to non-key
     * serialization metadata.
     */
    private static final int LOW_ORDER_BYTES_FOR_NON_KEY_SERIALIZATION_VERSION = 32;
    /** The version number for serialization data format for this bucket */
    private static final int BUCKET_SERIALIZATION_VERSION = 1;

    /** The current combined serialization version, for both bucket header and key serializer */
    private final long currentSerializationVersion;
    /** The key serializer that we use for keys in buckets */
    private final KeySerializer<K> keySerializer;

    public BucketSerializer(final KeySerializer<K> keySerializer) {
        this.keySerializer = keySerializer;
        long keyVersion = keySerializer.getCurrentDataVersion();
        if (Long.numberOfLeadingZeros(keyVersion) < Integer.SIZE) {
            throw new IllegalArgumentException(
                    "KeySerializer versions used in buckets have to be less than a integer.");
        }
        currentSerializationVersion =
                (keySerializer.getCurrentDataVersion() << LOW_ORDER_BYTES_FOR_NON_KEY_SERIALIZATION_VERSION)
                        | BUCKET_SERIALIZATION_VERSION;
        reusableBucketPool = new ReusableBucketPool<>(this);
    }

    /**
     * Get the key serializer.
     *
     * @return a key serializer
     */
    public KeySerializer<K> getKeySerializer() {
        return keySerializer;
    }

    /**
     * Reusable bucket pool for this bucket serializer.
     *
     * @return This serializer's reusable bucket pool.
     */
    public ReusableBucketPool<K> getBucketPool() {
        return reusableBucketPool;
    }

    /**
     * Get the number of bytes used for data item header
     *
     * @return size of header in bytes
     */
    @Override
    public int getHeaderSize() {
        return Integer.BYTES + Integer.BYTES;
    }

    /**
     * Deserialize data item header from the given byte buffer
     *
     * @param buffer Buffer to read from
     * @return The read header
     */
    @Override
    public DataItemHeader deserializeHeader(final ByteBuffer buffer) {
        int bucketIndex = buffer.getInt();
        int size = buffer.getInt();
        return new DataItemHeader(size, bucketIndex);
    }

    /**
     * Get the number of bytes a data item takes when serialized
     *
     * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
     */
    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    /**
     * Get the current serialization version. This a combination of the bucket header's
     * serialization version and the KeySerializer's serialization version.
     */
    @Override
    public long getCurrentDataVersion() {
        return currentSerializationVersion;
    }

    /**
     * Deserialize a data item from a byte buffer, that was written with given data version. The
     * resulting bucket, if not null, must be closed by the caller.
     *
     * @param buffer The buffer to read from
     * @param dataVersion The serialization version the data item was written with
     * @return Deserialized data item
     */
    @Override
    public Bucket<K> deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
        final Bucket<K> bucket = reusableBucketPool.getBucket();
        bucket.putAllData(buffer);
        // split bucketSerializationVersion
        bucket.setKeySerializationVersion((int) (dataVersion >> LOW_ORDER_BYTES_FOR_NON_KEY_SERIALIZATION_VERSION));
        return bucket;
    }

    /** {@inheritDoc} */
    @Override
    public int serialize(final Bucket<K> bucket, final ByteBuffer buffer) throws IOException {
        return bucket.writeToByteBuffer(buffer);
    }
}
