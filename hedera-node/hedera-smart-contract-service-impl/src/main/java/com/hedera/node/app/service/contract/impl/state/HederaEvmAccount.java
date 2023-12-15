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

package com.hedera.node.app.service.contract.impl.state;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;

public interface HederaEvmAccount extends MutableAccount {
    /**
     * Returns whether this account is an ERC-20/ERC-721 facade for a Hedera token.
     *
     * @return whether this account is token facade
     */
    boolean isTokenFacade();

    /**
     * Returns the Hedera account id for this account.
     *
     * @return the Hedera account id
     * @throws IllegalStateException if this account is a token facade
     */
    @NonNull
    AccountID hederaId();

    /**
     * Returns the Hedera contract id for this account.
     *
     * @return the Hedera contract id, including if the account is a token facade
     */
    @NonNull
    ContractID hederaContractId();

    /**
     * Returns the EVM code for this account. Added here to avoid client code needing to manage a
     * cache of {@link org.hyperledger.besu.evm.Code} wrappers around raw bytecode returned by
     * {@link Account#getCode()}.
     *
     * @return the EVM code for this account
     */
    @NonNull
    Code getEvmCode();
}
