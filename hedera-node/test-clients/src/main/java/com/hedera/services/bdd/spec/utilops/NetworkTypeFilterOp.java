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

package com.hedera.services.bdd.spec.utilops;

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.TargetNetworkType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * A {@link UtilOp} that runs a {@link HapiSpecOperation} if the {@link HapiSpec} is configured to run on a
 * {@link TargetNetworkType} that is contained in the {@link Set} of {@link TargetNetworkType} provided to the
 * constructor.
 */
public class NetworkTypeFilterOp extends UtilOp {
    private final Set<TargetNetworkType> allowedNetworkTypes;
    private final HapiSpecOperation[] ops;

    public NetworkTypeFilterOp(
            @NonNull final Set<TargetNetworkType> allowedNetworkTypes, @NonNull final HapiSpecOperation[] ops) {
        this.allowedNetworkTypes = requireNonNull(allowedNetworkTypes);
        this.ops = requireNonNull(ops);
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        requireNonNull(spec);
        if (allowedNetworkTypes.contains(spec.targetNetworkType())) {
            allRunFor(spec, ops);
        }
        return false;
    }

    @Override
    public String toString() {
        return "NetworkTypeFilterOp{targets=" + allowedNetworkTypes + "}";
    }
}
