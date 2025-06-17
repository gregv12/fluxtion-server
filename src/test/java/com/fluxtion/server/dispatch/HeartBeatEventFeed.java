/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.runtime.annotations.Start;
import com.fluxtion.server.service.AbstractAgentHostedEventSourceService;

import java.time.Duration;

public class HeartBeatEventFeed extends AbstractAgentHostedEventSourceService<HeartbeatEvent> {

    public HeartBeatEventFeed() {
        super("HeartBeatService2");
    }

    @Start
    public void start() {
        heartbeat();
    }

    private void heartbeat() {
        scheduler.scheduleAfterDelay(Duration.ofSeconds(2).toMillis(), this::heartbeat);
        System.out.println("publish");
        output.publish(new HeartbeatEvent());
    }

    @Override
    public int doWork() throws Exception {
        return 0;
    }
}
