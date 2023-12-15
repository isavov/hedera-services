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

package com.hedera.node.config.testfixtures;

import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.converter.*;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.ApiPermissionConfig;
import com.hedera.node.config.data.AutoCreationConfig;
import com.hedera.node.config.data.AutoRenew2Config;
import com.hedera.node.config.data.AutoRenewConfig;
import com.hedera.node.config.data.BalancesConfig;
import com.hedera.node.config.data.BlockRecordStreamConfig;
import com.hedera.node.config.data.BootstrapConfig;
import com.hedera.node.config.data.CacheConfig;
import com.hedera.node.config.data.ConsensusConfig;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.CryptoCreateWithAliasConfig;
import com.hedera.node.config.data.DevConfig;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.node.config.data.ExpiryConfig;
import com.hedera.node.config.data.FeesConfig;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.GrpcConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LazyCreationConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.NettyConfig;
import com.hedera.node.config.data.NetworkAdminConfig;
import com.hedera.node.config.data.RatesConfig;
import com.hedera.node.config.data.SchedulingConfig;
import com.hedera.node.config.data.SigsConfig;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.node.config.data.StatsConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.node.config.data.TopicsConfig;
import com.hedera.node.config.data.TraceabilityConfig;
import com.hedera.node.config.data.UpgradeConfig;
import com.hedera.node.config.data.UtilPrngConfig;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.node.config.validation.EmulatesMapValidator;
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.EventConfig;
import com.swirlds.common.config.OSHealthCheckConfig;
import com.swirlds.common.config.PathsConfig;
import com.swirlds.common.config.SocketConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.TransactionConfig;
import com.swirlds.common.config.WiringConfig;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.io.config.RecycleBinConfig;
import com.swirlds.common.io.config.TemporaryFileConfig;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.prometheus.PrometheusConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.fchashmap.config.FCHashMapConfig;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.platform.system.status.PlatformStatusConfig;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A builder for creating {@link TestConfigBuilder} instances, or {@link Configuration} instances for testing. The
 * builder is preloaded with all known configuration types, validators, and converters, but provides a convenient
 * way to add configuration sources inline in the test that override the default config settings.
 */
public final class HederaTestConfigBuilder {

    private HederaTestConfigBuilder() {}

