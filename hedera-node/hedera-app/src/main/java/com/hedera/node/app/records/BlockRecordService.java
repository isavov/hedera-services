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

package com.hedera.node.app.records;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.records.impl.BlockRecordManagerImpl;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.*;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link Service} for managing the state of the running hashes and block information. Used by the
 * {@link BlockRecordManagerImpl}. This service is not exposed outside `hedera-app`.
 */
@SuppressWarnings("rawtypes")
@Singleton
public final class BlockRecordService implements Service {
    private static final Logger logger = LogManager.getLogger(BlockRecordService.class);
    /** The name of this service */
    public static final String NAME = "BlockRecordService";
    /** The key for the {@link RunningHashes} object in state */
    public static final String RUNNING_HASHES_STATE_KEY = "RUNNING_HASHES";
    /** The key for the {@link BlockInfo} object in state */
    public static final String BLOCK_INFO_STATE_KEY = "BLOCKS";

    /** The original hash, only used at genesis */
    private static final Bytes GENESIS_HASH = Bytes.wrap(new byte[48]);

    public static final Timestamp EPOCH = new Timestamp(0, 0);

    @NonNull
    @Override
    public String getServiceName() {
        return NAME;
    }

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry, final SemanticVersion version) {
        registry.register(new Schema(version) {
            /** {@inheritDoc} */
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(
                        StateDefinition.singleton(RUNNING_HASHES_STATE_KEY, RunningHashes.PROTOBUF),
                        StateDefinition.singleton(BLOCK_INFO_STATE_KEY, BlockInfo.PROTOBUF));
            }

            /** {@inheritDoc} */
            @Override
            public void migrate(@NonNull final MigrationContext ctx) {
                final var runningHashState = ctx.newStates().getSingleton(RUNNING_HASHES_STATE_KEY);
                final var blocksState = ctx.newStates().getSingleton(BLOCK_INFO_STATE_KEY);
                final var isGenesis = ctx.previousStates().isEmpty();
                if (isGenesis) {
                    final var blocks = new BlockInfo(-1, EPOCH, Bytes.EMPTY, EPOCH, false, EPOCH);
                    blocksState.put(blocks);
                    final var runningHashes =
                            RunningHashes.newBuilder().runningHash(GENESIS_HASH).build();
                    runningHashState.put(runningHashes);
                }
            }

            @Override
            public void restart(@NonNull final MigrationContext ctx) {
                // TODO : seems there should be something done here to match hash of the state after loading on restart
            }
        });
    }
}
