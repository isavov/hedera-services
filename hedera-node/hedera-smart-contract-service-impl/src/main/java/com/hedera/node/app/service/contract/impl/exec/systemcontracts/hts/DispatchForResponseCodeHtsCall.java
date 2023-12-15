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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall.OutputFn.STANDARD_OUTPUT_FN;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.encodedRc;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.standardized;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Function;

/**
 * An HTS call that simply dispatches a synthetic transaction body and returns a result that is
 * an encoded {@link com.hedera.hapi.node.base.ResponseCodeEnum}.
 *
 * @param <T> the type of the record builder to expect from the dispatch
 */
public class DispatchForResponseCodeHtsCall<T extends SingleTransactionRecordBuilder> extends AbstractHtsCall {
    /**
     * The "standard" failure customizer that replaces {@link ResponseCodeEnum#INVALID_SIGNATURE} with
     * {@link ResponseCodeEnum#INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE}. (Note this code no longer
     * makes sense after the security model change that revoked use of top-level signatures; but for
     * now it is retained for backwards compatibility.)
     */
    private static final FailureCustomizer STANDARD_FAILURE_CUSTOMIZER =
            (body, code, enhancement) -> standardized(code);

    private final AccountID senderId;
    private final TransactionBody syntheticBody;
    private final Class<T> recordBuilderType;
    private final OutputFn outputFn;
    private final FailureCustomizer failureCustomizer;
    private final VerificationStrategy verificationStrategy;
    private final DispatchGasCalculator dispatchGasCalculator;

    /**
     * A customizer that can be used to modify the failure status of a dispatch.
     */
    @FunctionalInterface
    public interface FailureCustomizer {
        /**
         * A no-op customizer that simply returns the original failure code.
         */
        FailureCustomizer NOOP_CUSTOMIZER = (body, code, enhancement) -> code;

        /**
         * Customizes the failure status of a dispatch.
         *
         * @param syntheticBody the synthetic body that was dispatched
         * @param code the failure code
         * @param enhancement the enhancement that was used
         * @return the customized failure code
         */
        @NonNull
        ResponseCodeEnum customize(
                @NonNull TransactionBody syntheticBody,
                @NonNull ResponseCodeEnum code,
                @NonNull HederaWorldUpdater.Enhancement enhancement);
    }

    /**
     * A function that can be used to generate the output of a dispatch from its completed
     * record builder.
     */
    public interface OutputFn extends Function<ContractCallRecordBuilder, ByteBuffer> {
        /**
         * The standard output function that simply returns the encoded status.
         */
        OutputFn STANDARD_OUTPUT_FN = recordBuilder -> encodedRc(recordBuilder.status());
    }

    /**
     * Convenience overload that slightly eases construction for the most common case.
     *
     * @param attempt the attempt to translate to a dispatching
     * @param syntheticBody the synthetic body to dispatch
     * @param recordBuilderType the type of the record builder to expect from the dispatch
     * @param dispatchGasCalculator the dispatch gas calculator to use
     */
    public DispatchForResponseCodeHtsCall(
            @NonNull final HtsCallAttempt attempt,
            @NonNull final TransactionBody syntheticBody,
            @NonNull final Class<T> recordBuilderType,
            @NonNull final DispatchGasCalculator dispatchGasCalculator) {
        this(
                attempt.enhancement(),
                attempt.systemContractGasCalculator(),
                attempt.addressIdConverter().convertSender(attempt.senderAddress()),
                syntheticBody,
                recordBuilderType,
                attempt.defaultVerificationStrategy(),
                dispatchGasCalculator,
                STANDARD_FAILURE_CUSTOMIZER,
                STANDARD_OUTPUT_FN);
    }

