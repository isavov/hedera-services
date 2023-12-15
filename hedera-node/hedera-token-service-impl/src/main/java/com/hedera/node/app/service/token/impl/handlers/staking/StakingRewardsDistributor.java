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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import com.hedera.node.app.spi.workflows.record.DeleteCapableTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper class for paying out staking rewards.
 * This is called for all accounts that are modified in state for the current transaction,
 * and are staking to a node
 */
@Singleton
public class StakingRewardsDistributor {
    private static final Logger log = LogManager.getLogger(StakingRewardsDistributor.class);
    private StakingRewardsHelper stakingRewardHelper;
    private StakeRewardCalculatorImpl rewardCalculator;

    @Inject
    public StakingRewardsDistributor(
            @NonNull final StakingRewardsHelper stakingRewardHelper,
            @NonNull final StakeRewardCalculatorImpl rewardCalculator) {
        this.stakingRewardHelper = stakingRewardHelper;
        this.rewardCalculator = rewardCalculator;
    }

    public Map<AccountID, Long> payRewardsIfPending(
            @NonNull final Set<AccountID> possibleRewardReceivers,
            @NonNull final WritableAccountStore writableStore,
            @NonNull final WritableNetworkStakingRewardsStore stakingRewardsStore,
            @NonNull final WritableStakingInfoStore stakingInfoStore,
            @NonNull final Instant consensusNow,
            @NonNull final DeleteCapableTransactionRecordBuilder recordBuilder) {
        requireNonNull(possibleRewardReceivers);

        final Map<AccountID, Long> rewardsPaid = new HashMap<>();
        for (final var receiver : possibleRewardReceivers) {
            final var originalAccount = writableStore.getOriginalValue(receiver);
            final var modifiedAccount = writableStore.get(receiver);
            final var reward = rewardCalculator.computePendingReward(
                    originalAccount, stakingInfoStore, stakingRewardsStore, consensusNow);

            var receiverId = receiver;
            var beneficiary = modifiedAccount;
            // Only if reward is greater than zero do all operations below.
            // But, even if reward is zero add it to the rewardsPaid map, if the account is not declining reward.
            // It is important to know that if the reward is zero because of its zero stake in last period.
            // This is needed to update stakePeriodStart for the account.
            if (reward > 0) {
                stakingRewardHelper.decreasePendingRewardsBy(stakingRewardsStore, reward);

                // We cannot reward a deleted account, so keep redirecting to the beneficiaries of deleted
                // accounts until we find a non-deleted account to try to reward (it may still decline)
                if (modifiedAccount.deleted()) {
                    final var maxRedirects = recordBuilder.getNumberOfDeletedAccounts();
                    var j = 1;
                    do {
                        if (j++ > maxRedirects) {
                            log.error(
                                    "With {} accounts deleted, last redirect in modifications led to deleted"
                                            + " beneficiary {}",
                                    maxRedirects,
                                    receiverId);
                            throw new IllegalStateException("Had to redirect reward to a deleted beneficiary");
                        }
                        receiverId = recordBuilder.getDeletedAccountBeneficiaryFor(receiverId);
                        beneficiary = writableStore.getOriginalValue(receiverId);
                    } while (beneficiary.deleted());
                }
            }

            if (!beneficiary.declineReward() && reward > 0) {
                // even if reward is zero it will be added to rewardsPaid
                applyReward(reward, beneficiary, writableStore);
                rewardsPaid.merge(receiverId, reward, Long::sum);
            }
        }
        return rewardsPaid;
    }

    /**
     * Applies the reward to the receiver. This is done by updating the receiver's balance.
     * @param reward The reward to apply.
     * @param receiver The account that will receive the reward.
     * @param writableStore The store to update the receiver's balance in.
     */
    private void applyReward(final long reward, final Account receiver, final WritableAccountStore writableStore) {
        final var finalBalance = receiver.tinybarBalance() + reward;
        final var copy = receiver.copyBuilder();
        copy.tinybarBalance(finalBalance);
        writableStore.put(copy.build());
    }
}
