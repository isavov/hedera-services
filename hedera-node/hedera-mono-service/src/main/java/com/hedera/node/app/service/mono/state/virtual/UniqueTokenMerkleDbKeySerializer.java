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

package com.hedera.node.app.service.mono.state.virtual;

import com.swirlds.merkledb.serialize.KeySerializer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class UniqueTokenMerkleDbKeySerializer implements KeySerializer<UniqueTokenKey> {

    // Serializer class ID
    static final long CLASS_ID = 0xb3c94b6cf62aa6c5L;

    // Serializer version
    static final int CURRENT_VERSION = 1;

    // Key data version
    static final long DATA_VERSION = UniqueTokenKey.CURRENT_VERSION;

    // Serializer info

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    // Data version

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    // Key serialization

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getTypicalSerializedSize() {
        return UniqueTokenKey.ESTIMATED_SIZE_BYTES;
    }

    @Override
    public int serialize(final UniqueTokenKey key, final ByteBuffer buffer) throws IOException {
        Objects.requireNonNull(key);
        Objects.requireNonNull(buffer);
        return key.serializeTo(buffer::put);
    }

    // Key deserialization

    @Override
    public int deserializeKeySize(final ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
        return UniqueTokenKey.deserializeKeySize(buffer);
    }

    @Override
    public UniqueTokenKey deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
        Objects.requireNonNull(buffer);
        final UniqueTokenKey tokenKey = new UniqueTokenKey();
        tokenKey.deserialize(buffer, (int) dataVersion);
        return tokenKey;
    }

    @Override
    public boolean equals(final ByteBuffer buffer, final int version, final UniqueTokenKey key) throws IOException {
        Objects.requireNonNull(buffer);
        return key.equalsTo(buffer);
    }
}
