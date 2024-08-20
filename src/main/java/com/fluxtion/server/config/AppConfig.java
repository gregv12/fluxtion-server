/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.config;

import com.fluxtion.server.dispatch.LifeCycleEventSource;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AppConfig {
    private List<EventProcessorGroupConfig> eventHandlerAgents;
    private Map<String, LifeCycleEventSource<?>> eventSources;
    private List<ServiceConfig<?>> services;
    private List<ServiceWorkerConfig<?>> workerService;
}
