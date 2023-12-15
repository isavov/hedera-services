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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code RecordBuilder} that collects and builds the information required to create a synthetic
 * account record, specifically for a system account created during node genesis (startup)
 */
public interface GenesisAccountRecordBuilder {

    /**
     * Tracks the created account ID for the system account
     */
    @NonNull
    GenesisAccountRecordBuilder accountID(@NonNull final AccountID accountID);

    /**
     * Tracks the synthetic transaction that represents the created system account
     */
    @NonNull
    GenesisAccountRecordBuilder transaction(@NonNull final Transaction txn);

    /**
     * Tracks the memo for the synthetic record
     */
    @NonNull
    GenesisAccountRecordBuilder memo(@NonNull final String memo);
}
