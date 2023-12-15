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

import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.UnderflowException;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.ExtCodeCopyOperation;
import org.hyperledger.besu.evm.operation.Operation;

/**
 * Customization of {@link ExtCodeCopyOperation} that treats every long-zero address for an account
 * below {@code 0.0.1001} as having zero code; and otherwise requires the account to be present or
 * halts the frame with {@link CustomExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS}.
 */
public class CustomExtCodeCopyOperation extends ExtCodeCopyOperation {
    private static final Operation.OperationResult UNDERFLOW_RESPONSE =
            new Operation.OperationResult(0, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    private final AddressChecks addressChecks;

    public CustomExtCodeCopyOperation(
            @NonNull final GasCalculator gasCalculator, @NonNull final AddressChecks addressChecks) {
        super(gasCalculator);
        this.addressChecks = addressChecks;
    }

    @Override
    public OperationResult execute(@NonNull final MessageFrame frame, @NonNull final EVM evm) {
        try {
            final var address = Words.toAddress(frame.getStackItem(0));
            final var memOffset = clampedToLong(frame.getStackItem(1));
            final var sourceOffset = clampedToLong(frame.getStackItem(2));
            final var numBytes = clampedToLong(frame.getStackItem(3));
            // Special behavior for long-zero addresses below 0.0.1001
            if (addressChecks.isNonUserAccount(address)) {
                frame.writeMemory(memOffset, sourceOffset, numBytes, Bytes.EMPTY);
                frame.popStackItems(4);
                return new OperationResult(cost(frame, memOffset, numBytes, true), null);
            }
            // Otherwise the address must be present
            if (!addressChecks.isPresent(address, frame)) {
                return new OperationResult(
                        cost(frame, memOffset, numBytes, true), CustomExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS);
            }
            return super.execute(frame, evm);
        } catch (UnderflowException ignore) {
            return UNDERFLOW_RESPONSE;
        }
    }
}
