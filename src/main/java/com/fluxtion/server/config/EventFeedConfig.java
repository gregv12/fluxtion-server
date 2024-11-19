/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.config;

import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.input.NamedEventFeed;
import com.fluxtion.runtime.service.Service;
import com.fluxtion.server.dispatch.EventSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@Experimental
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventFeedConfig<T> {

    private T instance;
    private String name;
    private boolean broadcast = false;
    //event feed management
    private EventSource.EventWrapStrategy eventWrapStrategy = EventSource.EventWrapStrategy.NAMED_EVENT;

    @SneakyThrows
    @SuppressWarnings({"unchecked", "all"})
    public Service<T> toServiceAgent() {
        if (instance instanceof EventSource<?> eventSource) {
            eventWrapStrategy = broadcast ? EventSource.EventWrapStrategy.BROADCAST_EVENT : EventSource.EventWrapStrategy.NAMED_EVENT;
            eventSource.setEventWrapStrategy(eventWrapStrategy);
        }
        Service svc = new Service(instance, NamedEventFeed.class, name);
        return svc;
    }
}
