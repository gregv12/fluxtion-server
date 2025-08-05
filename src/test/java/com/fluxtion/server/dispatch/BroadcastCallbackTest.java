/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.server.FluxtionServer;
import com.fluxtion.server.config.AppConfig;
import com.fluxtion.server.config.EventProcessorConfig;
import com.fluxtion.server.config.EventProcessorGroupConfig;
import com.fluxtion.server.config.ServiceConfig;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.admin.impl.AdminCommandProcessor;
import com.fluxtion.server.service.admin.impl.CliAdminCommandProcessor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BroadcastCallbackTest {

    @Test
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
        AppConfig appConfig = new AppConfig();
        appConfig.setEventHandlers(List.of(eventProcessorGroupConfig));
        appConfig.setServices(List.of(adminRegistryConfig));

        //boot the server
        FluxtionServer.bootServer(appConfig, System.out::println);

        Thread.sleep(1_000_000);

    }
}
