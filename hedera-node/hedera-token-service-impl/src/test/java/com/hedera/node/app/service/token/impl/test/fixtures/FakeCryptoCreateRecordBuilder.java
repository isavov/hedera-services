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

package com.hedera.node.app.service.token.impl.test.fixtures;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.jetbrains.annotations.NotNull;

public class FakeCryptoCreateRecordBuilder {
    public FakeCryptoCreateRecordBuilder() {}

    public CryptoCreateRecordBuilder create() {
        return new CryptoCreateRecordBuilder() {

            private AccountID accountID;
            private Bytes evmAddress;
            private long transactionFee;
            private String memo;

            @NotNull
            @Override
            public ResponseCodeEnum status() {
                return ResponseCodeEnum.SUCCESS;
            }

            @NotNull
            @Override
            public CryptoCreateRecordBuilder accountID(@NotNull final AccountID accountID) {
                this.accountID = accountID;
                return this;
            }

            @Override
            public SingleTransactionRecordBuilder status(@NotNull ResponseCodeEnum status) {
                return this;
            }

            @NotNull
            @Override
            public CryptoCreateRecordBuilder evmAddress(@NotNull final Bytes evmAddress) {
                this.evmAddress = evmAddress;
                return this;
            }

            @NotNull
            @Override
            public CryptoCreateRecordBuilder transactionFee(@NotNull final long transactionFee) {
                this.transactionFee = transactionFee;
                return this;
            }

            @NotNull
            @Override
            public CryptoCreateRecordBuilder memo(@NotNull final String memo) {
                this.memo = memo;
                return this;
            }
        };
    }
}
