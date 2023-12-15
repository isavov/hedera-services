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

import com.swirlds.merkledb.serialize.AbstractFixedSizeKeySerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This is the key serializer for the {@link AccountVirtualMapKey}.
 */
public class AccountVirtualMapKeySerializer extends AbstractFixedSizeKeySerializer<AccountVirtualMapKey> {

    private static final long CLASS_ID = 0x93efc6111338834eL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    public AccountVirtualMapKeySerializer() {
        super(CLASS_ID, ClassVersion.ORIGINAL, AccountVirtualMapKey.getSizeInBytes(), 1, AccountVirtualMapKey::new);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final ByteBuffer buffer, final int dataVersion, final AccountVirtualMapKey keyToCompare)
            throws IOException {
        return keyToCompare.equals(buffer, dataVersion);
    }
}
