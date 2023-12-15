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

package com.hedera.node.app.spi.state;

import static com.hedera.node.app.spi.HapiUtils.SEMANTIC_VERSION_COMPARATOR;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.pbj.runtime.Codec;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Defines the schema of all states for a specific {@link SemanticVersion} of a specific {@link
 * com.hedera.node.app.spi.Service} instance. It is necessary to create a new {@link Schema}
 * whenever a new {@link ReadableKVState} is to be created, or an existing one removed, or a
 * migration has to happen. If your service makes use of a forwards and backwards compatible
 * serialization system (such as protobuf), then it is not necessary to define a new {@link Schema}
 * for simple compatible changes to serialization.
 */
public abstract class Schema implements Comparable<Schema> {
    /** The version of this schema */
    private final SemanticVersion version;

    /**
     * Create a new instance
     *
     * @param version The version of this schema
     */
    protected Schema(@NonNull SemanticVersion version) {
        this.version = Objects.requireNonNull(version);
    }

    /**
     * Gets the version of this {@link Schema}
     *
     * @return The version
     */
    @NonNull
    public SemanticVersion getVersion() {
        return version;
    }

    /**
     * Gets a {@link Set} of state definitions for states to create in this schema. For example,
     * perhaps in this version of the schema, you need to create a new state FOO. The set will have
     * a {@link StateDefinition} specifying the metadata for that state.
     *
     * <p>If a state is defined in this set that already exists, it will be redefined based on the
     * returned values. This can be used if a state exists but needs to use a new {@link Codec}, for
     * example.
     *
     * @return A map of all states to be created. Possibly empty.
     */
    @NonNull
    @SuppressWarnings("rawtypes")
    public Set<StateDefinition> statesToCreate() {
        return Collections.emptySet();
    }

    /**
     * Called after all new states have been created (as per {@link #statesToCreate()}), this method
     * is used to perform all <b>synchronous</b> migrations of state. This method will always be
     * called with the {@link ReadableStates} of the previous version of the {@link Schema}. If
     * there was no previous version, then {@code previousStates} will be empty, but not null.
     *
     * @param ctx {@link MigrationContext} for this schema migration
     */
    public void migrate(@NonNull final MigrationContext ctx) {
        Objects.requireNonNull(ctx);
    }

    /**
     * The {@link Set} of state keys of all states that should be removed <b>AFTER</b> {@link
     * #migrate(MigrationContext)}.
     *
     * @return the set of states to remove
     */
    @NonNull
    public Set<String> statesToRemove() {
        return Collections.emptySet();
    }

    /**
     * Called on this schema if, and only if, this schema is the most recent schema that is not newer than the
     * version of software in use, and if the node is being restarted. Most services have nothing to do in this
     * case, but some services may need to re-read configuration, or files on disk, or something similar. They
     * are free to modify the state as needed in the same way that {@link #migrate(MigrationContext)} does.
     *
     * @param ctx {@link MigrationContext} for this schema restart operation
     */
    public void restart(@NonNull final MigrationContext ctx) {
        Objects.requireNonNull(ctx);
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(Schema o) {
        return SEMANTIC_VERSION_COMPARATOR.compare(this.version, o.version);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Schema other)) {
            return false;
        }

        return version.equals(other.version);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return version.hashCode();
    }
}
