/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.accountAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.nftTransfer;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferList;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferLists;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.esaulpaugh.headlong.abi.Tuple;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.NonFungibleTransfers;
import com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("java:S1192") // "string literal should not be duplicated" - this rule makes test suites worse
public class CryptoTransferHTSV1SecurityModelSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(CryptoTransferHTSV1SecurityModelSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    public static final long TOTAL_SUPPLY = 1_000;
    private static final String FUNGIBLE_TOKEN = "TokenA";
    private static final String NFT_TOKEN = "Token_NFT";

    private static final String RECEIVER = "receiver";
    private static final String SENDER = "sender";
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE =
            KeyShape.threshOf(1, KeyShape.SIMPLE, DELEGATE_CONTRACT);

    public static final String DELEGATE_KEY = "contractKey";
    private static final String CONTRACT = "CryptoTransfer";
    private static final String MULTI_KEY = "purpose";
    private static final String HTS_TRANSFER_FROM_CONTRACT = "HtsTransferFrom";
    private static final String OWNER = "Owner";
    private static final String HTS_TRANSFER_FROM_NFT = "htsTransferFromNFT";
    public static final String TRANSFER_MULTIPLE_TOKENS = "transferMultipleTokens";
    private static final ByteString META1 = ByteStringUtils.wrapUnsafely("meta1".getBytes());
    private static final ByteString META2 = ByteStringUtils.wrapUnsafely("meta2".getBytes());
    private static final ByteString META3 = ByteStringUtils.wrapUnsafely("meta3".getBytes());
    private static final ByteString META4 = ByteStringUtils.wrapUnsafely("meta4".getBytes());
    private static final ByteString META5 = ByteStringUtils.wrapUnsafely("meta5".getBytes());
    private static final ByteString META6 = ByteStringUtils.wrapUnsafely("meta6".getBytes());
    private static final ByteString META7 = ByteStringUtils.wrapUnsafely("meta7".getBytes());
    private static final ByteString META8 = ByteStringUtils.wrapUnsafely("meta8".getBytes());
    private static final String NFT_TOKEN_WITH_FIXED_HBAR_FEE = "nftTokenWithFixedHbarFee";
    private static final String NFT_TOKEN_WITH_FIXED_TOKEN_FEE = "nftTokenWithFixedTokenFee";
    private static final String NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK =
            "nftTokenWithRoyaltyFeeWithHbarFallback";
    private static final String NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK =
            "nftTokenWithRoyaltyFeeWithTokenFallback";
    private static final String FUNGIBLE_TOKEN_FEE = "fungibleTokenFee";
    private static final String RECEIVER_SIGNATURE = "receiverSignature";
    private static final String APPROVE_TXN = "approveTxn";
    private static final String FIRST_MEMO = "firstMemo";
    private static final String SECOND_MEMO = "secondMemo";
    private static final String CRYPTO_TRANSFER_TXN = "cryptoTransferTxn";

    public static void main(final String... args) {
        new CryptoTransferHTSV1SecurityModelSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                nonNestedCryptoTransferForFungibleToken(),
                activeContractInFrameIsVerifiedWithoutNeedForSignature(),
                cryptoTransferNFTsWithCustomFeesMixedScenario(),
                hapiTransferFromForNFTWithCustomFeesWithApproveForAll(),
                hapiTransferFromForNFTWithCustomFeesWithBothApproveForAllAndAssignedSpender());
    }

    final HapiSpec nonNestedCryptoTransferForFungibleToken() {
        final var cryptoTransferTxn = CRYPTO_TRANSFER_TXN;

        return propertyPreservingHapiSpec("nonNestedCryptoTransferForFungibleToken")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(2 * ONE_HUNDRED_HBARS).receiverSigRequired(true),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT))
                .when(
                        withOpContext((spec, opLog) -> {
                            final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
                            final var sender = spec.registry().getAccountID(SENDER);
                            final var receiver = spec.registry().getAccountID(RECEIVER);
                            final var amountToBeSent = 50L;

                            allRunFor(
                                    spec,
                                    newKeyNamed(DELEGATE_KEY)
                                            .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, CONTRACT))),
                                    cryptoUpdate(SENDER).key(DELEGATE_KEY),
                                    cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
                                    contractCall(CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                                tokenTransferList()
                                                        .forToken(token)
                                                        .withAccountAmounts(
                                                                accountAmount(sender, -amountToBeSent),
                                                                accountAmount(receiver, amountToBeSent))
                                                        .build()
                                            })
                                            .payingWith(GENESIS)
                                            .via(cryptoTransferTxn)
                                            .gas(GAS_TO_OFFER),
                                    contractCall(CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                                tokenTransferList()
                                                        .forToken(token)
                                                        .withAccountAmounts(
                                                                accountAmount(sender, -0L), accountAmount(receiver, 0L))
                                                        .build()
                                            })
                                            .payingWith(GENESIS)
                                            .via("cryptoTransferZero")
                                            .gas(GAS_TO_OFFER));
                        }),
                        getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged(),
                        getTxnRecord("cryptoTransferZero").andAllChildRecords().logged())
                .then(
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
                        getAccountBalance(RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 50),
                        getAccountBalance(SENDER).hasTokenBalance(FUNGIBLE_TOKEN, 150),
                        getTokenInfo(FUNGIBLE_TOKEN).logged(),
                        childRecordsCheck(
                                cryptoTransferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS))
                                                .gasUsed(14085L))
                                        .tokenTransfers(SomeFungibleTransfers.changingFungibleBalances()
                                                .including(FUNGIBLE_TOKEN, SENDER, -50)
                                                .including(FUNGIBLE_TOKEN, RECEIVER, 50))));
    }

    final HapiSpec activeContractInFrameIsVerifiedWithoutNeedForSignature() {
        final var revertedFungibleTransferTxn = "revertedFungibleTransferTxn";
        final var successfulFungibleTransferTxn = "successfulFungibleTransferTxn";
        final var revertedNftTransferTxn = "revertedNftTransferTxn";
        final var successfulNftTransferTxn = "successfulNftTransferTxn";
        final var senderStartBalance = 200L;
        final var receiverStartBalance = 0L;
        final var toSendEachTuple = 50L;
        final var multiKey = MULTI_KEY;
        final var senderKey = "senderKey";
        final var contractKey = "contractAdminKey";

        return propertyPreservingHapiSpec("activeContractInFrameIsVerifiedWithoutNeedForSignature")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(multiKey),
                        newKeyNamed(senderKey),
                        newKeyNamed(contractKey),
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS).key(senderKey),
                        cryptoCreate(RECEIVER).balance(2 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT)
                                .payingWith(GENESIS)
                                .adminKey(contractKey)
                                .gas(GAS_TO_OFFER),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .adminKey(multiKey)
                                .supplyKey(multiKey)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY),
                        mintToken(NFT_TOKEN, List.of(metadata(FIRST_MEMO), metadata(SECOND_MEMO))),
                        tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
                        tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
                        tokenAssociate(CONTRACT, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
                        cryptoTransfer(
                                moving(senderStartBalance, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)),
                        cryptoTransfer(movingUnique(NFT_TOKEN, 1L).between(TOKEN_TREASURY, SENDER)),
                        cryptoTransfer(
                                moving(senderStartBalance, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, CONTRACT),
                                movingUnique(NFT_TOKEN, 2L).between(TOKEN_TREASURY, CONTRACT)))
                .when(withOpContext((spec, opLog) -> {
                    final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
                    final var nftToken = spec.registry().getTokenID(NFT_TOKEN);
                    final var sender = spec.registry().getAccountID(SENDER);
                    final var receiver = spec.registry().getAccountID(RECEIVER);
                    final var contractId = spec.registry().getAccountID(CONTRACT);
                    allRunFor(
                            spec,
                            contractCall(CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                        tokenTransferList()
                                                .forToken(token)
                                                .withAccountAmounts(
                                                        accountAmount(contractId, -toSendEachTuple),
                                                        accountAmount(receiver, toSendEachTuple))
                                                .build(),
                                        tokenTransferList()
                                                .forToken(token)
                                                .withAccountAmounts(
                                                        accountAmount(sender, -toSendEachTuple),
                                                        accountAmount(receiver, toSendEachTuple))
                                                .build()
                                    })
                                    .payingWith(GENESIS)
                                    .via(revertedFungibleTransferTxn)
                                    .gas(GAS_TO_OFFER)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                            contractCall(CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                        tokenTransferList()
                                                .forToken(token)
                                                .withAccountAmounts(
                                                        accountAmount(contractId, -toSendEachTuple),
                                                        accountAmount(receiver, toSendEachTuple))
                                                .build(),
                                        tokenTransferList()
                                                .forToken(token)
                                                .withAccountAmounts(
                                                        accountAmount(sender, -toSendEachTuple),
                                                        accountAmount(receiver, toSendEachTuple))
                                                .build()
                                    })
                                    .payingWith(GENESIS)
                                    .alsoSigningWithFullPrefix(senderKey)
                                    .via(successfulFungibleTransferTxn)
                                    .gas(GAS_TO_OFFER)
                                    .hasKnownStatus(SUCCESS),
                            contractCall(CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                        tokenTransferList()
                                                .forToken(nftToken)
                                                .withNftTransfers(nftTransfer(contractId, receiver, 2L))
                                                .build(),
                                        tokenTransferList()
                                                .forToken(nftToken)
                                                .withNftTransfers(nftTransfer(sender, receiver, 1L))
                                                .build()
                                    })
                                    .payingWith(GENESIS)
                                    .via(revertedNftTransferTxn)
                                    .gas(GAS_TO_OFFER)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                            contractCall(CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                        tokenTransferList()
                                                .forToken(nftToken)
                                                .withNftTransfers(nftTransfer(contractId, receiver, 2L))
                                                .build(),
                                        tokenTransferList()
                                                .forToken(nftToken)
                                                .withNftTransfers(nftTransfer(sender, receiver, 1L))
                                                .build()
                                    })
                                    .payingWith(GENESIS)
                                    .via(successfulNftTransferTxn)
                                    .alsoSigningWithFullPrefix(senderKey)
                                    .gas(GAS_TO_OFFER)
                                    .hasKnownStatus(SUCCESS));
                }))
                .then(
                        getAccountBalance(RECEIVER)
                                .hasTokenBalance(FUNGIBLE_TOKEN, receiverStartBalance + 2 * toSendEachTuple)
                                .hasTokenBalance(NFT_TOKEN, 2L),
                        getAccountBalance(SENDER)
                                .hasTokenBalance(FUNGIBLE_TOKEN, senderStartBalance - toSendEachTuple)
                                .hasTokenBalance(NFT_TOKEN, 0L),
                        getAccountBalance(CONTRACT)
                                .hasTokenBalance(FUNGIBLE_TOKEN, senderStartBalance - toSendEachTuple)
                                .hasTokenBalance(NFT_TOKEN, 0L),
                        childRecordsCheck(
                                revertedFungibleTransferTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))),
                        childRecordsCheck(
                                successfulFungibleTransferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))
                                        .tokenTransfers(SomeFungibleTransfers.changingFungibleBalances()
                                                .including(FUNGIBLE_TOKEN, SENDER, -toSendEachTuple)
                                                .including(FUNGIBLE_TOKEN, CONTRACT, -toSendEachTuple)
                                                .including(FUNGIBLE_TOKEN, RECEIVER, 2 * toSendEachTuple))),
                        childRecordsCheck(
                                revertedNftTransferTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))),
                        childRecordsCheck(
                                successfulNftTransferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))
                                        .tokenTransfers(NonFungibleTransfers.changingNFTBalances()
                                                .including(NFT_TOKEN, SENDER, RECEIVER, 1L)
                                                .including(NFT_TOKEN, CONTRACT, RECEIVER, 2L))));
    }

    final HapiSpec cryptoTransferNFTsWithCustomFeesMixedScenario() {
        final var SPENDER_SIGNATURE = "spenderSignature";
        return propertyPreservingHapiSpec("cryptoTransferNFTsWithCustomFeesMixedScenario")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(RECEIVER_SIGNATURE),
                        newKeyNamed(SPENDER_SIGNATURE),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNER)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(5)
                                .key(MULTI_KEY),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS).key(RECEIVER_SIGNATURE),
                        tokenCreate(NFT_TOKEN_WITH_FIXED_HBAR_FEE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(fixedHbarFee(1, OWNER)),
                        tokenCreate(FUNGIBLE_TOKEN_FEE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1000L),
                        tokenAssociate(CONTRACT, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(RECEIVER, FUNGIBLE_TOKEN_FEE),
                        tokenCreate(NFT_TOKEN_WITH_FIXED_TOKEN_FEE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(fixedHtsFee(1, FUNGIBLE_TOKEN_FEE, OWNER)),
                        tokenCreate(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(
                                        royaltyFeeWithFallback(1, 2, fixedHbarFeeInheritingRoyaltyCollector(1), OWNER)),
                        tokenCreate(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(1, FUNGIBLE_TOKEN_FEE), OWNER)),
                        tokenAssociate(
                                CONTRACT,
                                List.of(
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)),
                        tokenAssociate(
                                RECEIVER,
                                List.of(
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)),
                        mintToken(NFT_TOKEN_WITH_FIXED_HBAR_FEE, List.of(META1, META2)),
                        mintToken(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, List.of(META3, META4)),
                        mintToken(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, List.of(META5, META6)),
                        mintToken(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, List.of(META7, META8)),
                        cryptoTransfer(
                                movingUnique(NFT_TOKEN_WITH_FIXED_HBAR_FEE, 1L).between(OWNER, CONTRACT)),
                        cryptoTransfer(
                                movingUnique(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, 1L).between(OWNER, CONTRACT)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, 1L)
                                .between(OWNER, CONTRACT)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, 1L)
                                .between(OWNER, CONTRACT)),
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, CONTRACT)),
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, RECEIVER)),
                        cryptoTransfer(TokenMovement.movingHbar(100L).between(OWNER, CONTRACT)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        CONTRACT,
                                        TRANSFER_MULTIPLE_TOKENS,
                                        tokenTransferLists()
                                                .withTokenTransferList(
                                                        tokenTransferList()
                                                                .forToken(spec.registry()
                                                                        .getTokenID(NFT_TOKEN_WITH_FIXED_HBAR_FEE))
                                                                .withNftTransfers(nftTransfer(
                                                                        spec.registry()
                                                                                .getAccountID(CONTRACT),
                                                                        spec.registry()
                                                                                .getAccountID(RECEIVER),
                                                                        1L))
                                                                .build(),
                                                        tokenTransferList()
                                                                .forToken(spec.registry()
                                                                        .getTokenID(NFT_TOKEN_WITH_FIXED_TOKEN_FEE))
                                                                .withNftTransfers(nftTransfer(
                                                                        spec.registry()
                                                                                .getAccountID(CONTRACT),
                                                                        spec.registry()
                                                                                .getAccountID(RECEIVER),
                                                                        1L))
                                                                .build(),
                                                        tokenTransferList()
                                                                .forToken(
                                                                        spec.registry()
                                                                                .getTokenID(
                                                                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK))
                                                                .withNftTransfers(nftTransfer(
                                                                        spec.registry()
                                                                                .getAccountID(CONTRACT),
                                                                        spec.registry()
                                                                                .getAccountID(RECEIVER),
                                                                        1L))
                                                                .build(),
                                                        tokenTransferList()
                                                                .forToken(
                                                                        spec.registry()
                                                                                .getTokenID(
                                                                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK))
                                                                .withNftTransfers(nftTransfer(
                                                                        spec.registry()
                                                                                .getAccountID(CONTRACT),
                                                                        spec.registry()
                                                                                .getAccountID(RECEIVER),
                                                                        1L))
                                                                .build())
                                                .build())
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(RECEIVER_SIGNATURE)
                                .gas(1_000_000L))))
                .then();
    }

    final HapiSpec hapiTransferFromForNFTWithCustomFeesWithApproveForAll() {
        return propertyPreservingHapiSpec("hapiTransferFromForNFTWithCustomFeesWithApproveForAll")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(RECEIVER_SIGNATURE),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNER)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(5)
                                .key(MULTI_KEY),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS).key(RECEIVER_SIGNATURE),
                        tokenCreate(NFT_TOKEN_WITH_FIXED_HBAR_FEE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(fixedHbarFee(1, OWNER)),
                        tokenCreate(FUNGIBLE_TOKEN_FEE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1000L),
                        tokenAssociate(SENDER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(RECEIVER, FUNGIBLE_TOKEN_FEE),
                        tokenCreate(NFT_TOKEN_WITH_FIXED_TOKEN_FEE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(fixedHtsFee(1, FUNGIBLE_TOKEN_FEE, OWNER)),
                        tokenCreate(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(
                                        royaltyFeeWithFallback(1, 2, fixedHbarFeeInheritingRoyaltyCollector(1), OWNER)),
                        tokenCreate(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(1, FUNGIBLE_TOKEN_FEE), OWNER)),
                        tokenAssociate(
                                SENDER,
                                List.of(
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)),
                        tokenAssociate(
                                RECEIVER,
                                List.of(
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)),
                        mintToken(NFT_TOKEN_WITH_FIXED_HBAR_FEE, List.of(META1, META2)),
                        mintToken(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, List.of(META3, META4)),
                        mintToken(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, List.of(META5, META6)),
                        mintToken(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, List.of(META7, META8)),
                        cryptoTransfer(
                                movingUnique(NFT_TOKEN_WITH_FIXED_HBAR_FEE, 1L).between(OWNER, SENDER)),
                        cryptoTransfer(
                                movingUnique(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, 1L).between(OWNER, SENDER)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, 1L)
                                .between(OWNER, SENDER)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, 1L)
                                .between(OWNER, SENDER)),
                        uploadInitCode(HTS_TRANSFER_FROM_CONTRACT),
                        contractCreate(HTS_TRANSFER_FROM_CONTRACT),
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, SENDER)),
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, RECEIVER)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of())
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of())
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of())
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of())
                                .via(APPROVE_TXN)
                                .signedBy(DEFAULT_PAYER, SENDER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NFT_TOKEN_WITH_FIXED_HBAR_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NFT_TOKEN_WITH_FIXED_TOKEN_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getTokenID(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(RECEIVER_SIGNATURE),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getTokenID(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(RECEIVER_SIGNATURE))))
                .then();
    }

    final HapiSpec hapiTransferFromForNFTWithCustomFeesWithBothApproveForAllAndAssignedSpender() {
        return propertyPreservingHapiSpec("hapiTransferFromForNFTWithCustomFeesWithBothApproveForAllAndAssignedSpender")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(RECEIVER_SIGNATURE),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNER)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(5)
                                .key(MULTI_KEY),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS).key(RECEIVER_SIGNATURE),
                        tokenCreate(NFT_TOKEN_WITH_FIXED_HBAR_FEE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(fixedHbarFee(1, OWNER)),
                        tokenCreate(FUNGIBLE_TOKEN_FEE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1000L),
                        tokenAssociate(SENDER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(RECEIVER, FUNGIBLE_TOKEN_FEE),
                        tokenCreate(NFT_TOKEN_WITH_FIXED_TOKEN_FEE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(fixedHtsFee(1, FUNGIBLE_TOKEN_FEE, OWNER)),
                        tokenCreate(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(
                                        royaltyFeeWithFallback(1, 2, fixedHbarFeeInheritingRoyaltyCollector(1), OWNER)),
                        tokenCreate(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(1, FUNGIBLE_TOKEN_FEE), OWNER)),
                        tokenAssociate(
                                SENDER,
                                List.of(
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)),
                        tokenAssociate(
                                RECEIVER,
                                List.of(
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)),
                        mintToken(NFT_TOKEN_WITH_FIXED_HBAR_FEE, List.of(META1, META2)),
                        mintToken(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, List.of(META3, META4)),
                        mintToken(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, List.of(META5, META6)),
                        mintToken(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, List.of(META7, META8)),
                        cryptoTransfer(
                                movingUnique(NFT_TOKEN_WITH_FIXED_HBAR_FEE, 1L).between(OWNER, SENDER)),
                        cryptoTransfer(
                                movingUnique(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, 1L).between(OWNER, SENDER)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, 1L)
                                .between(OWNER, SENDER)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, 1L)
                                .between(OWNER, SENDER)),
                        uploadInitCode(HTS_TRANSFER_FROM_CONTRACT),
                        contractCreate(HTS_TRANSFER_FROM_CONTRACT),
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, SENDER)),
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, RECEIVER)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of(1L))
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of(1L))
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of(1L))
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of(1L))
                                .via(APPROVE_TXN)
                                .signedBy(DEFAULT_PAYER, SENDER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NFT_TOKEN_WITH_FIXED_HBAR_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NFT_TOKEN_WITH_FIXED_TOKEN_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getTokenID(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(RECEIVER_SIGNATURE),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getTokenID(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(RECEIVER_SIGNATURE))))
                .then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
