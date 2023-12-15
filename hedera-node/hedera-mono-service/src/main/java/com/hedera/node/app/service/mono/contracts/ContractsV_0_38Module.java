/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.contracts;

import static org.hyperledger.besu.evm.MainnetEVMs.registerShanghaiOperations;
import static org.hyperledger.besu.evm.operation.SStoreOperation.FRONTIER_MINIMUM;

import com.hedera.node.app.service.evm.contracts.operations.HederaBalanceOperationV038;
import com.hedera.node.app.service.evm.contracts.operations.HederaDelegateCallOperationV038;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmChainIdOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmCreate2Operation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmCreateOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeCopyOperationV038;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeHashOperationV038;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeSizeOperationV038;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.ContractsModule.V_0_38;
import com.hedera.node.app.service.mono.contracts.operation.HederaCallCodeOperationV038;
import com.hedera.node.app.service.mono.contracts.operation.HederaCallOperationV038;
import com.hedera.node.app.service.mono.contracts.operation.HederaLogOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaPrngSeedOperator;
import com.hedera.node.app.service.mono.contracts.operation.HederaSLoadOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaSStoreOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaSelfDestructOperationV038;
import com.hedera.node.app.service.mono.contracts.operation.HederaStaticCallOperationV038;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

@Module
public interface ContractsV_0_38Module {

    String EVM_VERSION_0_38 = "v0.38";

