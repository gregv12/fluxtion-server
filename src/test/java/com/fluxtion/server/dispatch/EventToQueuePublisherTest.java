/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.runtime.event.NamedFeedEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EventToQueuePublisherTest {

    @Test
    @Disabled
    public void testPublishToConsole() {
        EventToQueuePublisher<String> eventToQueue = new EventToQueuePublisher<>("myQueue");
        eventToQueue.setCacheEventLog(true);

        OneToOneConcurrentArrayQueue<String> targetQueue = new OneToOneConcurrentArrayQueue<>(100);
        eventToQueue.addTargetQueue(targetQueue, "outputQueue");

        eventToQueue.publish("A");

        System.out.println("----------- cache start --------------");
        eventToQueue.cache("cache-1");
        eventToQueue.cache("cache-2");
        System.out.println("----------- cache end --------------");

        targetQueue.drain(System.out::println);

        System.out.println("----------- publish B --------------");
        eventToQueue.publish("B");
        targetQueue.drain(System.out::println);

        System.out.println("----------- publish C --------------");
        eventToQueue.publish("C");
        targetQueue.drain(System.out::println);

        System.out.println("----------- event log --------------");
        eventToQueue.getEventLog().forEach(System.out::println);
    }

    @Test
    public void testPublish() {
        EventToQueuePublisher<String> eventToQueue = new EventToQueuePublisher<>("myQueue");
        eventToQueue.setCacheEventLog(true);
        ArrayList<String> actual = new ArrayList<>();

        OneToOneConcurrentArrayQueue<String> targetQueue = new OneToOneConcurrentArrayQueue<>(100);
        eventToQueue.addTargetQueue(targetQueue, "outputQueue");

        eventToQueue.publish("A");

//       ----------- cache start --------------
        eventToQueue.cache("cache-1");
        eventToQueue.cache("cache-2");
//      ----------- cache end --------------

        targetQueue.drainTo(actual, 100);
        Assertions.assertIterableEquals(List.of("A"), actual);

//      ----------- publish B --------------
        actual.clear();
        eventToQueue.publish("B");
        targetQueue.drainTo(actual, 100);
        Assertions.assertIterableEquals(List.of("cache-1", "cache-2", "B"), actual);

//      ----------- publish C --------------
        actual.clear();
        eventToQueue.publish("C");
        targetQueue.drainTo(actual, 100);
        Assertions.assertIterableEquals(List.of("C"), actual);

//      ----------- event log --------------
        Assertions.assertIterableEquals(
                List.of("A", "cache-1", "cache-2", "B", "C"),
                eventToQueue.getEventLog().stream().map(NamedFeedEvent::data).collect(Collectors.toList()));
    }
}