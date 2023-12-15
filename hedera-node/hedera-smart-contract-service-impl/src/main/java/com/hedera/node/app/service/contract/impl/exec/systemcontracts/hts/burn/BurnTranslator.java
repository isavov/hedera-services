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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.INT64_INT64;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn.BurnDecoder.BURN_OUTPUT_FN;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;

public class BurnTranslator extends AbstractHtsCallTranslator {
    public static final Function BURN_TOKEN_V1 = new Function("burnToken(address,uint64,int64[])", INT64_INT64);
    public static final Function BURN_TOKEN_V2 = new Function("burnToken(address,int64,int64[])", INT64_INT64);

    BurnDecoder decoder;

    @Inject
    public BurnTranslator(@NonNull final BurnDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), BurnTranslator.BURN_TOKEN_V1.selector())
                || Arrays.equals(attempt.selector(), BurnTranslator.BURN_TOKEN_V2.selector());
    }

    @Override
    public HtsCall callFrom(@NonNull HtsCallAttempt attempt) {
        final var body = bodyForClassic(attempt);
        final var isFungibleMint = body.tokenBurnOrThrow().serialNumbers().isEmpty();
        return new DispatchForResponseCodeHtsCall<>(
                attempt,
                body,
                SingleTransactionRecordBuilder.class,
                isFungibleMint ? BurnTranslator::fungibleBurnGasRequirement : BurnTranslator::nftBurnGasRequirement,
                BURN_OUTPUT_FN);
    }

    private TransactionBody bodyForClassic(@NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), BurnTranslator.BURN_TOKEN_V1.selector())) {
            return decoder.decodeBurn(attempt);
        } else {
            return decoder.decodeBurnV2(attempt);
        }
    }

    public static long fungibleBurnGasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.BURN_FUNGIBLE, payerId);
    }

    public static long nftBurnGasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.BURN_NFT, payerId);
    }
}
