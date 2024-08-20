/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.config;

import lombok.Data;

import java.util.Map;

@Data
public class EventProcessorGroupConfig {
    private String agentName;
    private Map<String, EventProcessorConfig<?>> eventHandlers;
}
