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

package com.hedera.node.app.service.token.impl.handlers.transfer.customfees;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.CustomFee;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;

/**
 * Provides a record for custom fee meta data for given token
 * @param tokenId the token id of the token for which this is the custom fee metadata
 * @param treasuryId the treasury account id of the token
 * @param customFees the list of custom fees for the token
 * @param tokenType the type of the token
 */
public record CustomFeeMeta(
        @NonNull TokenID tokenId,
        @NonNull AccountID treasuryId,
        @NonNull List<CustomFee> customFees,
        @NonNull TokenType tokenType) {
    public static CustomFeeMeta customFeeMetaFrom(@NonNull final Token token) {
        requireNonNull(token);
        return new CustomFeeMeta(
                token.tokenId(),
                token.treasuryAccountId(),
                token.customFeesOrElse(Collections.emptyList()),
                token.tokenType());
    }
}
