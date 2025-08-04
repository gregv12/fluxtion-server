/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.server.FluxtionServer;
import com.fluxtion.server.config.AppConfig;
import com.fluxtion.server.config.EventProcessorConfig;
import com.fluxtion.server.config.EventProcessorGroupConfig;
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


        AppConfig appConfig = new AppConfig();
        appConfig.setEventHandlers(List.of(eventProcessorGroupConfig));

        FluxtionServer.bootServer(appConfig, System.out::println);

        Thread.sleep(1_000_000);

    }
}
