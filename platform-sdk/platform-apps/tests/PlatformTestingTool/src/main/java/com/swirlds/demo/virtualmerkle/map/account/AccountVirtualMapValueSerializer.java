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

package com.swirlds.demo.virtualmerkle.map.account;

import com.swirlds.merkledb.serialize.ValueSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AccountVirtualMapValueSerializer implements ValueSerializer<AccountVirtualMapValue> {

    private static final long CLASS_ID = 0x7f4caa05eae90b01L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    @Override
    public int getSerializedSize() {
        return AccountVirtualMapValue.getSizeInBytes();
    }

    @Override
    public int serialize(AccountVirtualMapValue data, ByteBuffer buffer) throws IOException {
        data.serialize(buffer);
        return getSerializedSize();
    }

    @Override
    public AccountVirtualMapValue deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        if (dataVersion != getCurrentDataVersion()) {
            throw new IllegalStateException("Data version mismatch");
        }
        final AccountVirtualMapValue data = new AccountVirtualMapValue();
        data.deserialize(buffer, (int) dataVersion);
        return data;
    }
}
