/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.config;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.agrona.concurrent.YieldingIdleStrategy;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.input.NamedEventFeed;
import com.fluxtion.runtime.service.Service;
import com.fluxtion.server.dispatch.EventSource;
import com.fluxtion.server.dutycycle.ServiceAgent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.util.function.Function;

@Experimental
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventFeedWorkerConfig<T extends Agent> {

    private T instance;
    private String name;
    //do work thread management
    private String agentGroup;
    private IdleStrategy idleStrategy = new YieldingIdleStrategy();
    private boolean broadcast = false;
    private boolean wrapWithNamedEvent = false;
    //event feed management
    private EventSource.EventWrapStrategy eventWrapStrategy = EventSource.EventWrapStrategy.SUBSCRIPTION_NAMED_EVENT;
    private Function<T, ?> valueMapper = Function.identity();

    @SneakyThrows
    @SuppressWarnings({"unchecked", "all"})
    public ServiceAgent<T> toServiceAgent() {
        if (instance instanceof EventSource<?> eventSource) {
            if (wrapWithNamedEvent & broadcast) {
                eventWrapStrategy = EventSource.EventWrapStrategy.BROADCAST_NAMED_EVENT;
            } else if (!wrapWithNamedEvent & broadcast) {
                eventWrapStrategy = EventSource.EventWrapStrategy.BROADCAST_NOWRAP;
            } else if (wrapWithNamedEvent & !broadcast) {
                eventWrapStrategy = EventSource.EventWrapStrategy.SUBSCRIPTION_NAMED_EVENT;
            } else {
                eventWrapStrategy = EventSource.EventWrapStrategy.SUBSCRIPTION_NOWRAP;
            }
            EventSource<T> eventSource_t = (EventSource<T>) eventSource;
            eventSource_t.setEventWrapStrategy(eventWrapStrategy);
            eventSource_t.setDataMapper(valueMapper);
        }
        Service svc = new Service(instance, NamedEventFeed.class, name);
        return new ServiceAgent<>(agentGroup, idleStrategy, svc, instance);
    }
}
