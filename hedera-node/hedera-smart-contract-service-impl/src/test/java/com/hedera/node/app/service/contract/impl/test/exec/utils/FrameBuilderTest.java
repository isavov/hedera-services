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

package com.hedera.node.app.service.contract.impl.test.exec.utils;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.accessTrackerFor;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.configOf;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.tinybarValuesFor;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.*;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameBuilder;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FrameBuilderTest {
    @Mock
    private HederaEvmAccount account;

    @Mock
    private BlockValues blockValues;

    @Mock
    private TinybarValues tinybarValues;

    @Mock
    private SystemContractGasCalculator systemContractGasCalculator;

    @Mock
    private HederaEvmBlocks blocks;

    @Mock
    private HederaWorldUpdater worldUpdater;

    @Mock
    private HederaWorldUpdater stackedUpdater;

    private final FrameBuilder subject = new FrameBuilder();

    @Test
    void constructsExpectedFrameForCallToExtantContractIncludingAccessTrackerWithSidecarEnabled() {
        final var transaction = wellKnownHapiCall();
        given(worldUpdater.getHederaAccount(NON_SYSTEM_LONG_ZERO_ADDRESS)).willReturn(account);
        given(account.getEvmCode()).willReturn(CONTRACT_CODE);
        given(worldUpdater.updater()).willReturn(stackedUpdater);
        given(blocks.blockValuesOf(GAS_LIMIT)).willReturn(blockValues);
        given(blocks.blockHashOf(SOME_BLOCK_NO)).willReturn(Hash.EMPTY);
        final var config = HederaTestConfigBuilder.create()
                .withValue("ledger.fundingAccount", DEFAULT_COINBASE)
                .getOrCreateConfig();

        final var frame = subject.buildInitialFrameWith(
                transaction,
                worldUpdater,
                wellKnownContextWith(blocks, tinybarValues, systemContractGasCalculator),
                config,
                EIP_1014_ADDRESS,
                NON_SYSTEM_LONG_ZERO_ADDRESS,
                INTRINSIC_GAS);

        assertEquals(1024, frame.getMaxStackSize());
        assertSame(stackedUpdater, frame.getWorldUpdater());
        assertEquals(transaction.gasAvailable(INTRINSIC_GAS), frame.getRemainingGas());
        assertSame(EIP_1014_ADDRESS, frame.getOriginatorAddress());
        assertEquals(Wei.of(NETWORK_GAS_PRICE), frame.getGasPrice());
        assertEquals(Wei.of(VALUE), frame.getValue());
        assertEquals(Wei.of(VALUE), frame.getApparentValue());
        assertSame(blockValues, frame.getBlockValues());
        assertFalse(frame.isStatic());
        assertEquals(asLongZeroAddress(DEFAULT_COINBASE), frame.getMiningBeneficiary());
        final var hashLookup = frame.getBlockHashLookup();
        assertEquals(Hash.EMPTY, hashLookup.apply(SOME_BLOCK_NO));
        assertSame(config, configOf(frame));
        assertDoesNotThrow(frame::notifyCompletion);
        assertEquals(MessageFrame.Type.MESSAGE_CALL, frame.getType());
        assertEquals(NON_SYSTEM_LONG_ZERO_ADDRESS, frame.getRecipientAddress());
        assertEquals(NON_SYSTEM_LONG_ZERO_ADDRESS, frame.getContractAddress());
        assertEquals(transaction.evmPayload(), frame.getInputData());
        assertSame(CONTRACT_CODE, frame.getCode());
        assertNotNull(accessTrackerFor(frame));
        assertSame(tinybarValues, tinybarValuesFor(frame));
    }

    @Test
    void constructsExpectedFrameForCallToExtantContractNotIncludingAccessTrackerWithSidecarDisabled() {
        final var transaction = wellKnownHapiCall();
        given(worldUpdater.updater()).willReturn(stackedUpdater);
        given(blocks.blockValuesOf(GAS_LIMIT)).willReturn(blockValues);
        given(blocks.blockHashOf(SOME_BLOCK_NO)).willReturn(Hash.EMPTY);
        given(worldUpdater.getHederaAccount(NON_SYSTEM_LONG_ZERO_ADDRESS)).willReturn(account);
        given(account.getEvmCode()).willReturn(CONTRACT_CODE);
        final var config = HederaTestConfigBuilder.create()
                .withValue("ledger.fundingAccount", DEFAULT_COINBASE)
                .withValue("contracts.sidecars", "CONTRACT_BYTECODE,CONTRACT_ACTION")
                .getOrCreateConfig();

        final var frame = subject.buildInitialFrameWith(
                transaction,
                worldUpdater,
                wellKnownContextWith(blocks, tinybarValues, systemContractGasCalculator),
                config,
                EIP_1014_ADDRESS,
                NON_SYSTEM_LONG_ZERO_ADDRESS,
                INTRINSIC_GAS);

        assertEquals(1024, frame.getMaxStackSize());
        assertSame(stackedUpdater, frame.getWorldUpdater());
        assertEquals(transaction.gasAvailable(INTRINSIC_GAS), frame.getRemainingGas());
        assertSame(EIP_1014_ADDRESS, frame.getOriginatorAddress());
        assertEquals(Wei.of(NETWORK_GAS_PRICE), frame.getGasPrice());
        assertEquals(Wei.of(VALUE), frame.getValue());
        assertEquals(Wei.of(VALUE), frame.getApparentValue());
        assertSame(blockValues, frame.getBlockValues());
        assertFalse(frame.isStatic());
        assertEquals(asLongZeroAddress(DEFAULT_COINBASE), frame.getMiningBeneficiary());
        final var hashLookup = frame.getBlockHashLookup();
        assertEquals(Hash.EMPTY, hashLookup.apply(SOME_BLOCK_NO));
        assertSame(config, configOf(frame));
        assertDoesNotThrow(frame::notifyCompletion);
        assertEquals(MessageFrame.Type.MESSAGE_CALL, frame.getType());
        assertEquals(NON_SYSTEM_LONG_ZERO_ADDRESS, frame.getRecipientAddress());
        assertEquals(NON_SYSTEM_LONG_ZERO_ADDRESS, frame.getContractAddress());
        assertEquals(transaction.evmPayload(), frame.getInputData());
        assertSame(CONTRACT_CODE, frame.getCode());
        assertNull(accessTrackerFor(frame));
        assertSame(tinybarValues, tinybarValuesFor(frame));
    }

    @Test
    void constructsExpectedFrameForCallToMissingContract() {
        final var transaction = wellKnownRelayedHapiCall(VALUE);
        given(worldUpdater.updater()).willReturn(stackedUpdater);
        given(blocks.blockValuesOf(GAS_LIMIT)).willReturn(blockValues);
        given(blocks.blockHashOf(SOME_BLOCK_NO)).willReturn(Hash.EMPTY);
        final var config = HederaTestConfigBuilder.create()
                .withValue("ledger.fundingAccount", DEFAULT_COINBASE)
                .getOrCreateConfig();

        final var frame = subject.buildInitialFrameWith(
                transaction,
                worldUpdater,
                wellKnownContextWith(blocks, tinybarValues, systemContractGasCalculator),
                config,
                EIP_1014_ADDRESS,
                NON_SYSTEM_LONG_ZERO_ADDRESS,
                INTRINSIC_GAS);

        assertEquals(1024, frame.getMaxStackSize());
        assertSame(stackedUpdater, frame.getWorldUpdater());
        assertEquals(transaction.gasAvailable(INTRINSIC_GAS), frame.getRemainingGas());
        assertSame(EIP_1014_ADDRESS, frame.getOriginatorAddress());
        assertEquals(Wei.of(NETWORK_GAS_PRICE), frame.getGasPrice());
        assertEquals(Wei.of(VALUE), frame.getValue());
        assertEquals(Wei.of(VALUE), frame.getApparentValue());
        assertSame(blockValues, frame.getBlockValues());
        assertFalse(frame.isStatic());
        assertEquals(asLongZeroAddress(DEFAULT_COINBASE), frame.getMiningBeneficiary());
        final var hashLookup = frame.getBlockHashLookup();
        assertEquals(Hash.EMPTY, hashLookup.apply(SOME_BLOCK_NO));
        assertSame(config, configOf(frame));
        assertDoesNotThrow(frame::notifyCompletion);
        assertEquals(MessageFrame.Type.MESSAGE_CALL, frame.getType());
        assertEquals(NON_SYSTEM_LONG_ZERO_ADDRESS, frame.getRecipientAddress());
        assertEquals(NON_SYSTEM_LONG_ZERO_ADDRESS, frame.getContractAddress());
        assertEquals(transaction.evmPayload(), frame.getInputData());
        assertSame(CodeV0.EMPTY_CODE, frame.getCode());
        assertSame(tinybarValues, tinybarValuesFor(frame));
    }

    @Test
    void constructsExpectedFrameForCreate() {
        final var transaction = wellKnownHapiCreate();
        given(worldUpdater.updater()).willReturn(stackedUpdater);
        given(blocks.blockValuesOf(GAS_LIMIT)).willReturn(blockValues);
        given(blocks.blockHashOf(SOME_BLOCK_NO)).willReturn(Hash.EMPTY);
        final var config = HederaTestConfigBuilder.create()
                .withValue("ledger.fundingAccount", DEFAULT_COINBASE)
                .getOrCreateConfig();
        final var expectedCode = CodeFactory.createCode(transaction.evmPayload(), 0, false);

        final var frame = subject.buildInitialFrameWith(
                transaction,
                worldUpdater,
                wellKnownContextWith(blocks, tinybarValues, systemContractGasCalculator),
                config,
                EIP_1014_ADDRESS,
                NON_SYSTEM_LONG_ZERO_ADDRESS,
                INTRINSIC_GAS);

        assertEquals(1024, frame.getMaxStackSize());
        assertSame(stackedUpdater, frame.getWorldUpdater());
        assertEquals(transaction.gasAvailable(INTRINSIC_GAS), frame.getRemainingGas());
        assertSame(EIP_1014_ADDRESS, frame.getOriginatorAddress());
        assertEquals(Wei.of(NETWORK_GAS_PRICE), frame.getGasPrice());
        assertEquals(Wei.of(VALUE), frame.getValue());
        assertEquals(Wei.of(VALUE), frame.getApparentValue());
        assertSame(blockValues, frame.getBlockValues());
        assertFalse(frame.isStatic());
        assertEquals(asLongZeroAddress(DEFAULT_COINBASE), frame.getMiningBeneficiary());
        final var hashLookup = frame.getBlockHashLookup();
        assertEquals(Hash.EMPTY, hashLookup.apply(SOME_BLOCK_NO));
        assertSame(config, configOf(frame));
        assertDoesNotThrow(frame::notifyCompletion);
        assertEquals(MessageFrame.Type.CONTRACT_CREATION, frame.getType());
        assertEquals(NON_SYSTEM_LONG_ZERO_ADDRESS, frame.getRecipientAddress());
        assertEquals(NON_SYSTEM_LONG_ZERO_ADDRESS, frame.getContractAddress());
        assertEquals(Bytes.EMPTY, frame.getInputData());
        assertEquals(expectedCode, frame.getCode());
        assertSame(tinybarValues, tinybarValuesFor(frame));
    }
}