    @Provides
    @Singleton
    @V_0_38
    static BiPredicate<Address, MessageFrame> provideAddressValidator(
            final Map<String, PrecompiledContract> precompiledContractMap) {
        final var precompiles = precompiledContractMap.keySet().stream()
                .map(Address::fromHexString)
                .collect(Collectors.toSet());
        return (address, frame) ->
                precompiles.contains(address) || frame.getWorldUpdater().get(address) != null;
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_38
    static Operation provideLog0Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(0, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_38
    static Operation provideLog1Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(1, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_38
    static Operation provideLog2Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(2, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_38
    static Operation provideLog3Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(3, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_38
    static Operation provideLog4Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(4, gasCalculator);
    }

    @Binds
    @Singleton
    @IntoSet
    @V_0_38
    Operation bindChainIdOperation(HederaEvmChainIdOperation chainIdOperation);

    @Binds
    @Singleton
    @IntoSet
    @V_0_38
    Operation bindCreateOperation(HederaEvmCreateOperation createOperation);

    @Binds
    @Singleton
    @IntoSet
    @V_0_38
    Operation bindCreate2Operation(HederaEvmCreate2Operation create2Operation);

    @Provides
    @Singleton
    @IntoSet
    @V_0_38
    static Operation bindCallCodeOperation(
            final EvmSigsVerifier sigsVerifier,
            final GasCalculator gasCalculator,
            @V_0_38 final BiPredicate<Address, MessageFrame> addressValidator,
            final @Named("HederaSystemAccountDetector") Predicate<Address> hederaSystemAccountDetector) {
        return new HederaCallCodeOperationV038(
                sigsVerifier, gasCalculator, addressValidator, hederaSystemAccountDetector);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_38
    static Operation bindCallOperation(
            final EvmSigsVerifier sigsVerifier,
            final GasCalculator gasCalculator,
            @V_0_38 final BiPredicate<Address, MessageFrame> addressValidator,
            final @Named("HederaSystemAccountDetector") Predicate<Address> hederaSystemAccountDetector,
            final GlobalDynamicProperties globalDynamicProperties) {
        return new HederaCallOperationV038(
                sigsVerifier, gasCalculator, addressValidator, hederaSystemAccountDetector, globalDynamicProperties);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_38
    static Operation bindDelegateCallOperation(
            GasCalculator gasCalculator,
            @V_0_38 BiPredicate<Address, MessageFrame> addressValidator,
            final @Named("HederaSystemAccountDetector") Predicate<Address> hederaSystemAccountDetector) {
        return new HederaDelegateCallOperationV038(gasCalculator, addressValidator, hederaSystemAccountDetector);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_38
    static Operation bindStaticCallOperation(
            final GasCalculator gasCalculator,
            @V_0_38 final BiPredicate<Address, MessageFrame> addressValidator,
            final @Named("HederaSystemAccountDetector") Predicate<Address> hederaSystemAccountDetector) {
        return new HederaStaticCallOperationV038(gasCalculator, addressValidator, hederaSystemAccountDetector);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_38
    static Operation bindBalanceOperation(
            GasCalculator gasCalculator,
            @V_0_38 BiPredicate<Address, MessageFrame> addressValidator,
            final @Named("HederaSystemAccountDetector") Predicate<Address> hederaSystemAccountDetector) {
        return new HederaBalanceOperationV038(gasCalculator, addressValidator, hederaSystemAccountDetector);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_38
    static Operation bindExtCodeCopyOperation(
            GasCalculator gasCalculator,
            @V_0_38 BiPredicate<Address, MessageFrame> addressValidator,
            final @Named("StrictHederaSystemAccountDetector") Predicate<Address> strictHederaSystemAccountDetector) {
        return new HederaExtCodeCopyOperationV038(gasCalculator, addressValidator, strictHederaSystemAccountDetector);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_38
    static Operation bindExtCodeHashOperation(
            GasCalculator gasCalculator,
            @V_0_38 BiPredicate<Address, MessageFrame> addressValidator,
            final @Named("StrictHederaSystemAccountDetector") Predicate<Address> strictHederaSystemAccountDetector) {
        return new HederaExtCodeHashOperationV038(gasCalculator, addressValidator, strictHederaSystemAccountDetector);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_38
    static Operation bindExtCodeSizeOperation(
            GasCalculator gasCalculator,
            @V_0_38 BiPredicate<Address, MessageFrame> addressValidator,
            final @Named("StrictHederaSystemAccountDetector") Predicate<Address> strictHederaSystemAccountDetector) {
        return new HederaExtCodeSizeOperationV038(gasCalculator, addressValidator, strictHederaSystemAccountDetector);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_38
    static Operation bindSelfDestructOperation(
            GasCalculator gasCalculator,
            final TransactionContext txnCtx,
            /* Deliberately import the V_0_30 validator, we still want self-destructs to fail if the beneficiary is invalid */
            @ContractsModule.V_0_30 BiPredicate<Address, MessageFrame> addressValidator,
            final @Named("HederaSystemAccountDetector") Predicate<Address> hederaSystemAccountDetector,
            final EvmSigsVerifier sigsVerifier) {
        return new HederaSelfDestructOperationV038(
                gasCalculator, txnCtx, addressValidator, sigsVerifier, hederaSystemAccountDetector);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_38
    static Operation provideSStoreOperation(
            final GasCalculator gasCalculator, final GlobalDynamicProperties dynamicProperties) {
        return new HederaSStoreOperation(FRONTIER_MINIMUM, gasCalculator, dynamicProperties);
    }

    @Binds
    @Singleton
    @IntoSet
    @V_0_38
    Operation bindHederaSLoadOperation(HederaSLoadOperation sLoadOperation);

    @Binds
    @Singleton
    @IntoSet
    @V_0_38
    Operation bindHederaPrngSeedOperation(HederaPrngSeedOperator prngSeedOperator);

    @Provides
    @Singleton
    @V_0_38
    static EVM provideV_0_38EVM(
            @V_0_38 Set<Operation> hederaOperations, GasCalculator gasCalculator, EvmConfiguration evmConfiguration) {
        var operationRegistry = new OperationRegistry();
        // ChainID will be overridden
        registerShanghaiOperations(operationRegistry, gasCalculator, BigInteger.ZERO);
        hederaOperations.forEach(operationRegistry::put);
        return new EVM(operationRegistry, gasCalculator, evmConfiguration, EvmSpecVersion.SHANGHAI);
    }

    @Provides
    @Singleton
    @V_0_38
    static PrecompileContractRegistry providePrecompiledContractRegistry(GasCalculator gasCalculator) {
        final var precompileContractRegistry = new PrecompileContractRegistry();
        MainnetPrecompiledContracts.populateForIstanbul(precompileContractRegistry, gasCalculator);
        return precompileContractRegistry;
    }
}
