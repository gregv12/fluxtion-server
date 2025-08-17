/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.service;

import com.fluxtion.server.dispatch.EventToQueuePublisher;

import java.util.function.Function;

/**
 * Interface representing a source of events that supports subscription management
 * and event delivery customization. Defines methods to subscribe and unsubscribe
 * consumers of events, as well as to configure event handling strategies.
 *
 * @param <T> the type of event data managed by the event source
 */
@SuppressWarnings("EmptyMethod")
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
