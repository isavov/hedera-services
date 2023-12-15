/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app;

import static com.hedera.node.app.service.mono.context.AppsManager.APPS;
import static com.hedera.node.app.service.mono.context.properties.SemanticVersions.SEMANTIC_VERSIONS;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.hedera.node.app.service.mono.ServicesApp;
import com.hedera.node.app.service.mono.ServicesState;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.Browser;
import com.swirlds.platform.listeners.PlatformStatusChangeListener;
import com.swirlds.platform.listeners.ReconnectCompleteListener;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldMain;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.state.notifications.IssListener;
import com.swirlds.platform.system.state.notifications.NewSignedStateListener;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation of {@link SwirldMain} for the mono-service.
 */
public class MonoServicesMain implements SwirldMain {
    private static final Logger log = LogManager.getLogger(MonoServicesMain.class);

    /** Stores information related to the running of services in the mono-repo */
    private ServicesApp app;

    /**
     * Convenience launcher for dev env.
     *
     * @param args ignored
     */
    public static void main(final String... args) {
        Browser.main(args);
    }

    @Override
    public SoftwareVersion getSoftwareVersion() {
        return SEMANTIC_VERSIONS.deployedSoftwareVersion();
    }

    @Override
    public void init(final Platform ignore, final NodeId nodeId) {
        try {
            app = APPS.get(nodeId);
            initApp();
        } catch (final IllegalArgumentException iae) {
            log.error("No app present for {}", nodeId, iae);
            throw new AssertionError("Cannot continue without an app");
        }
    }

    @Override
    public SwirldState newState() {
        return new ServicesState();
    }

    @Override
    public void run() {
        /* No-op. */
    }

    private void initApp() {
        if (defaultCharsetIsCorrect() && sha384DigestIsAvailable()) {
            try {
                Locale.setDefault(Locale.US);
                consoleLog(String.format("Using context to initialize HederaNode#%s...", app.nodeId()));
                doStagedInit();
            } catch (final Exception e) {
                log.error("Fatal precondition violated in HederaNode#{}", app.nodeId(), e);
                app.systemExits().fail(1);
            }
        } else {
            app.systemExits().fail(1);
        }
    }

    private void doStagedInit() {
        validateLedgerState();
        log.info("Ledger state ok");

        configurePlatform();
        log.info("Platform is configured w/ callbacks and stats registered");

        exportAccountsIfDesired();
        log.info("Accounts exported (if requested)");

        startNettyIfAppropriate();
        log.info("Netty started (if appropriate)");
    }

    private void exportAccountsIfDesired() {
        app.accountsExporter().toFile(app.workingState().accounts());
    }

    private void startNettyIfAppropriate() {
        app.grpcStarter().startIfAppropriate();
    }

    private void configurePlatform() {
        final var platform = app.platform();
        app.statsManager().initializeFor(platform);
    }

    private void validateLedgerState() {
        app.ledgerValidator().validate(app.workingState().accounts());
        app.nodeInfo().validateSelfAccountIfNonZeroStake();
        final var notifications = app.notificationEngine().get();
        notifications.register(PlatformStatusChangeListener.class, app.statusChangeListener());
        notifications.register(ReconnectCompleteListener.class, app.reconnectListener());
        notifications.register(StateWriteToDiskCompleteListener.class, app.stateWriteToDiskListener());
        notifications.register(NewSignedStateListener.class, app.newSignedStateListener());
        notifications.register(IssListener.class, app.issListener());
    }

    private boolean defaultCharsetIsCorrect() {
        final var charset = app.nativeCharset().get();
        if (!UTF_8.equals(charset)) {
            log.error("Default charset is {}, not UTF-8", charset);
            return false;
        }
        return true;
    }

    private boolean sha384DigestIsAvailable() {
        try {
            app.digestFactory().forName("SHA-384");
            return true;
        } catch (final NoSuchAlgorithmException e) {
            log.error(e);
            return false;
        }
    }

    private void consoleLog(final String s) {
        log.info(s);
        app.consoleOut().ifPresent(c -> c.println(s));
    }
}
