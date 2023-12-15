/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.platform;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.metrics.DoubleAccumulator;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotEntry;
import com.swirlds.common.threading.atomic.AtomicDouble;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleSupplier;

/**
 * Platform-implementation of {@link DoubleAccumulator}
 */
public class DefaultDoubleAccumulator extends DefaultMetric implements DoubleAccumulator {

    private final AtomicDouble container;
    private final DoubleBinaryOperator accumulator;
    private final DoubleSupplier initializer;

    public DefaultDoubleAccumulator(@NonNull final Config config) {
        super(config);
        final double initialValue = config.getInitialValue();
        final DoubleSupplier configInitializer = config.getInitializer();

        this.accumulator = config.getAccumulator();
        this.initializer = configInitializer != null ? configInitializer : () -> initialValue;
        this.container = new AtomicDouble(this.initializer.getAsDouble());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getInitialValue() {
        return initializer.getAsDouble();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SnapshotEntry> takeSnapshot() {
        return List.of(new SnapshotEntry(VALUE, container.getAndSet(initializer.getAsDouble())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double get() {
        return container.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(final double other) {
        container.accumulateAndGet(other, accumulator);
    }

    @Override
    public void reset() {
        container.set(initializer.getAsDouble());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("value", get())
                .toString();
    }
}
