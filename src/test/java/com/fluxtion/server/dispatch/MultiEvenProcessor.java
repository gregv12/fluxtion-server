/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.server.FluxtionServer;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

public class MultiEvenProcessor {

    @Test
    public void multiEven() throws InterruptedException {

        FluxtionServer.bootServer(new StringReader(config), System.out::println);

        Thread.sleep(1_000_000);
//        EventFlowManager eventFlowManager = new EventFlowManager();
//
//        AppConfig appConfig = new AppConfig();
//        EventProcessorGroupConfig handler1Config = new EventProcessorGroupConfig();
//        handler1Config.getEventHandlers().put()
//
//
//        appConfig.getEventHandlers().add(handler1Config);

    }

    private static String config = """            
            # --------- EVENT INPUT FEEDS BEGIN CONFIG ---------
            eventFeeds:
              - instance: !!com.fluxtion.server.dispatch.HeartBeatEventFeed { }
                name: heartBeater
                agentName: heartBeatPublisher-thread
                broadcast: true
            # --------- EVENT INPUT FEEDS END CONFIG ---------
            
            
            # --------- EVENT HANDLERS BEGIN CONFIG ---------
            eventHandlers:
              - agentName: heartBeatProcessor-thread
                eventHandlers:
                  heartBeatProcessor_1:
                    eventHandler: !!com.fluxtion.server.dispatch.HeartBeatExampleProcessor { }
                    logLevel: DEBUG
                  heartBeatProcessor_2:
                    eventHandler: !!com.fluxtion.server.dispatch.HeartBeatExampleProcessor { }
                    logLevel: DEBUG
            # --------- EVENT HANDLERS END CONFIG ---------
            
            # --------- AGENT THREAD BEGIN CONFIG ---------
            agentThreads:
              - agentName: heartBeatPublisher-thread
                idleStrategy: !!com.fluxtion.agrona.concurrent.BusySpinIdleStrategy { }
              - agentName: heartBeatProcessor-thread
                idleStrategy: !!com.fluxtion.agrona.concurrent.BusySpinIdleStrategy { }
            # --------- AGENT THREAD END CONFIG ---------
            """;
}
