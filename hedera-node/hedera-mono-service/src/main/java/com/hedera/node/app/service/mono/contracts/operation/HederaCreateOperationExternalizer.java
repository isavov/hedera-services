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

package com.hedera.node.app.service.mono.contracts.operation;

import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.ETHEREUM_NONCE;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.KEY;
import static com.hedera.node.app.service.mono.legacy.core.jproto.JKey.denotesImmutableEntity;
import static com.hedera.node.app.service.mono.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.node.app.service.mono.state.EntityCreator.NO_CUSTOM_FEES;
import static com.hedera.node.app.service.mono.txns.contract.ContractCreateTransitionLogic.STANDIN_CONTRACT_ID_KEY;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.accountIdFromEvmAddress;

import com.hedera.node.app.service.evm.contracts.operations.CreateOperationExternalizer;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.SidecarUtils;
import com.hedera.services.stream.proto.SidecarType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Externalizes and handles child record, sidecars and lazy creation logic for create operations
 */
public class HederaCreateOperationExternalizer implements CreateOperationExternalizer {
    protected final GlobalDynamicProperties dynamicProperties;
    private final EntityCreator creator;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final RecordsHistorian recordsHistorian;

    @Inject
    public HederaCreateOperationExternalizer(
            final EntityCreator creator,
            final SyntheticTxnFactory syntheticTxnFactory,
            final RecordsHistorian recordsHistorian,
            final GlobalDynamicProperties dynamicProperties) {
        super();
        this.creator = creator;
        this.recordsHistorian = recordsHistorian;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.dynamicProperties = dynamicProperties;
    }

    /**
     * Handle the child record and hollow account completion of a create operation. Create sidecar with bytecodes of the
     * newly created contract, if sidecards are enabled.
     *
     * @param frame      current message frame
     * @param childFrame child message frame to be created
     */
    @Override
    public void externalize(MessageFrame frame, MessageFrame childFrame) {
        // Add an in-progress record so that if everything succeeds, we can externalize the
        // newly
        // created contract in the record stream with both its 0.0.X id and its EVM address.
        // C.f. https://github.com/hashgraph/hedera-services/issues/2807
        final var updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
        final var sideEffects = new SideEffectsTracker();

        ContractID createdContractId;
        final var hollowAccountID = matchingHollowAccountId(updater, childFrame.getContractAddress());

        // if a hollow account exists at the alias address, finalize it to a contract
        if (hollowAccountID != null) {
            finalizeHollowAccountIntoContract(hollowAccountID, updater, childFrame.getContractAddress());
            createdContractId = EntityIdUtils.asContract(hollowAccountID);
        } else {
            createdContractId = updater.idOfLastNewAddress();
        }

        sideEffects.trackNewContract(createdContractId, childFrame.getContractAddress());
        final var childRecord = creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects, EMPTY_MEMO);
        childRecord.onlyExternalizeIfSuccessful();
        final var opCustomizer = updater.customizerForPendingCreation();
        final var syntheticOp = syntheticTxnFactory.contractCreation(opCustomizer);
        if (dynamicProperties.enabledSidecars().contains(SidecarType.CONTRACT_BYTECODE)) {
            final var contractBytecodeSidecar = SidecarUtils.createContractBytecodeSidecarFrom(
                    createdContractId,
                    childFrame.getCode().getBytes().toArrayUnsafe(),
                    updater.get(childFrame.getContractAddress()).getCode().toArrayUnsafe());
            updater.manageInProgressRecord(
                    recordsHistorian, childRecord, syntheticOp, List.of(contractBytecodeSidecar));
        } else {
            updater.manageInProgressRecord(recordsHistorian, childRecord, syntheticOp, Collections.emptyList());
        }
    }

    /**
     * Fails new contract creation when lazy creation is disabled and the new contract matches the address of an
     * existing hollow account.
     *
     * @param frame           current message frame
     * @param contractAddress the target contract address
     * @return should creation fail
     */
    @Override
    public boolean shouldFailBasedOnLazyCreation(MessageFrame frame, Address contractAddress) {
        if (!dynamicProperties.isLazyCreationEnabled()) {
            final var hollowAccountID =
                    matchingHollowAccountId((HederaStackedWorldStateUpdater) frame.getWorldUpdater(), contractAddress);

            return hollowAccountID != null;
        }
        return false;
    }

    private AccountID matchingHollowAccountId(HederaStackedWorldStateUpdater updater, Address contract) {
        final var accountID = accountIdFromEvmAddress(updater.aliases().resolveForEvm(contract));
        final var trackingAccounts = updater.trackingAccounts();
        if (trackingAccounts.contains(accountID)) {
            final var accountKey = (JKey) updater.trackingAccounts().get(accountID, KEY);
            return denotesImmutableEntity(accountKey) ? accountID : null;
        } else {
            return null;
        }
    }

    private void finalizeHollowAccountIntoContract(
            AccountID hollowAccountID, HederaStackedWorldStateUpdater updater, Address contractAddress) {
        // We cannot reclaim the id reserved for a new contract here, even though it will not be used
        // (since in fact this creation is just finalizing a hollow account). The reason is that it is
        // possible there were child contracts created by the CONTRACT_CREATION message; and their ids
        // will not automatically "shrink" to fill a gap left by the reclaimed id. So if we reclaimed
        // the id here, the next entity number allocated would collide with the last-created child.

        // update the hollow account to be a contract
        updater.trackingAccounts().set(hollowAccountID, IS_SMART_CONTRACT, true);

        // update the hollow account key to be the default contract key
        updater.trackingAccounts().set(hollowAccountID, KEY, STANDIN_CONTRACT_ID_KEY);

        // set initial contract nonce to be the same as the nonce of the created account
        final var contractNonce = updater.get(contractAddress).getNonce();
        updater.trackingAccounts().set(hollowAccountID, ETHEREUM_NONCE, contractNonce);
    }
}
