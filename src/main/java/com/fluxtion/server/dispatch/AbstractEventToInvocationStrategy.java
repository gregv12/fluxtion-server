/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.annotations.feature.Experimental;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Abstract class to simplify create an EventToInvokeStrategy, by implementing two methods:
 *
 * <ul>
 *     <li>isValidTarget - is an eventProcessor a suitable target for callbacks</li>
 *     <li>dispatchEvent - process the event and dispatch to target eventProcessor's</li>
 * </ul>
 */
@Experimental
public abstract class AbstractEventToInvocationStrategy implements EventToInvokeStrategy {

    protected final List<StaticEventProcessor> eventProcessorSinks = new CopyOnWriteArrayList<>();
    protected static final Map<StaticEventProcessor, AtomicLong> syntheticClocks = new ConcurrentHashMap<>();

    @Override
    public void processEvent(Object event) {
        for (int i = 0, targetQueuesSize = eventProcessorSinks.size(); i < targetQueuesSize; i++) {
            StaticEventProcessor eventProcessor = eventProcessorSinks.get(i);
            com.fluxtion.server.dispatch.EventFlowManager.setCurrentProcessor(eventProcessor);
            dispatchEvent(event, eventProcessor);
            EventFlowManager.removeCurrentProcessor();
        }
    }

    @Override
    public void processEvent(Object event, long time) {
        for (int i = 0, targetQueuesSize = eventProcessorSinks.size(); i < targetQueuesSize; i++) {
            StaticEventProcessor eventProcessor = eventProcessorSinks.get(i);
            syntheticClocks.computeIfAbsent(eventProcessor, k -> {
                AtomicLong atomicLong = new AtomicLong();
                eventProcessor.setClockStrategy(atomicLong::get);
                return atomicLong;
            }).set(time);
        }

        processEvent(event);
    }

    /**
     * Map the event to a callback invocation on the supplied eventProcessor
     *
     * @param event          the incoming event to map to a callback method
     * @param eventProcessor the target of the callback method
     */
    abstract protected void dispatchEvent(Object event, StaticEventProcessor eventProcessor);

    @Override
    public void registerProcessor(StaticEventProcessor eventProcessor) {
        if (isValidTarget(eventProcessor) && !eventProcessorSinks.contains(eventProcessor)) {
            eventProcessorSinks.add(eventProcessor);
        }
    }

    /**
     * Return true if the eventProcessor is a valid target for receiving callbacks from this invocation strategy.
     *
     * @param eventProcessor the potential target of this invocation strategy
     * @return is a valid target
     */
    abstract protected boolean isValidTarget(StaticEventProcessor eventProcessor);

    @Override
    public void deregisterProcessor(StaticEventProcessor eventProcessor) {
        eventProcessorSinks.remove(eventProcessor);
    }

    @Override
    public int listenerCount() {
        return eventProcessorSinks.size();
    }
}
