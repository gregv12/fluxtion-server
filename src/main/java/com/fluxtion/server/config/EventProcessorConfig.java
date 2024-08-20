/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.config;

import com.fluxtion.runtime.EventProcessor;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Data
public class EventProcessorConfig<T extends EventProcessor<?>> {
    private T eventHandler;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Map<String, Object> configMap = new HashMap<>();

    @SuppressWarnings({"raw", "unckecked"})
    public Map<String, Object> getConfigMap() {
        return configMap;
    }

    @SuppressWarnings({"raw", "unckecked"})
    public void setConfigMap(Map<String, Object> configMap) {
        this.configMap = configMap;
    }

    public ConfigMap getConfig() {
        return new ConfigMap(getConfigMap());
    }
}
