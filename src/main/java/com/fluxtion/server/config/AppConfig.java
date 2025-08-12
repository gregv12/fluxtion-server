/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
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

    private IdleStrategy idleStrategy = new YieldingIdleStrategy();

    /**
     * Return an {@link IdleStrategy}, looking up from config if the supplied strategy is null
     *
     * @param preferredIdeIdleStrategy the preferred idle strategy to use
     * @param agentName                The name of the agent in {@link ThreadConfig}
     * @return An IdleStrategy
     */
    public IdleStrategy lookupIdleStrategyWhenNull(IdleStrategy preferredIdeIdleStrategy, String agentName) {
        if (preferredIdeIdleStrategy == null && agentThreads == null) {
            return idleStrategy;
        } else if (preferredIdeIdleStrategy == null) {
            return agentThreads.stream().filter(cfg -> cfg.getAgentName().equals(agentName))
                    .findFirst()
                    .map(ThreadConfig::getIdleStrategy)
                    .orElse(new YieldingIdleStrategy());
        }
        return preferredIdeIdleStrategy;
    }

    /**
     * @param agentName              The name of the agent in {@link ThreadConfig}
     * @param defaultIdeIdleStrategy the default idle strategy to use if no match found in {@link ThreadConfig}
     * @return An IdleStrategy
     */
    public IdleStrategy getIdleStrategyOrDefault(String agentName, IdleStrategy defaultIdeIdleStrategy) {
        if (agentThreads == null) {
            return defaultIdeIdleStrategy;
        }
        var idleStrategy = agentThreads.stream().filter(cfg -> cfg.getAgentName().equals(agentName))
                .findFirst()
                .map(ThreadConfig::getIdleStrategy)
                .orElse(new YieldingIdleStrategy());
        return idleStrategy == null ? defaultIdeIdleStrategy : idleStrategy;
    }
}
