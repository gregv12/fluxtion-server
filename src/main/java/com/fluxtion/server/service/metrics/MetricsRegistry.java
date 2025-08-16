/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server.service.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Very small registry for metrics by name.
 */
public final class MetricsRegistry {

    private static final Map<String, EventProcessingMetrics> EVENT_METRICS = new ConcurrentHashMap<>();

    private MetricsRegistry() {}

    public static EventProcessingMetrics getOrCreate(String name) {
        return EVENT_METRICS.computeIfAbsent(name, EventProcessingMetrics::new);
    }

    public static EventProcessingMetrics get(String name) {
        return EVENT_METRICS.get(name);
    }

    public static Map<String, EventProcessingMetrics> all() {
        return EVENT_METRICS;
    }
}
