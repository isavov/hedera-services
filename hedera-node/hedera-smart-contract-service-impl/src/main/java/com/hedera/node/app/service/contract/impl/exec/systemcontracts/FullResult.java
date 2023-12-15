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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.ByteBuffer;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

/**
 * Represents the result of executing a Hedera system contract.
 *
 * @param result the result of the computation
 * @param gasRequirement the gas requirement of the computation
 * @param recordBuilder the record builder, if any, generated as a side effect of the computation
 */
public record FullResult(
        @NonNull PrecompiledContract.PrecompileContractResult result,
        long gasRequirement,
        @Nullable ContractCallRecordBuilder recordBuilder) {
    public FullResult {
        requireNonNull(result);
    }

    public Bytes output() {
        return result.getOutput();
    }

    public boolean isRefundGas() {
        return result.isRefundGas();
    }

    public void recordInsufficientGas() {
        if (recordBuilder != null) {
            recordBuilder.status(INSUFFICIENT_GAS);
        }
    }

    public static FullResult revertResult(@NonNull final ResponseCodeEnum reason, final long gasRequirement) {
        requireNonNull(reason);
        return new FullResult(
                PrecompiledContract.PrecompileContractResult.revert(
                        Bytes.wrap(reason.protoName().getBytes())),
                gasRequirement,
                null);
    }

    public static FullResult revertResult(@NonNull Bytes reason, final long gasRequirement) {
        requireNonNull(reason);
        return new FullResult(PrecompiledContract.PrecompileContractResult.revert(reason), gasRequirement, null);
    }

    public static FullResult revertResult(
            @NonNull final ContractCallRecordBuilder recordBuilder, final long gasRequirement) {
        requireNonNull(recordBuilder);
        return new FullResult(
                PrecompiledContract.PrecompileContractResult.revert(
                        Bytes.wrap(recordBuilder.status().protoName().getBytes())),
                gasRequirement,
                recordBuilder);
    }

    public static FullResult haltResult(
            @NonNull final ContractCallRecordBuilder recordBuilder, final long gasRequirement) {
        requireNonNull(recordBuilder);
        final var reason = recordBuilder.status() == NOT_SUPPORTED
                ? CustomExceptionalHaltReason.NOT_SUPPORTED
                : CustomExceptionalHaltReason.ERROR_DECODING_PRECOMPILE_INPUT;
        return new FullResult(
                PrecompiledContract.PrecompileContractResult.halt(Bytes.EMPTY, Optional.of(reason)),
                gasRequirement,
                recordBuilder);
    }

    public static FullResult haltResult(@NonNull final ExceptionalHaltReason reason, final long gasRequirement) {
        requireNonNull(reason);
        return new FullResult(
                PrecompiledContract.PrecompileContractResult.halt(Bytes.EMPTY, Optional.of(reason)),
                gasRequirement,
                null);
    }

    public static FullResult successResult(
            @NonNull final ByteBuffer encoded,
            final long gasRequirement,
            @NonNull final ContractCallRecordBuilder recordBuilder) {
        requireNonNull(encoded);
        return new FullResult(
                PrecompiledContract.PrecompileContractResult.success(Bytes.wrap(encoded.array())),
                gasRequirement,
                recordBuilder);
    }

    public static FullResult successResult(@NonNull final ByteBuffer encoded, final long gasRequirement) {
        requireNonNull(encoded);
        return new FullResult(
                PrecompiledContract.PrecompileContractResult.success(Bytes.wrap(encoded.array())),
                gasRequirement,
                null);
    }

    public static FullResult haltResult(final long gasRequirement) {
        return new FullResult(
                PrecompiledContract.PrecompileContractResult.halt(Bytes.EMPTY, Optional.empty()), gasRequirement, null);
    }
}
