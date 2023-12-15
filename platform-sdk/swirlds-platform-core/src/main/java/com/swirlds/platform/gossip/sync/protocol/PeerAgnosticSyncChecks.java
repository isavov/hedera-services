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

package com.swirlds.platform.gossip.sync.protocol;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Contains a list of checks, which determine whether a node should sync.
 * <p>
 * Doesn't take potential peer into account
 *
 * @param checks the list of checks to be performed when deciding to sync or not
 */
public record PeerAgnosticSyncChecks(@NonNull List<BooleanSupplier> checks) {
    /**
     * Checks whether the node should sync or not (peer agnostic)
     *
     * @return true if the node should sync, false otherwise
     */
    public boolean shouldSync() {
        for (final BooleanSupplier check : checks) {
            if (!check.getAsBoolean()) {
                return false;
            }
        }

        return true;
    }
}
