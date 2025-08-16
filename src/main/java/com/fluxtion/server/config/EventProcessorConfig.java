/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.config;

import com.fluxtion.runtime.DefaultEventProcessor;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.audit.EventLogControlEvent;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Data
public class EventProcessorConfig<T extends EventProcessor<?>> {
    private T eventHandler;
    private ObjectEventHandlerNode customHandler;
    private Supplier<T> eventHandlerBuilder;
    private EventLogControlEvent.LogLevel logLevel;
    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.NONE)
    private Map<String, Object> configMap = new HashMap<>();

    @SuppressWarnings({"unchecked"})
    public T getEventHandler() {
        if (eventHandler == null && customHandler != null) {
            DefaultEventProcessor wrappingProcessor = new DefaultEventProcessor(customHandler);
            eventHandler = (T) wrappingProcessor;
        }
        return eventHandler;
    }

    public ConfigMap getConfig() {
        return new ConfigMap(getConfigMap());
    }
}
