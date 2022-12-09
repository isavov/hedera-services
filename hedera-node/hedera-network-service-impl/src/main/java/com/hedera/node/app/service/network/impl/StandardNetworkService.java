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
package com.hedera.node.app.service.network.impl;

import com.hedera.node.app.service.network.NetworkPreTransactionHandler;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Standard implementation of the {@link NetworkService} {@link com.hedera.node.app.spi.Service}.
 */
public final class StandardNetworkService implements NetworkService {
    @NonNull
    @Override
    public NetworkPreTransactionHandler createPreTransactionHandler(
            @NonNull States states, @NonNull PreHandleContext ctx) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
