/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.token.impl;

import com.hedera.node.app.service.token.TokenPreTransactionHandler;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;

/** Standard implementation of the {@link TokenService} {@link com.hedera.node.app.spi.Service}. */
public final class StandardTokenService implements TokenService {
    @NonNull
    @Override
    public TokenPreTransactionHandler createPreTransactionHandler(
            @NonNull States states, @NonNull PreHandleContext ctx) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
