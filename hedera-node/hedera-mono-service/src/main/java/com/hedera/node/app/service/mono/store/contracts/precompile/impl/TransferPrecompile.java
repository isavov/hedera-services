/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts.precompile.impl;

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfers.NO_ALIASES;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.NO_FUNGIBLE_TRANSFERS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.NO_NFT_EXCHANGES;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.bindFungibleTransfersFrom;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.bindHBarTransfersFrom;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.bindNftExchangesFrom;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.decodeAccountIds;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.mono.txns.span.SpanMapManager.reCalculateXferMeta;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfers;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.node.app.service.mono.ledger.BalanceChange;
import com.hedera.node.app.service.mono.state.submerkle.FcAssessedCustomFee;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.CryptoTransferWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.TokenTransferWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.TransferWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class TransferPrecompile extends AbstractWritePrecompile {
    private static final Function CRYPTO_TRANSFER_FUNCTION =
            new Function(
                    "cryptoTransfer((address,(address,int64)[],(address,address,int64)[])[])", INT);
    private static final Function CRYPTO_TRANSFER_FUNCTION_V2 =
            new Function(
                    "cryptoTransfer(((address,int64,bool)[]),(address,(address,int64,bool)[],(address,address,int64,"
                        + "bool)[])[])",
                    INT);
    private static final Bytes CRYPTO_TRANSFER_SELECTOR =
            Bytes.wrap(CRYPTO_TRANSFER_FUNCTION.selector());
    private static final Bytes CRYPTO_TRANSFER_SELECTOR_V2 =
            Bytes.wrap(CRYPTO_TRANSFER_FUNCTION_V2.selector());
    private static final ABIType<Tuple> CRYPTO_TRANSFER_DECODER =
            TypeFactory.create("((bytes32,(bytes32,int64)[],(bytes32,bytes32,int64)[])[])");
    private static final ABIType<Tuple> CRYPTO_TRANSFER_DECODER_V2 =
            TypeFactory.create(
                    "(((bytes32,int64,bool)[]),(bytes32,(bytes32,int64,bool)[],(bytes32,bytes32,int64,bool)[])[])");
    private static final Function TRANSFER_TOKENS_FUNCTION =
            new Function("transferTokens(address,address[],int64[])", INT);
    private static final Bytes TRANSFER_TOKENS_SELECTOR =
            Bytes.wrap(TRANSFER_TOKENS_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_TOKENS_DECODER =
            TypeFactory.create("(bytes32,bytes32[],int64[])");
    private static final Function TRANSFER_TOKEN_FUNCTION =
            new Function("transferToken(address,address,address,int64)", INT);
    private static final Bytes TRANSFER_TOKEN_SELECTOR =
            Bytes.wrap(TRANSFER_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_TOKEN_DECODER =
            TypeFactory.create("(bytes32,bytes32,bytes32,int64)");
    private static final Function TRANSFER_NFTS_FUNCTION =
            new Function("transferNFTs(address,address[],address[],int64[])", INT);
    private static final Bytes TRANSFER_NFTS_SELECTOR =
            Bytes.wrap(TRANSFER_NFTS_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_NFTS_DECODER =
            TypeFactory.create("(bytes32,bytes32[],bytes32[],int64[])");
    private static final Function TRANSFER_NFT_FUNCTION =
            new Function("transferNFT(address,address,address,int64)", INT);
    private static final Bytes TRANSFER_NFT_SELECTOR = Bytes.wrap(TRANSFER_NFT_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_NFT_DECODER =
            TypeFactory.create("(bytes32,bytes32,bytes32,int64)");
    private static final String TRANSFER = String.format(FAILURE_MESSAGE, "transfer");
    private final HederaStackedWorldStateUpdater updater;
    private final EvmSigsVerifier sigsVerifier;
    private final int functionId;
    private final Address senderAddress;
    private final ImpliedTransfersMarshal impliedTransfersMarshal;
    private ResponseCodeEnum impliedValidity;
    private ImpliedTransfers impliedTransfers;
    private HederaTokenStore hederaTokenStore;
    protected CryptoTransferWrapper transferOp;

    public TransferPrecompile(
            final WorldLedgers ledgers,
            final HederaStackedWorldStateUpdater updater,
            final EvmSigsVerifier sigsVerifier,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final int functionId,
            final Address senderAddress) {
        super(ledgers, sideEffects, syntheticTxnFactory, infrastructureFactory, pricingUtils);
        this.updater = updater;
        this.sigsVerifier = sigsVerifier;
        this.functionId = functionId;
        this.senderAddress = senderAddress;
        this.impliedTransfersMarshal =
                infrastructureFactory.newImpliedTransfersMarshal(ledgers.customFeeSchedules());
    }

    protected void initializeHederaTokenStore() {
        hederaTokenStore =
                infrastructureFactory.newHederaTokenStore(
                        sideEffects, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());
    }

    @Override
    public void customizeTrackingLedgers(final WorldLedgers worldLedgers) {
        worldLedgers.customizeForAutoAssociatingOp(sideEffects);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {

        transferOp =
                switch (functionId) {
                    case AbiConstants.ABI_ID_CRYPTO_TRANSFER -> decodeCryptoTransfer(
                            input, aliasResolver);
                    case AbiConstants.ABI_ID_CRYPTO_TRANSFER_V2 -> decodeCryptoTransferV2(
                            input, aliasResolver);
                    case AbiConstants.ABI_ID_TRANSFER_TOKENS -> decodeTransferTokens(
                            input, aliasResolver);
                    case AbiConstants.ABI_ID_TRANSFER_TOKEN -> decodeTransferToken(
                            input, aliasResolver);
                    case AbiConstants.ABI_ID_TRANSFER_NFTS -> decodeTransferNFTs(
                            input, aliasResolver);
                    case AbiConstants.ABI_ID_TRANSFER_NFT -> decodeTransferNFT(
                            input, aliasResolver);
                    default -> null;
                };
        Objects.requireNonNull(transferOp, "Unable to decode function input");

        transactionBody =
                syntheticTxnFactory.createCryptoTransfer(transferOp.tokenTransferWrappers());
        if (!transferOp.transferWrapper().hbarTransfers().isEmpty()) {
            transactionBody.mergeFrom(
                    syntheticTxnFactory.createCryptoTransferForHbar(transferOp.transferWrapper()));
        }

        extrapolateDetailsFromSyntheticTxn();

        initializeHederaTokenStore();
        return transactionBody;
    }

    @Override
    public void addImplicitCostsIn(final TxnAccessor accessor) {
        if (impliedTransfers != null) {
            reCalculateXferMeta(accessor, impliedTransfers);
        }
    }

    @Override
    public void run(final MessageFrame frame) {
        if (impliedValidity == null) {
            extrapolateDetailsFromSyntheticTxn();
        }
        if (impliedValidity != ResponseCodeEnum.OK) {
            throw new InvalidTransactionException(impliedValidity);
        }

        final var assessmentStatus = impliedTransfers.getMeta().code();
        validateTrue(assessmentStatus == OK, assessmentStatus);
        final var changes = impliedTransfers.getAllBalanceChanges();

        hederaTokenStore.setAccountsLedger(ledgers.accounts());

        final var transferLogic =
                infrastructureFactory.newTransferLogic(
                        hederaTokenStore,
                        sideEffects,
                        ledgers.nfts(),
                        ledgers.accounts(),
                        ledgers.tokenRels());

        for (int i = 0, n = changes.size(); i < n; i++) {
            final var change = changes.get(i);
            final var units = change.getAggregatedUnits();
            if (change.isForNft() || units < 0) {
                if (change.isApprovedAllowance() || change.isForCustomFee()) {
                    // Signing requirements are skipped for changes to be authorized via an
                    // allowance
                    continue;
                }
                final var hasSenderSig =
                        KeyActivationUtils.validateKey(
                                frame,
                                change.getAccount().asEvmAddress(),
                                sigsVerifier::hasActiveKey,
                                ledgers,
                                updater.aliases());
                validateTrue(hasSenderSig, INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE, TRANSFER);
            }
            if (!change.isForCustomFee()) {
                /* Only process receiver sig requirements for that are not custom fee payments (custom fees are never
                NFT exchanges) */
                var hasReceiverSigIfReq = true;
                if (change.isForNft()) {
                    final var counterPartyAddress =
                            asTypedEvmAddress(change.counterPartyAccountId());
                    hasReceiverSigIfReq =
                            KeyActivationUtils.validateKey(
                                    frame,
                                    counterPartyAddress,
                                    sigsVerifier::hasActiveKeyOrNoReceiverSigReq,
                                    ledgers,
                                    updater.aliases());
                } else if (units > 0) {
                    hasReceiverSigIfReq =
                            KeyActivationUtils.validateKey(
                                    frame,
                                    change.getAccount().asEvmAddress(),
                                    sigsVerifier::hasActiveKeyOrNoReceiverSigReq,
                                    ledgers,
                                    updater.aliases());
                }
                validateTrue(
                        hasReceiverSigIfReq,
                        INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE,
                        TRANSFER);
            }
        }

        transferLogic.doZeroSum(changes);
    }

    @Override
    public List<FcAssessedCustomFee> getCustomFees() {
        return impliedTransfers.getAssessedCustomFees();
    }

    protected void extrapolateDetailsFromSyntheticTxn() {
        Objects.requireNonNull(
                transferOp,
                "`body` method should be called before `extrapolateDetailsFromSyntheticTxn`");

        final var op = transactionBody.getCryptoTransfer();
        impliedValidity = impliedTransfersMarshal.validityWithCurrentProps(op);
        if (impliedValidity != ResponseCodeEnum.OK) {
            return;
        }
        final var explicitChanges = constructBalanceChanges();
        final var hbarOnly = transferOp.transferWrapper().hbarTransfers().size();
        impliedTransfers =
                impliedTransfersMarshal.assessCustomFeesAndValidate(
                        hbarOnly,
                        0,
                        0,
                        explicitChanges,
                        NO_ALIASES,
                        impliedTransfersMarshal.currentProps());
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        Objects.requireNonNull(
                transferOp, "`body` method should be called before `getMinimumFeeInTinybars`");
        long accumulatedCost = 0;
        final boolean customFees =
                impliedTransfers != null && !impliedTransfers.getAssessedCustomFees().isEmpty();
        // For fungible there are always at least two operations, so only charge half for each
        // operation
        final long ftTxCost =
                pricingUtils.getMinimumPriceInTinybars(
                                customFees
                                        ? PrecompilePricingUtils.GasCostType
                                                .TRANSFER_FUNGIBLE_CUSTOM_FEES
                                        : PrecompilePricingUtils.GasCostType.TRANSFER_FUNGIBLE,
                                consensusTime)
                        / 2;
        // NFTs are atomic, one line can do it.
        final long nonFungibleTxCost =
                pricingUtils.getMinimumPriceInTinybars(
                        customFees
                                ? PrecompilePricingUtils.GasCostType.TRANSFER_NFT_CUSTOM_FEES
                                : PrecompilePricingUtils.GasCostType.TRANSFER_NFT,
                        consensusTime);
        for (final var transfer : transferOp.tokenTransferWrappers()) {
            accumulatedCost += transfer.fungibleTransfers().size() * ftTxCost;
            accumulatedCost += transfer.nftExchanges().size() * nonFungibleTxCost;
        }

        // add the cost for transferring hbars
        // Hbar transfer is similar to fungible tokens so only charge half for each operation
        final long hbarTxCost =
                pricingUtils.getMinimumPriceInTinybars(
                                PrecompilePricingUtils.GasCostType.TRANSFER_HBAR, consensusTime)
                        / 2;
        accumulatedCost += transferOp.transferWrapper().hbarTransfers().size() * hbarTxCost;

        return accumulatedCost;
    }

    /**
     * Decodes the given bytes of the cryptoTransfer function parameters
     *
     * <p><b>Important: </b>This is an old version of this method and is superseded by
     * decodeCryptoTransferV2(). The selector for this function is derived from:
     * cryptoTransfer((address,(address,int64)[],(address,address,int64)[])[])
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return CryptoTransferWrapper codec
     */
    public static CryptoTransferWrapper decodeCryptoTransfer(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final List<SyntheticTxnFactory.HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedTuples =
                decodeFunctionCall(input, CRYPTO_TRANSFER_SELECTOR, CRYPTO_TRANSFER_DECODER);

        final List<TokenTransferWrapper> tokenTransferWrappers = new ArrayList<>();

        for (final var tuple : decodedTuples) {
            decodeTokenTransfer(aliasResolver, tokenTransferWrappers, (Tuple[]) tuple);
        }

        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    /**
     * Decodes the given bytes of the cryptoTransfer function parameters
     *
     * <p><b>Important: </b>This is the latest version and supersedes public static
     * CryptoTransferWrapper decodeCryptoTransfer(). The selector for this function is derived from:
     * cryptoTransfer(((address,int64,bool)[]),(address,(address,int64,bool)[],(address,address,int64,bool)[])[])
     * The first parameter describes hbar transfers and the second describes token transfers
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return CryptoTransferWrapper codec
     */
    public static CryptoTransferWrapper decodeCryptoTransferV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedTuples =
                decodeFunctionCall(input, CRYPTO_TRANSFER_SELECTOR_V2, CRYPTO_TRANSFER_DECODER_V2);
        List<SyntheticTxnFactory.HbarTransfer> hbarTransfers = new ArrayList<>();
        final List<TokenTransferWrapper> tokenTransferWrappers = new ArrayList<>();

        final Tuple[] hbarTransferTuples = ((Tuple) decodedTuples.get(0)).get(0);
        final var tokenTransferTuples = decodedTuples.get(1);

        hbarTransfers = decodeHbarTransfers(aliasResolver, hbarTransfers, hbarTransferTuples);

        decodeTokenTransfer(aliasResolver, tokenTransferWrappers, (Tuple[]) tokenTransferTuples);

        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static List<SyntheticTxnFactory.HbarTransfer> decodeHbarTransfers(
            final UnaryOperator<byte[]> aliasResolver,
            List<SyntheticTxnFactory.HbarTransfer> hbarTransfers,
            final Tuple[] hbarTransferTuples) {
        if (hbarTransferTuples.length > 0) {
            hbarTransfers = bindHBarTransfersFrom(hbarTransferTuples, aliasResolver);
        }
        return hbarTransfers;
    }

    public static void decodeTokenTransfer(
            final UnaryOperator<byte[]> aliasResolver,
            final List<TokenTransferWrapper> tokenTransferWrappers,
            final Tuple[] tokenTransferTuples) {
        for (final var tupleNested : tokenTransferTuples) {
            final var tokenType = convertAddressBytesToTokenID(tupleNested.get(0));

            var nftExchanges = NO_NFT_EXCHANGES;
            var fungibleTransfers = NO_FUNGIBLE_TRANSFERS;

            final var abiAdjustments = (Tuple[]) tupleNested.get(1);
            if (abiAdjustments.length > 0) {
                fungibleTransfers =
                        bindFungibleTransfersFrom(tokenType, abiAdjustments, aliasResolver);
            }
            final var abiNftExchanges = (Tuple[]) tupleNested.get(2);
            if (abiNftExchanges.length > 0) {
                nftExchanges = bindNftExchangesFrom(tokenType, abiNftExchanges, aliasResolver);
            }

            tokenTransferWrappers.add(new TokenTransferWrapper(nftExchanges, fungibleTransfers));
        }
    }

    public static CryptoTransferWrapper decodeTransferTokens(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final List<SyntheticTxnFactory.HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments =
                decodeFunctionCall(input, TRANSFER_TOKENS_SELECTOR, TRANSFER_TOKENS_DECODER);

        final var tokenType = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var accountIDs = decodeAccountIds(decodedArguments.get(1), aliasResolver);
        final var amounts = (long[]) decodedArguments.get(2);

        final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
        for (int i = 0; i < accountIDs.size(); i++) {
            final var accountID = accountIDs.get(i);
            final var amount = amounts[i];

            DecodingFacade.addSignedAdjustment(
                    fungibleTransfers, tokenType, accountID, amount, false);
        }

        final var tokenTransferWrappers =
                Collections.singletonList(
                        new TokenTransferWrapper(NO_NFT_EXCHANGES, fungibleTransfers));

        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static CryptoTransferWrapper decodeTransferToken(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final List<SyntheticTxnFactory.HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments =
                decodeFunctionCall(input, TRANSFER_TOKEN_SELECTOR, TRANSFER_TOKEN_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var sender =
                convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);
        final var receiver =
                convertLeftPaddedAddressToAccountId(decodedArguments.get(2), aliasResolver);
        final var amount = (long) decodedArguments.get(3);

        final var tokenTransferWrappers =
                Collections.singletonList(
                        new TokenTransferWrapper(
                                NO_NFT_EXCHANGES,
                                List.of(
                                        new SyntheticTxnFactory.FungibleTokenTransfer(
                                                amount, false, tokenID, sender, receiver))));
        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static CryptoTransferWrapper decodeTransferNFTs(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final List<SyntheticTxnFactory.HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments =
                decodeFunctionCall(input, TRANSFER_NFTS_SELECTOR, TRANSFER_NFTS_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var senders = decodeAccountIds(decodedArguments.get(1), aliasResolver);
        final var receivers = decodeAccountIds(decodedArguments.get(2), aliasResolver);
        final var serialNumbers = ((long[]) decodedArguments.get(3));

        final List<SyntheticTxnFactory.NftExchange> nftExchanges = new ArrayList<>();
        for (var i = 0; i < senders.size(); i++) {
            final var nftExchange =
                    new SyntheticTxnFactory.NftExchange(
                            serialNumbers[i], tokenID, senders.get(i), receivers.get(i));
            nftExchanges.add(nftExchange);
        }

        final var tokenTransferWrappers =
                Collections.singletonList(
                        new TokenTransferWrapper(nftExchanges, NO_FUNGIBLE_TRANSFERS));
        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static CryptoTransferWrapper decodeTransferNFT(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final List<SyntheticTxnFactory.HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments =
                decodeFunctionCall(input, TRANSFER_NFT_SELECTOR, TRANSFER_NFT_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var sender =
                convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);
        final var receiver =
                convertLeftPaddedAddressToAccountId(decodedArguments.get(2), aliasResolver);
        final var serialNumber = (long) decodedArguments.get(3);

        final var tokenTransferWrappers =
                Collections.singletonList(
                        new TokenTransferWrapper(
                                List.of(
                                        new SyntheticTxnFactory.NftExchange(
                                                serialNumber, tokenID, sender, receiver)),
                                NO_FUNGIBLE_TRANSFERS));
        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    private List<BalanceChange> constructBalanceChanges() {
        Objects.requireNonNull(
                transferOp, "`body` method should be called before `getMinimumFeeInTinybars`");
        final List<BalanceChange> allChanges = new ArrayList<>();
        final var accountId = EntityIdUtils.accountIdFromEvmAddress(senderAddress);
        for (final TokenTransferWrapper tokenTransferWrapper : transferOp.tokenTransferWrappers()) {
            for (final var fungibleTransfer : tokenTransferWrapper.fungibleTransfers()) {
                if (fungibleTransfer.sender() != null && fungibleTransfer.receiver() != null) {
                    allChanges.addAll(
                            List.of(
                                    BalanceChange.changingFtUnits(
                                            Id.fromGrpcToken(fungibleTransfer.getDenomination()),
                                            fungibleTransfer.getDenomination(),
                                            aaWith(
                                                    fungibleTransfer.receiver(),
                                                    fungibleTransfer.amount(),
                                                    fungibleTransfer.isApproval()),
                                            accountId),
                                    BalanceChange.changingFtUnits(
                                            Id.fromGrpcToken(fungibleTransfer.getDenomination()),
                                            fungibleTransfer.getDenomination(),
                                            aaWith(
                                                    fungibleTransfer.sender(),
                                                    -fungibleTransfer.amount(),
                                                    fungibleTransfer.isApproval()),
                                            accountId)));
                } else if (fungibleTransfer.sender() == null) {
                    allChanges.add(
                            BalanceChange.changingFtUnits(
                                    Id.fromGrpcToken(fungibleTransfer.getDenomination()),
                                    fungibleTransfer.getDenomination(),
                                    aaWith(
                                            fungibleTransfer.receiver(),
                                            fungibleTransfer.amount(),
                                            fungibleTransfer.isApproval()),
                                    accountId));
                } else {
                    allChanges.add(
                            BalanceChange.changingFtUnits(
                                    Id.fromGrpcToken(fungibleTransfer.getDenomination()),
                                    fungibleTransfer.getDenomination(),
                                    aaWith(
                                            fungibleTransfer.sender(),
                                            -fungibleTransfer.amount(),
                                            fungibleTransfer.isApproval()),
                                    accountId));
                }
            }
            for (final var nftExchange : tokenTransferWrapper.nftExchanges()) {
                allChanges.add(
                        BalanceChange.changingNftOwnership(
                                Id.fromGrpcToken(nftExchange.getTokenType()),
                                nftExchange.getTokenType(),
                                nftExchange.asGrpc(),
                                accountId));
            }
        }

        for (final var hbarTransfer : transferOp.transferWrapper().hbarTransfers()) {
            if (hbarTransfer.sender() != null) {
                allChanges.add(
                        BalanceChange.changingHbar(
                                aaWith(
                                        hbarTransfer.sender(),
                                        -hbarTransfer.amount(),
                                        hbarTransfer.isApproval()),
                                accountId));
            } else if (hbarTransfer.receiver() != null) {
                allChanges.add(
                        BalanceChange.changingHbar(
                                aaWith(
                                        hbarTransfer.receiver(),
                                        hbarTransfer.amount(),
                                        hbarTransfer.isApproval()),
                                accountId));
            }
        }
        return allChanges;
    }

    private AccountAmount aaWith(
            final AccountID account, final long amount, final boolean isApproval) {
        return AccountAmount.newBuilder()
                .setAccountID(account)
                .setAmount(amount)
                .setIsApproval(isApproval)
                .build();
    }
}
