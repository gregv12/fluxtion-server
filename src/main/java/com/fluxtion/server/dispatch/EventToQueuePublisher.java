/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
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
 * Handles publishing events to dag dispatch queues, provides the functionality:
 * <ul>
 *     <li>Multiplexes a single event message to multiple queues</li>
 *     <li>Monitors and disconnects slow readers</li>
 * </ul>
 *
 * @param <T>
 */
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

    public void publish(T itemToPublish) {
        if (itemToPublish == null) {
            log.fine("itemToPublish is null");
            return;
        }

        Object mappedItem = mapItemSafely(itemToPublish, "publish");
        if (mappedItem == null) {
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

        Object mappedItem = mapItemSafely(itemToCache, "cache");
        if (mappedItem == null) {
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
        if (!cacheEventLog) {
            return Collections.emptyList();
        }
        // Return a thread-safe snapshot for concurrent readers while maintaining
        // single-writer performance characteristics for the underlying eventLog.
        return Collections.unmodifiableList(new ArrayList<>(eventLog));
    }

    private Object mapItemSafely(T item, String context) {
        try {
            Object mapped = dataMapper.apply(item);
            if (mapped == null) {
                log.fine("mappedItem is null");
            }
            return mapped;
        } catch (Throwable t) {
            log.severe("data mapping (" + context + ") failed: publisher=" + name + ", nextSequenceNumber=" + (sequenceNumber + 1) + ", item=" + String.valueOf(item) + ", error=" + t);
            com.fluxtion.server.service.error.ErrorReporting.report(
                    "EventToQueuePublisher:" + name,
                    "data mapping failed for " + context + ": nextSeq=" + (sequenceNumber + 1) + ", item=" + String.valueOf(item),
                    t,
                    com.fluxtion.server.service.error.ErrorEvent.Severity.ERROR);
            return null;
        }
    }

    private void dispatch(Object mappedItem) {
        for (int i = 0, targetQueuesSize = targetQueues.size(); i < targetQueuesSize; i++) {
            NamedQueue namedQueue = targetQueues.get(i);
            OneToOneConcurrentArrayQueue<Object> targetQueue = namedQueue.getTargetQueue();
            switch (eventWrapStrategy) {
                case SUBSCRIPTION_NOWRAP, BROADCAST_NOWRAP -> writeToQueue(namedQueue, mappedItem);
                case SUBSCRIPTION_NAMED_EVENT, BROADCAST_NAMED_EVENT -> {
                    //TODO reduce memory pressure by using copy or a recyclable wrapper if needed
                    NamedFeedEventImpl<Object> namedFeedEvent = new NamedFeedEventImpl<>(name)
                            .data(mappedItem)
                            .sequenceNumber(sequenceNumber);
                    writeToQueue(namedQueue, namedFeedEvent);
                }
            }
            if (log.isLoggable(Level.FINE)) {
                log.fine("queue:" + namedQueue.getName() + " size:" + targetQueue.size());
            }
        }
    }

    private void writeToQueue(NamedQueue namedQueue, Object itemToPublish) {
        OneToOneConcurrentArrayQueue<Object> targetQueue = namedQueue.getTargetQueue();
        boolean offered = false;
        long startNs = -1;
        final long maxSpinNs = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(2); // bound spin to avoid publisher timeouts under contention
        try {
            while (!offered) {
                offered = targetQueue.offer(itemToPublish);
                if (!offered) {
                    if (startNs < 0) {
                        startNs = System.nanoTime();
                    } else if (System.nanoTime() - startNs > maxSpinNs) {
                        // give up for this queue to avoid blocking publishers; event remains in eventLog
                        if (logInfo) {
                            log.warning("dropping publish to slow/contended queue: " + namedQueue.getName() + 
                                    " after ~" + ((System.nanoTime() - startNs) / 1_000_000) + "ms seq:" + sequenceNumber + 
                                    " queueSize:" + targetQueue.size());
                        }
                        return;
                    }
                    java.lang.Thread.onSpinWait();
                }
            }
        } catch (Throwable t) {
            log.severe("queue write failed: publisher=" + name + ", queue=" + namedQueue.getName() + ", seq=" + sequenceNumber + ", item=" + String.valueOf(itemToPublish) + ", error=" + t);
            com.fluxtion.server.service.error.ErrorReporting.report(
                    "EventToQueuePublisher:" + name,
                    "queue write failed: queue=" + namedQueue.getName() + ", seq=" + sequenceNumber + ", item=" + String.valueOf(itemToPublish),
                    t,
                    com.fluxtion.server.service.error.ErrorEvent.Severity.CRITICAL);
            throw new com.fluxtion.server.exception.QueuePublishException("Failed to write to queue '" + namedQueue.getName() + "' for publisher '" + name + "'", t);
        }
        if (logInfo && startNs > 1) {
            long delta = System.nanoTime() - startNs;
            log.warning("spin wait took " + (delta / 1_000_000) + "ms queue:" + namedQueue.getName() + " size:" + targetQueue.size());
        }
    }

    @Value
    public static class NamedQueue {
        String name;
        OneToOneConcurrentArrayQueue<Object> targetQueue;
    }

    public void removeTargetQueueByName(String queueName) {
        if (queueName == null) {
            return;
        }
        targetQueues.removeIf(q -> queueName.equals(q.getName()));
    }
}
