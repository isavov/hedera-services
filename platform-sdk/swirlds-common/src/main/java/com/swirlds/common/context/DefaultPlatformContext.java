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

package com.swirlds.common.context;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.PlatformMetricsProvider;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Default implementation of the platform context. Warning: this class is private API and will be changed in the future.
 * The {@link PlatformContext} interface should be used all the time to interact with basic services.
 */
public final class DefaultPlatformContext implements PlatformContext {

    private final Configuration configuration;

    private final Metrics metrics;

    private final Cryptography cryptography;

    public DefaultPlatformContext(
            final NodeId nodeId,
            @NonNull final PlatformMetricsProvider metricsProvider,
            @NonNull final Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.metrics = Objects.requireNonNull(metricsProvider, "metricsProvider must not be null")
                .createPlatformMetrics(nodeId);
        this.cryptography = CryptographyHolder.get();
    }

    public DefaultPlatformContext(
            final Configuration configuration, final Metrics metrics, final Cryptography cryptography) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.cryptography = Objects.requireNonNull(cryptography, "cryptography");
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public Cryptography getCryptography() {
        return cryptography;
    }

    @Override
    public Metrics getMetrics() {
        return metrics;
    }
}
