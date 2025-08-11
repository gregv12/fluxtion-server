/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.event.NamedFeedEvent;
import com.fluxtion.runtime.event.NamedFeedEventImpl;
import com.fluxtion.runtime.event.ReplayRecord;
import lombok.*;
import lombok.extern.java.Log;

import java.util.ArrayList;
import java.util.Collections;
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

    private final List<NamedQueue> targetQueues = new CopyOnWriteArrayList<>();
    private final List<NamedFeedEvent<?>> eventLog = new ArrayList<>();
    private final String name;
    @Setter
    private boolean cacheEventLog;
    private long sequenceNumber = 0;
    @Setter
    private EventSource.EventWrapStrategy eventWrapStrategy = EventSource.EventWrapStrategy.SUBSCRIPTION_NOWRAP;
    @Setter
    private Function<T, ?> dataMapper = Function.identity();
    private int cacheReadPointer = 0;
    private final boolean logInfo = log.isLoggable(Level.FINE);

    public void addTargetQueue(OneToOneConcurrentArrayQueue<Object> targetQueue, String name) {
        NamedQueue namedQueue = new NamedQueue(name, targetQueue);
        if (log.isLoggable(Level.FINE)) {
            log.fine("adding a publisher queue:" + namedQueue);
        }
        if (!targetQueues.contains(namedQueue)) {
            targetQueues.add(namedQueue);
        }
    }

    @SuppressWarnings("unchecked")
    public void publish(T itemToPublish) {
        if (itemToPublish == null) {
            log.fine("itemToPublish is null");
            return;
        }

        var mappedItem = dataMapper.apply(itemToPublish);
        if (mappedItem == null) {
            log.fine("mappedItem is null");
            return;
        }

        sequenceNumber++;

        if (log.isLoggable(Level.FINE)) {
            log.fine("listenerCount:" + targetQueues.size() + " sequenceNumber:" + sequenceNumber + " publish:" + itemToPublish);
        }

        if (cacheEventLog) {
            dispatchCachedEventLog();
            NamedFeedEventImpl<Object> namedFeedEvent = new NamedFeedEventImpl<>(name)
                    .data(mappedItem)
                    .sequenceNumber(sequenceNumber);
            eventLog.add(namedFeedEvent);
        }

        cacheReadPointer++;
        dispatch(mappedItem);
    }

    public void cache(T itemToCache) {
        if (itemToCache == null) {
            log.fine("itemToCache is null");
            return;
        }

        var mappedItem = dataMapper.apply(itemToCache);
        if (mappedItem == null) {
            log.fine("mappedItem is null");
            return;
        }

        if (log.isLoggable(Level.FINE)) {
            log.fine("listenerCount:" + targetQueues.size() + " sequenceNumber:" + sequenceNumber + " publish:" + itemToCache);
        }
        sequenceNumber++;
        if (cacheEventLog) {
            NamedFeedEventImpl<Object> namedFeedEvent = new NamedFeedEventImpl<>(name)
                    .data(mappedItem)
                    .sequenceNumber(sequenceNumber);
            eventLog.add(namedFeedEvent);
        }
    }

    @SuppressWarnings("unchecked")
    public void publishReplay(ReplayRecord record) {
        if (record == null) {
            log.fine("itemToPublish is null");
            return;
        }
        if (log.isLoggable(Level.FINE)) {
            log.fine("listenerCount:" + targetQueues.size() + " publish:" + record);
        }

        for (int i = 0, targetQueuesSize = targetQueues.size(); i < targetQueuesSize; i++) {
            NamedQueue namedQueue = targetQueues.get(i);
            OneToOneConcurrentArrayQueue<Object> targetQueue = namedQueue.getTargetQueue();
            targetQueue.offer(record);
            if (log.isLoggable(Level.FINE)) {
                log.fine("queue:" + namedQueue.getName() + " size:" + targetQueue.size());
            }
        }
    }

    public void dispatchCachedEventLog() {
        if (cacheReadPointer < eventLog.size()) {
            if (log.isLoggable(Level.FINE)) {
                log.fine("publishing cached items cacheReadPointer:" + cacheReadPointer + " eventLog.size():" + eventLog.size());
            }
            //send updates
            for (int i = cacheReadPointer, eventLogSize = eventLog.size(); i < eventLogSize; i++) {
                NamedFeedEvent<?> cachedFeedEvent = eventLog.get(i);
                dispatch(cachedFeedEvent.data());
            }

        }
        cacheReadPointer = eventLog.size();
    }

    public List<NamedFeedEvent<?>> getEventLog() {
        return cacheEventLog ? eventLog : Collections.emptyList();
    }

    private void dispatch(Object mappedItem) {
        for (int i = 0, targetQueuesSize = targetQueues.size(); i < targetQueuesSize; i++) {
            NamedQueue namedQueue = targetQueues.get(i);
            OneToOneConcurrentArrayQueue<Object> targetQueue = namedQueue.getTargetQueue();
            switch (eventWrapStrategy) {
                case SUBSCRIPTION_NOWRAP, BROADCAST_NOWRAP -> writeToQueue(namedQueue, mappedItem);
                case SUBSCRIPTION_NAMED_EVENT, BROADCAST_NAMED_EVENT -> {
                    //TODO reduce memory pressure by using copy
                    NamedFeedEventImpl<Object> namedFeedEvent = new NamedFeedEventImpl<>(name)
                            .data(mappedItem)
                            .sequenceNumber(sequenceNumber);
                    writeToQueue(namedQueue, mappedItem);
                }
            }
            if (log.isLoggable(Level.FINE)) {
                log.fine("queue:" + namedQueue.getName() + " size:" + targetQueue.size());
            }
        }
    }

    private void writeToQueue(NamedQueue namedQueue, Object itemToPublish) {
        OneToOneConcurrentArrayQueue<Object> targetQueue = namedQueue.getTargetQueue();
        boolean eventNotificationNotReceived = false;
        long now = -1;
        while (!eventNotificationNotReceived) {
            eventNotificationNotReceived = targetQueue.offer(itemToPublish);
            if (!eventNotificationNotReceived) {
                if (now < 0) {
                    now = System.nanoTime();
                }
                java.lang.Thread.onSpinWait();
            }
        }
        if(logInfo & now > 1){
            long delta = System.nanoTime() - now;
            log.warning("spin wait took " + (delta / 1_000_000) + "ms queue:" + namedQueue.getName() + " size:" + targetQueue.size() );
        }
    }

    @Value
    public static class NamedQueue {
        String name;
        OneToOneConcurrentArrayQueue<Object> targetQueue;
    }
}
