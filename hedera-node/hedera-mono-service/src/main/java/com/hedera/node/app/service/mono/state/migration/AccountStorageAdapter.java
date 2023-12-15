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

package com.hedera.node.app.service.mono.state.migration;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.common.crypto.Hash;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AccountStorageAdapter {
    private static final Logger log = LogManager.getLogger(AccountStorageAdapter.class);
    private static final int THREAD_COUNT = 32;
    private final boolean accountsOnDisk;
    private final @Nullable MerkleMapLike<EntityNum, MerkleAccount> inMemoryAccounts;
    private final @Nullable VirtualMapLike<EntityNumVirtualKey, OnDiskAccount> onDiskAccounts;

    public static AccountStorageAdapter fromInMemory(final MerkleMapLike<EntityNum, MerkleAccount> accounts) {
        return new AccountStorageAdapter(accounts, null);
    }

    public static AccountStorageAdapter fromOnDisk(final VirtualMapLike<EntityNumVirtualKey, OnDiskAccount> accounts) {
        return new AccountStorageAdapter(null, accounts);
    }

    private AccountStorageAdapter(
            @Nullable final MerkleMapLike<EntityNum, MerkleAccount> inMemoryAccounts,
            @Nullable final VirtualMapLike<EntityNumVirtualKey, OnDiskAccount> onDiskAccounts) {
        if (inMemoryAccounts != null) {
            this.accountsOnDisk = false;
            this.inMemoryAccounts = inMemoryAccounts;
            this.onDiskAccounts = null;
        } else {
            this.accountsOnDisk = true;
            this.inMemoryAccounts = null;
            this.onDiskAccounts = onDiskAccounts;
        }
    }

    public HederaAccount get(final EntityNum num) {
        return accountsOnDisk ? onDiskAccounts.get(EntityNumVirtualKey.from(num)) : inMemoryAccounts.get(num);
    }

    public HederaAccount getForModify(final EntityNum num) {
        return accountsOnDisk
                ? onDiskAccounts.getForModify(EntityNumVirtualKey.from(num))
                : inMemoryAccounts.getForModify(num);
    }

    public void put(final EntityNum num, final HederaAccount wrapper) {
        if (accountsOnDisk) {
            wrapper.setEntityNum(num);
            onDiskAccounts.put(EntityNumVirtualKey.from(num), (OnDiskAccount) wrapper);
        } else {
            inMemoryAccounts.put(num, (MerkleAccount) wrapper);
        }
    }

    public void remove(final EntityNum num) {
        if (accountsOnDisk) {
            onDiskAccounts.remove(EntityNumVirtualKey.from(num));
        } else {
            inMemoryAccounts.remove(num);
        }
    }

    public long size() {
        return accountsOnDisk ? onDiskAccounts.size() : inMemoryAccounts.size();
    }

    public boolean containsKey(final EntityNum num) {
        return accountsOnDisk
                ? onDiskAccounts.containsKey(EntityNumVirtualKey.from(num))
                : inMemoryAccounts.containsKey(num);
    }

    public Hash getHash() {
        return accountsOnDisk ? onDiskAccounts.getHash() : inMemoryAccounts.getHash();
    }

    public Set<EntityNum> keySet() {
        if (accountsOnDisk) {
            final Set<EntityNum> allAccountNums = new HashSet<>();
            forEachOnDisk((num, account) -> allAccountNums.add(num));
            return allAccountNums;
        } else {
            return inMemoryAccounts.keySet();
        }
    }

    public void forEach(final BiConsumer<EntityNum, HederaAccount> visitor) {
        if (accountsOnDisk) {
            forEachOnDisk(visitor);
        } else {
            inMemoryAccounts.forEach(visitor);
        }
    }

    private void forEachOnDisk(final BiConsumer<EntityNum, HederaAccount> visitor) {
        try {
            onDiskAccounts.extractVirtualMapData(
                    getStaticThreadManager(),
                    entry -> visitor.accept(entry.left().asEntityNum(), entry.right()),
                    THREAD_COUNT);
        } catch (final InterruptedException e) {
            log.error("Interrupted while extracting VM data", e);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public void forEachParallel(final BiConsumer<EntityNum, HederaAccount> visitor) {
        if (accountsOnDisk) {
            try {
                onDiskAccounts.extractVirtualMapDataC(
                        getStaticThreadManager(),
                        entry -> visitor.accept(entry.left().asEntityNum(), entry.right()),
                        THREAD_COUNT);
            } catch (final InterruptedException e) {
                log.error("Interrupted while extracting VM data", e);
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        } else {
            inMemoryAccounts.forEach(visitor);
        }
    }

    public boolean areOnDisk() {
        return accountsOnDisk;
    }

    @Nullable
    public MerkleMapLike<EntityNum, MerkleAccount> getInMemoryAccounts() {
        return inMemoryAccounts;
    }

    @Nullable
    public VirtualMapLike<EntityNumVirtualKey, OnDiskAccount> getOnDiskAccounts() {
        return onDiskAccounts;
    }
}