    /**
     * Creates a new {@link TestConfigBuilder} instance. Attempts to register all config records that are part of the
     * base packages {@code com.hedera} or {@code com.swirlds} are automatically registered. If false, no config record
     * is registered.
     *
     * @return the new {@link TestConfigBuilder} instance
     */
    @NonNull
    public static TestConfigBuilder create() {
        return new TestConfigBuilder(false)
                // Configuration Data Types from the Hashgraph Platform.
                .withConfigDataType(BasicConfig.class)
                .withConfigDataType(com.swirlds.common.config.ConsensusConfig.class)
                .withConfigDataType(EventConfig.class)
                .withConfigDataType(OSHealthCheckConfig.class)
                .withConfigDataType(PathsConfig.class)
                .withConfigDataType(SocketConfig.class)
                .withConfigDataType(StateConfig.class)
                .withConfigDataType(TransactionConfig.class)
                .withConfigDataType(WiringConfig.class)
                .withConfigDataType(CryptoConfig.class)
                .withConfigDataType(RecycleBinConfig.class)
                .withConfigDataType(TemporaryFileConfig.class)
                .withConfigDataType(ReconnectConfig.class)
                .withConfigDataType(MetricsConfig.class)
                .withConfigDataType(PrometheusConfig.class)
                .withConfigDataType(PlatformStatusConfig.class)
                .withConfigDataType(FCHashMapConfig.class)
                .withConfigDataType(MerkleDbConfig.class)
                /*
                These data types from the platform were not available on the classpath. Add if needed later.
                .withConfigDataType(AddressBookConfig.class)
                .withConfigDataType(ThreadConfig.class)
                .withConfigDataType(DispatchConfiguration.class)
                .withConfigDataType(PreconsensusEventStreamConfig.class)
                .withConfigDataType(EventCreationConfig.class)
                .withConfigDataType(ChatterConfig.class)
                .withConfigDataType(SyncConfig.class)
                .withConfigDataType(UptimeConfig.class)
                 */
                .withConfigDataType(VirtualMapConfig.class)

                // These data types, converters, and validators are defined by services.
                .withConfigDataType(AccountsConfig.class)
                .withConfigDataType(ApiPermissionConfig.class)
                .withConfigDataType(AutoCreationConfig.class)
                .withConfigDataType(AutoRenew2Config.class)
                .withConfigDataType(AutoRenewConfig.class)
                .withConfigDataType(BalancesConfig.class)
                .withConfigDataType(BlockRecordStreamConfig.class)
                .withConfigDataType(BootstrapConfig.class)
                .withConfigDataType(CacheConfig.class)
                .withConfigDataType(ConsensusConfig.class)
                .withConfigDataType(ContractsConfig.class)
                .withConfigDataType(CryptoCreateWithAliasConfig.class)
                .withConfigDataType(DevConfig.class)
                .withConfigDataType(EntitiesConfig.class)
                .withConfigDataType(ExpiryConfig.class)
                .withConfigDataType(FeesConfig.class)
                .withConfigDataType(FilesConfig.class)
                .withConfigDataType(GrpcConfig.class)
                .withConfigDataType(HederaConfig.class)
                .withConfigDataType(LazyCreationConfig.class)
                .withConfigDataType(LedgerConfig.class)
                .withConfigDataType(NettyConfig.class)
                .withConfigDataType(NetworkAdminConfig.class)
                .withConfigDataType(RatesConfig.class)
                .withConfigDataType(SchedulingConfig.class)
                .withConfigDataType(SigsConfig.class)
                .withConfigDataType(StakingConfig.class)
                .withConfigDataType(StatsConfig.class)
                .withConfigDataType(TokensConfig.class)
                .withConfigDataType(TopicsConfig.class)
                .withConfigDataType(TraceabilityConfig.class)
                .withConfigDataType(UpgradeConfig.class)
                .withConfigDataType(UtilPrngConfig.class)
                .withConfigDataType(VersionConfig.class)
                .withConverter(new CongestionMultipliersConverter())
                .withConverter(new EntityScaleFactorsConverter())
                .withConverter(new EntityTypeConverter())
                .withConverter(new KnownBlockValuesConverter())
                .withConverter(new LegacyContractIdActivationsConverter())
                .withConverter(new MapAccessTypeConverter())
                .withConverter(new RecomputeTypeConverter())
                .withConverter(new ScaleFactorConverter())
                .withConverter(new AccountIDConverter())
                .withConverter(new ContractIDConverter())
                .withConverter(new FileIDConverter())
                .withConverter(new HederaFunctionalityConverter())
                .withConverter(new PermissionedAccountsRangeConverter())
                .withConverter(new SidecarTypeConverter())
                .withConverter(new SemanticVersionConverter())
                .withConverter(new KeyValuePairConverter())
                .withConverter(new LongPairConverter())
                .withConverter(new FunctionalitySetConverter())
                .withConverter(new BytesConverter())
                .withConverter(new ProfileConverter())
                .withValidator(new EmulatesMapValidator());
    }

    /**
     * Creates a new {@link Configuration} instance that has automatically registered all config records that are part
     * of the base packages {@code com.hedera} or {@code com.swirlds}.
     *
     * @return a new {@link Configuration} instance
     */
    @NonNull
    public static Configuration createConfig() {
        return create().getOrCreateConfig();
    }

    /**
     * Convenience method that creates and returns a {@link ConfigProvider} with the configuration of this builder as
     * a {@link VersionedConfig} with version number 0.
     */
    @NonNull
    public static ConfigProvider createConfigProvider() {
        final var config = createConfig();
        final var versioned = new VersionedConfigImpl(config, 0);
        return () -> versioned;
    }
}
