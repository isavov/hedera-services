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

package com.hedera.node.app.service.token.records;

import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Represents the context of used for finalizing a user transaction.
 */
@SuppressWarnings("UnusedReturnValue")
public interface FinalizeContext extends ChildFinalizeContext {
    /**
     * Returns the current consensus time.
     *
     * @return the current consensus time
     */
    @NonNull
    Instant consensusTime();

    /**
     * Returns the current {@link Configuration} for the node.
     *
     * @return the {@code Configuration}
     */
    @NonNull
    Configuration configuration();

    /**
     * Indicates whether or not the transaction has any child records.
     *
     * @return {@code true} if the transaction has child records; otherwise {@code false}
     */
    boolean hasChildRecords();

    /**
     * This method can be used to iterate over all child records.
     *
     * @param recordBuilderClass the record type
     * @param consumer the consumer to be called for each record
     * @param <T> the record type
     * @throws NullPointerException if any parameter is {@code null}
     * @throws IllegalArgumentException if the record builder type is unknown to the app
     */
    <T> void forEachChildRecord(@NonNull Class<T> recordBuilderClass, @NonNull Consumer<T> consumer);
}
