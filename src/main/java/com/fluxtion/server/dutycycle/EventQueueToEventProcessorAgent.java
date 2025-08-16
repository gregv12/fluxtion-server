/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dutycycle;

import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.event.BroadcastEvent;
import com.fluxtion.runtime.event.ReplayRecord;
import com.fluxtion.server.dispatch.EventToInvokeStrategy;
import lombok.extern.java.Log;

import java.util.logging.Logger;


@Experimental
@Log
public class EventQueueToEventProcessorAgent implements EventQueueToEventProcessor {

    private final OneToOneConcurrentArrayQueue<?> inputQueue;
    private final EventToInvokeStrategy eventToInvokeStrategy;
    private final String name;
    private final Logger logger;
    private com.fluxtion.server.dispatch.RetryPolicy retryPolicy = com.fluxtion.server.dispatch.RetryPolicy.defaultProcessingPolicy();


    //TODO add an unsubscribe action that is called when there are no more listeners registered
    // should remove from the EventFLowManager
    public EventQueueToEventProcessorAgent(
            OneToOneConcurrentArrayQueue<?> inputQueue,
            EventToInvokeStrategy eventToInvokeStrategy,
            String name) {
        this.inputQueue = inputQueue;
        this.eventToInvokeStrategy = eventToInvokeStrategy;
        this.name = name;

        logger = Logger.getLogger("EventQueueToEventProcessorAgent." + name);
    }

    @Override
    public void onStart() {
        logger.info("start");
    }

    @Override
    public int doWork() {
        int processed = 0;
        // Batch up to a fixed number of events per tick to reduce per-event overhead
        final int batchLimit = 64;
        Object event;
        while (processed < batchLimit && (event = inputQueue.poll()) != null) {
            int attempt = 0;
            boolean done = false;
            Throwable lastError = null;
            while (!done) {
                try {
                    if (event instanceof ReplayRecord replayRecord) {
                        eventToInvokeStrategy.processEvent(replayRecord.getEvent(), replayRecord.getWallClockTime());
                    } else if (event instanceof BroadcastEvent broadcastEvent) {
                        eventToInvokeStrategy.processEvent(broadcastEvent.getEvent());
                    } else {
                        eventToInvokeStrategy.processEvent(event);
                    }
                    done = true;
                } catch (Throwable t) {
                    lastError = t;
                    attempt++;
                    String warnMsg = "event processing failed: agent=" + name +
                            ", attempt=" + attempt +
                            ", eventClass=" + (event == null ? "null" : event.getClass().getName()) +
                            ", event=" + String.valueOf(event) +
                            ", error=" + t.toString();
                    logger.warning(warnMsg);
                    com.fluxtion.server.service.error.ErrorReporting.report(
                            "EventQueueToEventProcessorAgent:" + name,
                            warnMsg,
                            t,
                            com.fluxtion.server.service.error.ErrorEvent.Severity.WARNING);
                    if (!retryPolicy.shouldRetry(t, attempt)) {
                        String errMsg = "dropping event after retries: agent=" + name +
                                ", attempts=" + attempt +
                                ", eventClass=" + (event == null ? "null" : event.getClass().getName()) +
                                ", event=" + String.valueOf(event) +
                                ", lastError=" + t.toString();
                        logger.severe(errMsg);
                        com.fluxtion.server.service.error.ErrorReporting.report(
                                "EventQueueToEventProcessorAgent:" + name,
                                errMsg,
                                t,
                                com.fluxtion.server.service.error.ErrorEvent.Severity.ERROR);
                        break;
                    }
                    retryPolicy.backoff(attempt);
                }
            }
            // Count it as processed even if dropped to avoid infinite loops
            processed++;
        }
        return processed;
    }

    @Override
    public void onClose() {
        logger.info("onClose");
    }

    @Override
    public String roleName() {
        return name;
    }

    /** Configure the retry policy for processing events. */
    public EventQueueToEventProcessorAgent withRetryPolicy(com.fluxtion.server.dispatch.RetryPolicy retryPolicy) {
        if (retryPolicy != null) {
            this.retryPolicy = retryPolicy;
        }
        return this;
    }

    @Override
    public int registerProcessor(StaticEventProcessor eventProcessor) {
        logger.info("registerProcessor: " + eventProcessor);
        eventToInvokeStrategy.registerProcessor(eventProcessor);
        logger.info("listener count:" + listenerCount());
        return listenerCount();
    }

    @Override
    public int deregisterProcessor(StaticEventProcessor eventProcessor) {
        logger.info("deregisterProcessor: " + eventProcessor);
        eventToInvokeStrategy.deregisterProcessor(eventProcessor);
        //TODO when the listener count is < 1 then run the unsubscribe action
        return listenerCount();
    }

    @Override
    public int listenerCount() {
        return eventToInvokeStrategy.listenerCount();
    }
}
