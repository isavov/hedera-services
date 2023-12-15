/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.ledger.backing;

import static com.hedera.node.app.service.mono.utils.EntityNum.fromTokenId;

import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.Supplier;

public class BackingTokens implements BackingStore<TokenID, MerkleToken> {
    private final Supplier<MerkleMapLike<EntityNum, MerkleToken>> delegate;

    public BackingTokens(Supplier<MerkleMapLike<EntityNum, MerkleToken>> delegate) {
        this.delegate = delegate;
    }

    @Override
    public MerkleToken getRef(TokenID id) {
        return delegate.get().getForModify(fromTokenId(id));
    }

    @Override
    public void put(TokenID id, MerkleToken token) {
        final var tokens = delegate.get();
        final var eId = fromTokenId(id);
        if (!tokens.containsKey(eId)) {
            tokens.put(eId, token);
        }
    }

    @Override
    public boolean contains(TokenID id) {
        return delegate.get().containsKey(EntityNum.fromTokenId(id));
    }

    @Override
    public void remove(TokenID id) {
        delegate.get().remove(fromTokenId(id));
    }

    @Override
    public long size() {
        return delegate.get().size();
    }

    @Override
    public MerkleToken getImmutableRef(TokenID id) {
        return delegate.get().get(fromTokenId(id));
    }

    /* -- only for unit tests */
    public Supplier<MerkleMapLike<EntityNum, MerkleToken>> getDelegate() {
        return delegate;
    }
}
