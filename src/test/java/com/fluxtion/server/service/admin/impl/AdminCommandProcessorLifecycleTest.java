/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server.service.admin.impl;

import com.fluxtion.runtime.service.Service;
import com.fluxtion.server.MongooseServer;
import com.fluxtion.server.config.MongooseServerConfig;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for the admin gateways showing no commands.
 * <p>
 * AdminCommandProcessor is a LifeCycleEventSource, so it is skipped by the plain-service
 * lifecycle loop; it must register itself with the EventFlowManager in setEventFlowManager so the
 * flow manager drives its init()/start(). start() is what registers the built-in commands
 * (help, ?, commands, eventSources). If that registration is missing, start() never runs and the
 * telnet/REST gateways report "command not found" for everything.
 */
public class AdminCommandProcessorLifecycleTest {

    @Test
    void builtInCommandsAreRegisteredAfterServerStart() {
        MongooseServer server = new MongooseServer(new MongooseServerConfig());
        AdminCommandProcessor processor = new AdminCommandProcessor();
        server.registerService(new Service<>(processor, AdminCommandRegistry.class, "adminRegistry"));

        try {
            server.init();
            server.start();

            List<String> commands = processor.commandList();
            assertTrue(commands.containsAll(List.of("help", "?", "commands", "eventSources")),
                    "AdminCommandProcessor.start() must register the built-in commands; "
                            + "actual command list = " + commands);
        } finally {
            server.stop();
        }
    }
}
