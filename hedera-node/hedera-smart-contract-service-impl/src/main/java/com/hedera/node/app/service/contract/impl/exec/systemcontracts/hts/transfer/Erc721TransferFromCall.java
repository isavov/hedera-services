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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersCall.transferGasRequirement;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.TransferEventLoggingUtils.logSuccessfulNftTransfer;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Implements the ERC-721 {@code transferFrom()} call of the HTS contract.
 */
public class Erc721TransferFromCall extends AbstractHtsCall {
    private final long serialNo;
    private final Address from;
    private final Address to;
    private final TokenID tokenId;
    private final VerificationStrategy verificationStrategy;
    private final AccountID senderId;
    private final AddressIdConverter addressIdConverter;

    // too many parameters
    @SuppressWarnings("java:S107")
    public Erc721TransferFromCall(
            final long serialNo,
            @NonNull final Address from,
            @NonNull final Address to,
            @NonNull final TokenID tokenId,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final AccountID senderId,
            @NonNull final AddressIdConverter addressIdConverter) {
        super(gasCalculator, enhancement, false);
        this.from = requireNonNull(from);
        this.to = requireNonNull(to);
        this.tokenId = tokenId;
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.senderId = requireNonNull(senderId);
        this.addressIdConverter = requireNonNull(addressIdConverter);
        this.serialNo = serialNo;
    }

    @NonNull
    @Override
    public PricedResult execute(final MessageFrame frame) {
        // https://eips.ethereum.org/EIPS/eip-721
        if (tokenId == null) {
            return reversionWith(INVALID_TOKEN_ID, gasCalculator.canonicalGasRequirement(DispatchType.TRANSFER_NFT));
        }
        final var syntheticTransfer = syntheticTransfer(senderId);
        final var gasRequirement = transferGasRequirement(syntheticTransfer, gasCalculator, enhancement, senderId);
        final var recordBuilder = systemContractOperations()
                .dispatch(syntheticTransfer, verificationStrategy, senderId, ContractCallRecordBuilder.class);
        final var status = recordBuilder.status();
        if (status != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(recordBuilder, gasRequirement), status, false);
        } else {
            final var nftTransfer = syntheticTransfer
                    .cryptoTransferOrThrow()
                    .tokenTransfersOrThrow()
                    .get(0)
                    .nftTransfersOrThrow()
                    .get(0);
            logSuccessfulNftTransfer(tokenId, nftTransfer, readableAccountStore(), frame);
            return gasOnly(
                    successResult(
                            Erc721TransferFromTranslator.ERC_721_TRANSFER_FROM
                                    .getOutputs()
                                    .encodeElements(),
                            gasRequirement,
                            recordBuilder),
                    status,
                    false);
        }
    }

    private TransactionBody syntheticTransfer(@NonNull final AccountID spenderId) {
        final var ownerId = addressIdConverter.convert(from);
        final var receiverId = addressIdConverter.convertCredit(to);
        return TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .tokenTransfers(TokenTransferList.newBuilder()
                                .token(tokenId)
                                .nftTransfers(NftTransfer.newBuilder()
                                        .serialNumber(serialNo)
                                        .senderAccountID(ownerId)
                                        .receiverAccountID(receiverId)
                                        .isApproval(!spenderId.equals(ownerId))
                                        .build())
                                .build()))
                .build();
    }
}
