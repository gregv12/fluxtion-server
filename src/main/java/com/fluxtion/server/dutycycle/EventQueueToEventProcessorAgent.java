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
        Object event = inputQueue.poll();
        if (event != null) {
            if (event instanceof ReplayRecord replayRecord) {
                eventToInvokeStrategy.processEvent(replayRecord.getEvent(), replayRecord.getWallClockTime());
            } else if (event instanceof BroadcastEvent broadcastEvent) {
                eventToInvokeStrategy.processEvent(broadcastEvent.getEvent());
            } else {
                eventToInvokeStrategy.processEvent(event);
            }
            return 1;
        }
        return 0;
    }

    @Override
    public void onClose() {
        logger.info("onClose");
    }

    @Override
    public String roleName() {
        return name;
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
