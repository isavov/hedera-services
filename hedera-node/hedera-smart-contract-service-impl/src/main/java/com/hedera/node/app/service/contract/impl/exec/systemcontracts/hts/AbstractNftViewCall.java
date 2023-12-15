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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Implementation support for view calls that require an extant token and NFT to succeed.
 */
public abstract class AbstractNftViewCall extends AbstractRevertibleTokenViewCall {
    protected final long serialNo;

    protected AbstractNftViewCall(
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final Token token,
            final long serialNo) {
        super(gasCalculator, enhancement, token);
        this.serialNo = serialNo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected @NonNull FullResult resultOfViewingToken(@NonNull final Token token) {
        requireNonNull(token);
        final var nft = nativeOperations().getNft(token.tokenIdOrThrow().tokenNum(), serialNo);
        if (nft == null) {
            return revertResult(INVALID_NFT_ID, gasCalculator.viewGasRequirement());
        } else {
            return resultOfViewingNft(token, nft);
        }
    }

    /**
     * Returns the result of viewing the given NFT of the given token
     *
     * @param token the token
     * @param nft the NFT
     * @return the result of viewing the given NFT of the given token
     */
    @NonNull
    protected abstract FullResult resultOfViewingNft(@NonNull Token token, @NonNull Nft nft);
}
