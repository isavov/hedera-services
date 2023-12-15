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

package com.swirlds.common.wiring.transformers;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Executes a transformation for an advanced transformer as created by
 * {@link com.swirlds.common.wiring.wires.output.OutputWire#buildAdvancedTransformer(AdvancedTransformation)}.
 *
 * @param <A> the original wire output type
 * @param <B> the output type of the transformer
 */
public interface AdvancedTransformation<A, B> {

    /**
     * Given data that comes off of the original output wire, this method transforms it before it is passed to each
     * input wire that is connected to this transformer. Called once per data element per listener.
     *
     * @param a a data element from the original output wire
     * @return the transformed data element, or null if the data should not be forwarded
     */
    @Nullable
    B transform(@NonNull A a);

    /**
     * Called on the original data element after it has been forwarded to all listeners. This method can do cleanup if
     * necessary. Doing nothing is perfectly ok if the use case does not require cleanup.
     *
     * @param a the original data element
     */
    void cleanup(@NonNull A a);

    /**
     * @return the name of this transformer
     */
    @NonNull
    String getName();
}
