/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.test.util;

import com.fluxtion.runtime.audit.LogRecordListener;
import com.fluxtion.server.FluxtionServer;
import com.fluxtion.server.config.AppConfig;
import org.junit.jupiter.api.AfterEach;

/**
 * Base class for tests that boot a FluxtionServer. Ensures the server is
 * stopped after each test and provides a convenience boot method.
 */
public abstract class WithServer {
    protected FluxtionServer server;

    protected FluxtionServer boot(AppConfig cfg, LogRecordListener listener) {
        this.server = FluxtionServer.bootServer(cfg, listener);
        return this.server;
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }
}
