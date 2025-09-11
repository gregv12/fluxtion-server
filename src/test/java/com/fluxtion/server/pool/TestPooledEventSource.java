/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.pool;

import com.fluxtion.server.service.EventFlowService;
import com.fluxtion.server.service.EventSubscriptionKey;

import java.util.function.Function;

/**
 * Simple EventSource that publishes via injected EventToQueuePublisher.
 */
class TestPooledEventSource implements EventFlowService<PooledMessage> {
    private com.fluxtion.server.dispatch.EventToQueuePublisher<PooledMessage> publisher;
    private EventWrapStrategy wrapStrategy = EventWrapStrategy.SUBSCRIPTION_NOWRAP;
    public Function<PooledMessage, ?> dataMapper = Function.identity();

    @Override
    public void subscribe(EventSubscriptionKey<PooledMessage> eventSourceKey) { /* no-op for test */ }

    @Override
    public void unSubscribe(EventSubscriptionKey<PooledMessage> eventSourceKey) { /* no-op */ }

    @Override
    public void setEventToQueuePublisher(com.fluxtion.server.dispatch.EventToQueuePublisher<PooledMessage> targetQueue) {
        this.publisher = targetQueue;
        this.publisher.setEventWrapStrategy(wrapStrategy);
        this.publisher.setDataMapper(dataMapper);
    }

    @Override
    public void setEventWrapStrategy(EventWrapStrategy eventWrapStrategy) {
        this.wrapStrategy = eventWrapStrategy;
        if (publisher != null) {
            publisher.setEventWrapStrategy(eventWrapStrategy);
            publisher.setDataMapper(dataMapper);
        }
    }

    public void publish(PooledMessage msg) {
        publisher.publish(msg);
    }

    @Override
    public void setDataMapper(Function<PooledMessage, ?> dataMapper) {
        this.dataMapper = dataMapper;
    }
}
