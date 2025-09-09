/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.server.MongooseServer;
import com.fluxtion.server.config.MongooseServerConfig;
import com.fluxtion.server.config.EventProcessorConfig;
import com.fluxtion.server.config.EventProcessorGroupConfig;
import com.fluxtion.server.config.ServiceConfig;
import com.fluxtion.server.example.MyCustomEventHandler;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.admin.impl.AdminCommandProcessor;
import com.fluxtion.server.service.admin.impl.CliAdminCommandProcessor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BroadcastCallbackTest {

    @Test
    @Disabled
    public void testPublishToConsole() throws InterruptedException {
        EventProcessorConfig<?> eventProcessorConfig = new EventProcessorConfig<>();
        eventProcessorConfig.setCustomHandler(new MyCustomEventHandler());

        Map<String, EventProcessorConfig<?>> handlerConfigMap = new HashMap<>();
        handlerConfigMap.put("customHandler", eventProcessorConfig);

        EventProcessorGroupConfig eventProcessorGroupConfig = new EventProcessorGroupConfig();
        eventProcessorGroupConfig.setAgentName("testHandler");
        eventProcessorGroupConfig.setEventHandlers(handlerConfigMap);

        //admin service
        ServiceConfig<AdminCommandRegistry> adminRegistryConfig = new ServiceConfig<>(new AdminCommandProcessor(), AdminCommandRegistry.class, "adminService");
        ServiceConfig<?> adminCli = new ServiceConfig<>()
                .service(new CliAdminCommandProcessor())
                .name("adminCli");

        //whole config
        MongooseServerConfig mongooseServerConfig = new MongooseServerConfig();
        mongooseServerConfig.setEventHandlers(List.of(eventProcessorGroupConfig));
        mongooseServerConfig.setServices(List.of(adminRegistryConfig));

        //boot the server
        MongooseServer.bootServer(mongooseServerConfig, System.out::println);
    }

    public static void main(String[] args) {
        EventProcessorConfig<?> eventProcessorConfig = new EventProcessorConfig<>();
        eventProcessorConfig.setCustomHandler(new MyCustomEventHandler());

        Map<String, EventProcessorConfig<?>> handlerConfigMap = new HashMap<>();
        handlerConfigMap.put("customHandler", eventProcessorConfig);

        EventProcessorGroupConfig eventProcessorGroupConfig = new EventProcessorGroupConfig();
        eventProcessorGroupConfig.setAgentName("testHandler");
        eventProcessorGroupConfig.setEventHandlers(handlerConfigMap);

        //admin service
        ServiceConfig<AdminCommandRegistry> adminRegistryConfig = new ServiceConfig<>(new AdminCommandProcessor(), AdminCommandRegistry.class, "adminService");
        ServiceConfig<?> adminCli = new ServiceConfig<>()
                .service(new CliAdminCommandProcessor())
                .name("adminCli");

        //whole config
        MongooseServerConfig mongooseServerConfig = new MongooseServerConfig();
        mongooseServerConfig.setEventHandlers(List.of(eventProcessorGroupConfig));
        mongooseServerConfig.setServices(List.of(adminRegistryConfig, adminCli));

        //boot the server
        MongooseServer.bootServer(mongooseServerConfig, System.out::println);
    }
}
