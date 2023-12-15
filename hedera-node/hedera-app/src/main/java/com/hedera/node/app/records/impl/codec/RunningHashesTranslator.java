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

package com.hedera.node.app.records.impl.codec;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

public final class RunningHashesTranslator {
    private RunningHashesTranslator() {
        throw new IllegalStateException("Utility class");
    }

    @NonNull
    /**
     * Converts {@link com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf} to {@link RunningHashes}.
     * @param recordsRunningHashLeaf the {@link com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf}
     * @return the {@link RunningHashes} converted from the {@link com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf}
     */
    public static RunningHashes runningHashesFromRecordsRunningHashLeaf(
            @NonNull final com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf recordsRunningHashLeaf) {
        requireNonNull(recordsRunningHashLeaf);
        return new RunningHashes.Builder()
                .runningHash(Bytes.wrap(
                        recordsRunningHashLeaf.getRunningHash().getHash().getValue()))
                .nMinus1RunningHash(Bytes.wrap(
                        recordsRunningHashLeaf.getNMinus1RunningHash().getHash().getValue()))
                .nMinus2RunningHash(Bytes.wrap(
                        recordsRunningHashLeaf.getNMinus2RunningHash().getHash().getValue()))
                .nMinus3RunningHash(Bytes.wrap(
                        recordsRunningHashLeaf.getNMinus3RunningHash().getHash().getValue()))
                .build();
    }
}
