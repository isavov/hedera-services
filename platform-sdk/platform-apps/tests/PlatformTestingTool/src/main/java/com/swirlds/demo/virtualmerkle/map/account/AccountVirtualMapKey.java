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

package com.swirlds.demo.virtualmerkle.map.account;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * This class represents the key to find an account that is being
 * stored inside a {@link com.swirlds.virtualmap.VirtualMap} instance.
 */
public class AccountVirtualMapKey implements VirtualKey {
    private static final long CLASS_ID = 0xff95b64a8d311cdaL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private long realmID;
    private long shardId;
    private long accountID;

    public AccountVirtualMapKey() {
        this(0, 0, 0);
    }

    public AccountVirtualMapKey(final long realmID, final long shardId, final long accountID) {
        this.realmID = realmID;
        this.shardId = shardId;
        this.accountID = accountID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
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
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(realmID);
        out.writeLong(shardId);
        out.writeLong(accountID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        this.realmID = in.readLong();
        this.shardId = in.readLong();
        this.accountID = in.readLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final ByteBuffer buffer) throws IOException {
        buffer.putLong(realmID);
        buffer.putLong(shardId);
        buffer.putLong(accountID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final ByteBuffer buffer, final int version) throws IOException {
        this.realmID = buffer.getLong();
        this.shardId = buffer.getLong();
        this.accountID = buffer.getLong();
    }

    public boolean equals(final ByteBuffer buffer, final int version) throws IOException {
        return realmID == buffer.getLong() && shardId == buffer.getLong() && accountID == buffer.getLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "AccountVirtualMapKey{" + "realmID="
                + realmID + ", shardId="
                + shardId + ", accountID="
                + accountID + '}';
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        final AccountVirtualMapKey that = (AccountVirtualMapKey) other;
        return realmID == that.realmID && shardId == that.shardId && accountID == that.accountID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(realmID, shardId, accountID);
    }

    public static int getSizeInBytes() {
        return 3 * Long.BYTES;
    }

    /**
     * @return The id of the account that can be found by this key.
     */
    public long getAccountID() {
        return accountID;
    }
}
