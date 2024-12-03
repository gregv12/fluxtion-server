/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.event.NamedFeedEvent;
import com.fluxtion.runtime.event.ReplayRecord;
import lombok.*;
import lombok.extern.java.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Handles publishing events to internal dispatch queues, provides the functionality:
 * <ul>
 *     <li>Multiplexes a single event message to multiple queues</li>
 *     <li>Monitors and disconnects slow readers</li>
 * </ul>
 *
 * @param <T>
 */
@Experimental
@RequiredArgsConstructor
@ToString
@Log
@Getter
public class EventToQueuePublisher<T> {

    private final List<NamedQueue<T>> targetQueues = new CopyOnWriteArrayList<>();
    private final String name;
    @Setter
    private EventSource.EventWrapStrategy eventWrapStrategy = EventSource.EventWrapStrategy.SUBSCRIPTION_NOWRAP;
    @Setter
    private Function<T, ?> dataMapper = Function.identity();

    public void addTargetQueue(OneToOneConcurrentArrayQueue<T> targetQueue, String name) {
        NamedQueue<T> namedQueue = new NamedQueue<>(name, targetQueue);
        if (log.isLoggable(Level.FINE)) {
            log.fine("adding a publisher queue:" + namedQueue);
        }
        if (!targetQueues.contains(namedQueue)) {
            targetQueues.add(namedQueue);
        }
    }

    @SuppressWarnings("unchecked")
    public void publish(T itemToPublish) {
        if (log.isLoggable(Level.FINE)) {
            log.fine("listenerCount:" + targetQueues.size() + " publish:" + itemToPublish);
        }

        var mappedItem = dataMapper.apply(itemToPublish);

        for (int i = 0, targetQueuesSize = targetQueues.size(); i < targetQueuesSize; i++) {
            NamedQueue<T> namedQueue = targetQueues.get(i);
            OneToOneConcurrentArrayQueue<Object> targetQueue = (OneToOneConcurrentArrayQueue<Object>) namedQueue.getTargetQueue();
            switch (eventWrapStrategy) {
                case SUBSCRIPTION_NOWRAP, BROADCAST_NOWRAP -> targetQueue.offer(mappedItem);
                case SUBSCRIPTION_NAMED_EVENT, BROADCAST_NAMED_EVENT ->
                        targetQueue.offer(new NamedFeedEvent<>(name, null, mappedItem));
            }
            if (log.isLoggable(Level.FINE)) {
                log.fine("queue:" + namedQueue.getName() + " size:" + targetQueue.size());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void publishReplay(ReplayRecord record) {
        if (log.isLoggable(Level.FINE)) {
            log.fine("listenerCount:" + targetQueues.size() + " publish:" + record);
        }

        for (int i = 0, targetQueuesSize = targetQueues.size(); i < targetQueuesSize; i++) {
            NamedQueue<T> namedQueue = targetQueues.get(i);
            OneToOneConcurrentArrayQueue<Object> targetQueue = (OneToOneConcurrentArrayQueue<Object>) namedQueue.getTargetQueue();
            targetQueue.offer(record);
            if (log.isLoggable(Level.FINE)) {
                log.fine("queue:" + namedQueue.getName() + " size:" + targetQueue.size());
            }
        }
    }

    @Value
    public static class NamedQueue<T> {
        String name;
        OneToOneConcurrentArrayQueue<T> targetQueue;
    }
}
