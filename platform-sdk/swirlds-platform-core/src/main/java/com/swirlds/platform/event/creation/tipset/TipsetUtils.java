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

package com.swirlds.platform.event.creation.tipset;

import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Misc tipset utilities.
 */
public final class TipsetUtils {

    private TipsetUtils() {}

    /**
     * Build a descriptor from an EventImpl.
     *
     * @param event the event
     * @return the descriptor
     */
    public static EventDescriptor buildDescriptor(@NonNull final EventImpl event) {
        if (event.getBaseHash() == null) {
            throw new IllegalStateException("event is not hashed");
        }
        return event.getHashedData().createEventDescriptor();
    }

    /**
     * Get the descriptors of an event's parents.
     *
     * @param event the event to get parent descriptors for
     */
    @NonNull
    public static List<EventDescriptor> getParentDescriptors(@NonNull final EventImpl event) {
        final List<EventDescriptor> parentDescriptors = new ArrayList<>(2);
        if (event.getSelfParent() != null) {
            parentDescriptors.add(buildDescriptor(event.getSelfParent()));
        }
        if (event.getOtherParent() != null) {
            parentDescriptors.add(buildDescriptor(event.getOtherParent()));
        }
        return parentDescriptors;
    }

    /**
     * Get the descriptors of an event's parents.
     *
     * @param event the event to the parent descriptors for
     * @return a list of parent descriptors
     */
    @NonNull
    public static List<EventDescriptor> getParentDescriptors(@NonNull final GossipEvent event) {
        final List<EventDescriptor> parentDescriptors = new ArrayList<>(2);

        final BaseEventHashedData hashedData = event.getHashedData();
        if (hashedData.hasSelfParent()) {
            parentDescriptors.add(event.getHashedData().getSelfParent());
        }
        if (hashedData.hasOtherParent()) {
            hashedData.getOtherParents().forEach(parentDescriptors::add);
        }

        return parentDescriptors;
    }
}
