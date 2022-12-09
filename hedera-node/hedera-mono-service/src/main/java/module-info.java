module com.hedera.node.app.service.mono {
    exports com.hedera.node.app.service.mono;
    exports com.hedera.node.app.service.mono.state.submerkle to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.exceptions to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.legacy.core.jproto to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.utils to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.ledger to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.store.models to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.state.merkle to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.utils.accessors to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.sigs.utils to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.sigs.verification to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.files to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.state.virtual.schedule to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.store.schedule to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.store.tokens to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.context to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.state.enums to
            com.hedera.node.app.service.mono.testFixtures;
    exports com.hedera.node.app.service.mono.throttling to
            com.fasterxml.jackson.databind;

    opens com.hedera.node.app.service.mono to
            com.swirlds.common;
    opens com.hedera.node.app.service.mono.context.properties to
            com.swirlds.common;
    opens com.hedera.node.app.service.mono.legacy.core.jproto to
            com.swirlds.common;
    opens com.hedera.node.app.service.mono.state.merkle to
            com.swirlds.common;
    opens com.hedera.node.app.service.mono.state.merkle.internals to
            com.swirlds.common;
    opens com.hedera.node.app.service.mono.state.submerkle to
            com.swirlds.common;
    opens com.hedera.node.app.service.mono.state.virtual to
            com.swirlds.common;
    opens com.hedera.node.app.service.mono.state.virtual.entities to
            com.swirlds.common;
    opens com.hedera.node.app.service.mono.state.virtual.schedule to
            com.swirlds.common;
    opens com.hedera.node.app.service.mono.state.virtual.temporal to
            com.swirlds.common;
    opens com.hedera.node.app.service.mono.stream to
            com.swirlds.common;

    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.swirlds.common;
    requires dagger;
    requires javax.inject;
    requires com.hedera.node.app.spi;
    requires com.google.protobuf;
    requires com.google.common;
    requires org.apache.logging.log4j;
    requires com.hedera.node.app.hapi.utils;
    requires com.swirlds.merkle;
    requires com.swirlds.virtualmap;
    requires tuweni.bytes;
    requires org.hyperledger.besu.datatypes;
    requires org.hyperledger.besu.evm;
    requires static com.github.spotbugs.annotations;
    requires org.apache.commons.codec;
    requires com.swirlds.fchashmap;
    requires com.swirlds.jasperdb;
    requires com.swirlds.platform;
    requires org.apache.commons.lang3;
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.app.service.evm;
    requires com.swirlds.fcqueue;
    requires com.hedera.node.app.hapi.fees;
    requires headlong;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.swirlds.logging;
    requires org.bouncycastle.provider;
    requires tuweni.units;
    requires commons.collections4;
    requires org.eclipse.collections.impl;
    requires org.apache.commons.io;
    requires io.grpc;
    requires grpc.stub;
}