    /**
     * Convenience overload that eases construction with a failure status customizer.
     *
     * @param attempt the attempt to translate to a dispatching
     * @param syntheticBody the synthetic body to dispatch
     * @param recordBuilderType the type of the record builder to expect from the dispatch
     * @param dispatchGasCalculator the dispatch gas calculator to use
     */
    public DispatchForResponseCodeHtsCall(
            @NonNull final HtsCallAttempt attempt,
            @NonNull final TransactionBody syntheticBody,
            @NonNull final Class<T> recordBuilderType,
            @NonNull final DispatchGasCalculator dispatchGasCalculator,
            @NonNull final FailureCustomizer failureCustomizer) {
        this(
                attempt.enhancement(),
                attempt.systemContractGasCalculator(),
                attempt.addressIdConverter().convertSender(attempt.senderAddress()),
                syntheticBody,
                recordBuilderType,
                attempt.defaultVerificationStrategy(),
                dispatchGasCalculator,
                failureCustomizer,
                STANDARD_OUTPUT_FN);
    }

    /**
     * Convenience overload that eases construction with a custom output function.
     *
     * @param attempt the attempt to translate to a dispatching
     * @param syntheticBody the synthetic body to dispatch
     * @param recordBuilderType the type of the record builder to expect from the dispatch
     * @param dispatchGasCalculator the dispatch gas calculator to use
     */
    public DispatchForResponseCodeHtsCall(
            @NonNull final HtsCallAttempt attempt,
            @NonNull final TransactionBody syntheticBody,
            @NonNull final Class<T> recordBuilderType,
            @NonNull final DispatchGasCalculator dispatchGasCalculator,
            @NonNull final OutputFn outputFn) {
        this(
                attempt.enhancement(),
                attempt.systemContractGasCalculator(),
                attempt.addressIdConverter().convertSender(attempt.senderAddress()),
                syntheticBody,
                recordBuilderType,
                attempt.defaultVerificationStrategy(),
                dispatchGasCalculator,
                STANDARD_FAILURE_CUSTOMIZER,
                outputFn);
    }

    /**
     * More general constructor, for cases where perhaps a custom {@link VerificationStrategy} is needed.
     *
     * @param enhancement the enhancement to use
     * @param senderId the id of the spender
     * @param syntheticBody the synthetic body to dispatch
     * @param recordBuilderType the type of the record builder to expect from the dispatch
     * @param verificationStrategy the verification strategy to use
     * @param dispatchGasCalculator the dispatch gas calculator to use
     * @param failureCustomizer the status customizer to use
     * @param outputFn the output function to use
     */
    // too many parameters
    @SuppressWarnings("java:S107")
    public <U extends SingleTransactionRecordBuilder> DispatchForResponseCodeHtsCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final AccountID senderId,
            @NonNull final TransactionBody syntheticBody,
            @NonNull final Class<T> recordBuilderType,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final DispatchGasCalculator dispatchGasCalculator,
            @NonNull final FailureCustomizer failureCustomizer,
            @NonNull final OutputFn outputFn) {
        super(gasCalculator, enhancement, false);
        this.senderId = Objects.requireNonNull(senderId);
        this.outputFn = Objects.requireNonNull(outputFn);
        this.syntheticBody = Objects.requireNonNull(syntheticBody);
        this.recordBuilderType = Objects.requireNonNull(recordBuilderType);
        this.verificationStrategy = Objects.requireNonNull(verificationStrategy);
        this.dispatchGasCalculator = Objects.requireNonNull(dispatchGasCalculator);
        this.failureCustomizer = Objects.requireNonNull(failureCustomizer);
    }

    @Override
    public @NonNull PricedResult execute() {
        final var recordBuilder = systemContractOperations()
                .dispatch(syntheticBody, verificationStrategy, senderId, ContractCallRecordBuilder.class);
        final var gasRequirement =
                dispatchGasCalculator.gasRequirement(syntheticBody, gasCalculator, enhancement, senderId);
        var status = recordBuilder.status();
        if (status != SUCCESS) {
            status = failureCustomizer.customize(syntheticBody, status, enhancement);
            recordBuilder.status(status);
        }
        return completionWith(gasRequirement, recordBuilder, outputFn.apply(recordBuilder));
    }
}
