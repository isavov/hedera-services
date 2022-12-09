/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi;

import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A definition of an interface that will be implemented by each conceptual "service" like
 * crypto-service, token-service etc.,
 */
public interface Service {
    /**
     * Creates and returns a new {@link PreTransactionHandler}
     *
     * @return A new {@link PreTransactionHandler}
     */
    @NonNull
    PreTransactionHandler createPreTransactionHandler(
            @NonNull States states, @NonNull PreHandleContext ctx);

    /**
     * Creates and returns a new {@link TransactionHandler}
     *
     * @return A new {@link TransactionHandler}
     */
    default @NonNull TransactionHandler createTransactionHandler(@NonNull States states) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates and returns a new {@link QueryHandler}
     *
     * @return A new {@link QueryHandler}
     */
    default @NonNull QueryHandler createQueryHandler(@NonNull States states) {
        throw new UnsupportedOperationException();
    }
}
