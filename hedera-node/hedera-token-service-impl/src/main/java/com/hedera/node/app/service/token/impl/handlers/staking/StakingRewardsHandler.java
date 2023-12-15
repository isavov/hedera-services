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

package com.hedera.node.app.service.token.impl.handlers.staking;

import static com.hedera.node.app.service.token.impl.handlers.staking.StakingUtilities.NOT_REWARDED_SINCE_LAST_STAKING_META_CHANGE;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.records.FinalizeContext;
import java.util.Map;

/**
 * On each transaction, before finalizing the state to a transaction record, goes through all the modified accounts
 * and pays out the staking rewards if any.
 * Staking rewards are assessed when an account that is staked to a node or if the account is staked to another
 * account that is staked to a node, has one of following conditions met:
 * 1. The account's balance changes
 * 2. The account's stakedId changes
 * 3. The account's declineReward field changes
 * 4. If stakedToMe field of the node to which the account is staked changes
 *
 */
public interface StakingRewardsHandler {
    /**
     * Goes through all the modified accounts and pays out the staking rewards if any and returns the map of account id
     * to the amount of rewards paid out.
     * @param context the context of the transaction
     * @return a map of account id to the amount of rewards paid out
     */
    Map<AccountID, Long> applyStakingRewards(final FinalizeContext context);

    /**
     * Checks if the account has been rewarded since the last staking metadata change.
     * @param account the account to check
     * @return true if the account has been rewarded since the last staking metadata change, false otherwise
     */
    default boolean isRewardedSinceLastStakeMetaChange(Account account) {
        return account.stakeAtStartOfLastRewardedPeriod() != NOT_REWARDED_SINCE_LAST_STAKING_META_CHANGE;
    }
}
