/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.config;

import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.agrona.concurrent.YieldingIdleStrategy;
import lombok.Data;

import java.util.Map;

@Data
public class EventProcessorGroupConfig {
    private String agentName;
    private IdleStrategy idleStrategy = new YieldingIdleStrategy();
    private Map<String, EventProcessorConfig<?>> eventHandlers;
}
