/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.example.hellomongoose;

import com.fluxtion.agrona.concurrent.BusySpinIdleStrategy;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.server.config.AppConfig;
import com.fluxtion.server.connector.memory.InMemoryEventSource;

import static com.fluxtion.server.FluxtionServer.bootServer;

/**
 *
 */
public final class HelloMongoose {
    public static void main(String[] args) {
        // 1) Business logic handler
        var handler = new ObjectEventHandlerNode() {
            @Override
            protected boolean handleEvent(Object event) {
                if (event instanceof String s) {
                    System.out.println("Got event: " + s);
                }
                return true;
            }
        };

        // 2) Build in memory feed
        var feed = new InMemoryEventSource<String>();

        // 3) Build and boot server with an in-memory feed and handler
        var app = new AppConfig()
                .addProcessor("processor-agent", handler, "hello-handler")
                .addEventSourceWorker(feed,
                        "hello-feed", //name
                        true, //broadcast events - no subscription required
                        "feed-agent", //agent name
                        new BusySpinIdleStrategy());// agent idel strategy

        var server = bootServer(app, rec -> {
        });

        // 4) Publish a few events
        feed.offer("hi");
        feed.offer("mongoose");

        // 5) Cleanup (in a real app, keep running)
        server.stop();
    }
}
