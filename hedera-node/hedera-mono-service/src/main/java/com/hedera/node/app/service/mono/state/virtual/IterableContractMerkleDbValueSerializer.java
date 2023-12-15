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

import com.swirlds.merkledb.serialize.ValueSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class IterableContractMerkleDbValueSerializer implements ValueSerializer<IterableContractValue> {

    static final long CLASS_ID = 0x2137d0dcac9ab2b3L;

    static final int CURRENT_VERSION = 1;

    static final long DATA_VERSION = 1;

    // Serializer info

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    // Value info

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    // FUTURE WORK: mark it as @Override after migration to platform 0.39
    public int getTypicalSerializedSize() {
        return IterableContractValue.getTypicalSerializedSize();
    }

    // Value serialization

    @Override
    public int serialize(final IterableContractValue value, final ByteBuffer out) throws IOException {
        Objects.requireNonNull(value);
        Objects.requireNonNull(out);
        value.serialize(out);
        return value.getSerializedSize();
    }

    // Value deserialization

    @Override
    public IterableContractValue deserialize(final ByteBuffer buffer, final long version) throws IOException {
        Objects.requireNonNull(buffer);
        final IterableContractValue value = new IterableContractValue();
        value.deserialize(buffer, (int) version);
        return value;
    }
}
