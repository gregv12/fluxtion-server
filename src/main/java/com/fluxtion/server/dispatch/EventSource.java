/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.runtime.annotations.feature.Experimental;

import java.util.function.Function;

/**
 * Publishes events to {@link com.fluxtion.server.dispatch.EventToQueuePublisher}. Register an {@link EventSource} instance with {@link EventFlowManager}
 * to receive the target queue via the setEventToQueuePublisher callback method.
 *
 * @param <T>
 */
@SuppressWarnings("EmptyMethod")
@Experimental
public interface EventSource<T> {

    enum EventWrapStrategy {SUBSCRIPTION_NOWRAP, SUBSCRIPTION_NAMED_EVENT, BROADCAST_NOWRAP, BROADCAST_NAMED_EVENT}

    enum SlowConsumerStrategy {DISCONNECT, EXIT_PROCESS, BACKOFF}

    void subscribe(EventSubscriptionKey<T> eventSourceKey);

    void unSubscribe(EventSubscriptionKey<T> eventSourceKey);

    void setEventToQueuePublisher(EventToQueuePublisher<T> targetQueue);

    default void setEventWrapStrategy(EventWrapStrategy eventWrapStrategy) {
    }

    default void setSlowConsumerStrategy(EventSource.SlowConsumerStrategy slowConsumerStrategy) {
    }

    default void setDataMapper(Function<T, ?> dataMapper) {
    }
}
