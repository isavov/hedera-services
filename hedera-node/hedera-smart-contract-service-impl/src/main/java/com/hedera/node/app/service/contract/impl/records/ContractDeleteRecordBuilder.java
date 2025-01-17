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

package com.hedera.node.app.service.contract.impl.records;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.node.app.spi.workflows.record.DeleteCapableTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A {@code RecordBuilder} specialization for tracking the side effects of a {@code ContractDelete}.
 */
public interface ContractDeleteRecordBuilder extends DeleteCapableTransactionRecordBuilder {
    /**
     * Tracks the contract id deleted by a successful top-level contract deletion.
     *
     * @param contractId the {@link ContractID} of the deleted top-level contract
     * @return this builder
     */
    @NonNull
    ContractDeleteRecordBuilder contractID(@Nullable ContractID contractId);

    @NonNull
    ContractDeleteRecordBuilder transaction(@NonNull final Transaction txn);
}
