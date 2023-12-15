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

package com.swirlds.platform.recovery;

import com.swirlds.common.scratchpad.ScratchpadType;

/**
 * Defines the data for the recovery scratchpad.
 */
public enum RecoveryScratchpad implements ScratchpadType {

    /**
     * The epoch hash that the platform is currently "in". A platform does not consider itself to be "in" an epoch until
     * it has performed all necessary cleanup (i.e. removing files from the previous epoch).
     */
    EPOCH_HASH(0);

    public static final String SCRATCHPAD_ID = "platform.recovery";

    private final int id;

    /**
     * Constructor
     *
     * @param id the ID of this field
     */
    RecoveryScratchpad(final int id) {
        this.id = id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getFieldId() {
        return id;
    }
}
