/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.config;

import lombok.ToString;

import java.util.Map;

@ToString
public class ConfigMap {

    private final Map<String, Object> configMap;

    public ConfigMap(Map<String, Object> configMap) {
        this.configMap = configMap;
    }

    @SuppressWarnings({"raw", "unchecked"})
    public <T> T get(String key) {
        return (T) configMap.get(key);
    }

    @SuppressWarnings({"raw", "unchecked"})
    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) configMap.getOrDefault(key, defaultValue);
    }
}
