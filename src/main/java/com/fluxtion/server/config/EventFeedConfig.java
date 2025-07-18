/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.config;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.input.NamedFeed;
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
public class EventFeedConfig<T> {

    private T instance;
    private String name;
    private boolean broadcast = false;
    private boolean wrapWithNamedEvent = false;
    //event feed management
    private EventSource.EventWrapStrategy eventWrapStrategy = EventSource.EventWrapStrategy.SUBSCRIPTION_NAMED_EVENT;
    private EventSource.SlowConsumerStrategy slowConsumerStrategy = EventSource.SlowConsumerStrategy.BACKOFF;
    private Function<T, ?> valueMapper = Function.identity();
    //optional agent configurationx
    private String agentName;
    private IdleStrategy idleStrategy;

    public boolean isAgent() {
        return agentName != null;
    }

    @SneakyThrows
    @SuppressWarnings({"unchecked", "all"})
    public Service<T> toService() {
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
            eventSource_t.setSlowConsumerStrategy(slowConsumerStrategy);
            eventSource_t.setDataMapper(valueMapper);
        }
        Service svc = new Service(instance, NamedFeed.class, name);
        return svc;
    }

    @SneakyThrows
    @SuppressWarnings({"unchecked", "all"})
    public <A extends Agent> ServiceAgent<A> toServiceAgent() {
        Service svc = toService();
        return new ServiceAgent<>(agentName, idleStrategy, svc, (A) instance);
    }
}
