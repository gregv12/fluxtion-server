/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.example.hellomongoose;

import com.fluxtion.agrona.concurrent.BusySpinIdleStrategy;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.server.FluxtionServer;
import com.fluxtion.server.config.AppConfig;
import com.fluxtion.server.config.EventFeedConfig;
import com.fluxtion.server.config.EventProcessorConfig;
import com.fluxtion.server.config.EventProcessorGroupConfig;
import com.fluxtion.server.connector.memory.InMemoryEventSource;

/**
 * One-file getting-started example for Mongoose server.
 *
 * It boots a server with:
 * - A simple business-logic handler (ObjectEventHandlerNode) that prints String events
 * - An in-memory feed that we publish a couple of messages to
 *
 * Run this class' main() to see events flowing through your handler.
 */
public final class HelloMongoose {

    /**
     * Simple handler focused on business logic: prints any String event it receives.
     * Runs single-threaded on the processor agent.
     */
    public static class PrintHandler extends ObjectEventHandlerNode {
        @Override
        protected boolean handleEvent(Object event) {
            if (event instanceof String s) {
                System.out.println("Got event: " + s);
            }
            // true indicates the event was handled without requesting a stop
            return true;
        }
    }

    public static void main(String[] args) {
        // 1) Business logic handler
        var handler = new PrintHandler();

        // 2) Create an in-memory event feed (String payloads)
        var feed = new InMemoryEventSource<String>();
        feed.setCacheEventLog(true); // allow publish-before-start, will replay on startComplete

        // 3) Wire processor group with our handler
        var processorGroup = EventProcessorGroupConfig.builder()
                .agentName("processor-agent")
                .put("hello-processor", new EventProcessorConfig<>(handler))
                .build();

        // 4) Wire the feed on its own agent with a busy-spin idle strategy (lowest latency)
        var feedCfg = EventFeedConfig.builder()
                .instance(feed)
                .name("hello-feed")
                .agent("feed-agent", new BusySpinIdleStrategy())
                .build();

        // 5) Build the application config and boot the server
        var app = AppConfig.builder()
                .addProcessorGroup(processorGroup)
                .addEventFeed(feedCfg)
                .build();

        // boot with a no-op record consumer
        var server = FluxtionServer.bootServer(app, rec -> {});

        // 6) Publish a few events
        feed.offer("hi");
        feed.offer("mongoose");

        // 7) Give the agents a brief moment to process (demo only)
        try { Thread.sleep(250); } catch (InterruptedException ignored) {}

        // 8) Stop the server (in real apps, you keep it running)
        server.stop();
    }
}
