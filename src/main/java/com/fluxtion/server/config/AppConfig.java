/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.config;

import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.agrona.concurrent.YieldingIdleStrategy;
import lombok.Data;

import java.util.List;

@Data
public class AppConfig {
    //event handler
    private List<EventProcessorGroupConfig> eventHandlers;

    //event feeds
    private List<EventFeedConfig<?>> eventFeeds;

    //event sink
    private List<EventSinkConfig<?>> eventSinks;

    //services
    private List<ServiceConfig<?>> services;

    //agent thread config
    private List<ThreadConfig> agentThreads;

    public IdleStrategy getIdleStrategy(String agentName, IdleStrategy overrideIdeIdleStrategy) {
        if (overrideIdeIdleStrategy == null) {
            return agentThreads.stream().filter(cfg -> cfg.getAgentName().equals(agentName))
                    .findFirst()
                    .map(ThreadConfig::getIdleStrategy)
                    .orElse(new YieldingIdleStrategy());
        }
        return overrideIdeIdleStrategy;
    }
}
