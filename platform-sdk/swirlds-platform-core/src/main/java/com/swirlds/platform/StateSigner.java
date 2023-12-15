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

package com.swirlds.platform;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.stream.HashSigner;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.platform.system.status.PlatformStatusGetter;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * This class is responsible for signing states and producing {@link StateSignatureTransaction}s.
 */
public class StateSigner {
    /** An object responsible for signing states with this node's key. */
    private final HashSigner signer;
    /** provides the current platform status */
    private final PlatformStatusGetter platformStatusGetter;

    /**
     * Create a new {@link StateSigner} instance.
     *
     * @param signer               an object responsible for signing states with this node's key
     * @param platformStatusGetter provides the current platform status
     */
    public StateSigner(@NonNull final HashSigner signer, @NonNull final PlatformStatusGetter platformStatusGetter) {
        this.signer = Objects.requireNonNull(signer);
        this.platformStatusGetter = Objects.requireNonNull(platformStatusGetter);
    }

    /**
     * Sign the given state and produce a {@link StateSignatureTransaction} containing the signature. This method
     * assumes that the given {@link ReservedSignedState} is reserved by the caller and will release the state when
     * done.
     *
     * @param reservedSignedState the state to sign
     * @return a {@link StateSignatureTransaction} containing the signature, or null if the state should not be signed
     */
    public @Nullable StateSignatureTransaction signState(@NonNull final ReservedSignedState reservedSignedState) {
        try (reservedSignedState) {
            if (platformStatusGetter.getCurrentStatus() == PlatformStatus.REPLAYING_EVENTS) {
                // the only time we don't want to submit signatures is during PCES replay
                return null;
            }

            final Hash stateHash =
                    Objects.requireNonNull(reservedSignedState.get().getState().getHash());
            final Signature signature = signer.sign(stateHash);
            Objects.requireNonNull(signature);

            return new StateSignatureTransaction(reservedSignedState.get().getRound(), signature, stateHash);
        }
    }
}
