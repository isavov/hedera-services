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

package com.hedera.node.app.service.contract.impl.exec.operations;

import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.operations.CustomizedOpcodes.SELFDESTRUCT;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.isDelegateCall;
import static java.util.Objects.requireNonNull;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.UnderflowException;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.SelfDestructOperation;

/**
 * Hedera {@link SelfDestructOperation} that checks whether there is a Hedera-specific reason to halt
 * execution before proceeding with a self-destruct that uses
 * {@link ProxyWorldUpdater#tryTransfer(Address, Address, long, boolean)}.
 * instead of direct {@link org.hyperledger.besu.evm.account.MutableAccount#setBalance(Wei)} calls to
 * ensure Hedera signing requirements are enforced.
 */
public class CustomSelfDestructOperation extends AbstractOperation {
    private static final Operation.OperationResult UNDERFLOW_RESPONSE =
            new Operation.OperationResult(0, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);

    private final AddressChecks addressChecks;

    public CustomSelfDestructOperation(
            @NonNull final GasCalculator gasCalculator, @NonNull final AddressChecks addressChecks) {
        super(SELFDESTRUCT.opcode(), "SELFDESTRUCT", 1, 0, gasCalculator);
        this.addressChecks = addressChecks;
    }

    @Override
    public OperationResult execute(@NonNull final MessageFrame frame, @NonNull final EVM evm) {
        try {
            final var beneficiaryAddress = Words.toAddress(frame.popStackItem());
            // Enforce Hedera-specific checks on the beneficiary address
            if (addressChecks.isSystemAccount(beneficiaryAddress)
                    || !addressChecks.isPresent(beneficiaryAddress, frame)) {
                return haltFor(null, 0, INVALID_SOLIDITY_ADDRESS);
            }

            final var tbdAddress = frame.getRecipientAddress();
            final var proxyWorldUpdater = (ProxyWorldUpdater) frame.getWorldUpdater();
            // Enforce Hedera-specific restrictions on account deletion
            final var maybeHaltReason = proxyWorldUpdater.tryTrackingDeletion(tbdAddress, beneficiaryAddress);
            if (maybeHaltReason.isPresent()) {
                return haltFor(null, 0, maybeHaltReason.get());
            }

            // Now proceed with the self-destruct
            final var inheritance =
                    requireNonNull(proxyWorldUpdater.get(tbdAddress)).getBalance();
            final var beneficiary = proxyWorldUpdater.get(beneficiaryAddress);
            final var beneficiaryIsWarm =
                    frame.warmUpAddress(beneficiaryAddress) || gasCalculator().isPrecompile(beneficiaryAddress);
            final var cost = gasCalculator().selfDestructOperationGasCost(beneficiary, inheritance)
                    + (beneficiaryIsWarm ? 0L : gasCalculator().getColdAccountAccessCost());
            if (frame.isStatic()) {
                return new OperationResult(cost, ILLEGAL_STATE_CHANGE);
            } else if (frame.getRemainingGas() < cost) {
                return new OperationResult(cost, INSUFFICIENT_GAS);
            }

            // This will enforce the Hedera signing requirements (while treating any Key{contractID=tbdAddress}
            // or Key{delegatable_contract_id=tbdAddress} keys on the beneficiary account as active); it could
            // also fail if the beneficiary is a token address
            final var maybeReasonToHalt = proxyWorldUpdater.tryTransfer(
                    tbdAddress, beneficiaryAddress, inheritance.toLong(), isDelegateCall(frame));
            if (maybeReasonToHalt.isPresent()) {
                return new OperationResult(cost, maybeReasonToHalt.get());
            }
            frame.addSelfDestruct(tbdAddress);
            frame.addRefund(beneficiaryAddress, inheritance);
            frame.setState(MessageFrame.State.CODE_SUCCESS);
            return new OperationResult(cost, null);
        } catch (UnderflowException ignore) {
            return UNDERFLOW_RESPONSE;
        }
    }

    private OperationResult haltFor(
            @Nullable final Account beneficiary, final long inheritance, @NonNull final ExceptionalHaltReason reason) {
        final long cost = gasCalculator().selfDestructOperationGasCost(beneficiary, Wei.of(inheritance));
        return new OperationResult(cost, reason);
    }
}
