package com.fluxtion.server.dispatch;

import com.fluxtion.runtime.annotations.Start;
import com.fluxtion.server.service.AbstractAgentHostedEventSourceService;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

public class HeartBeatEventFeed extends AbstractAgentHostedEventSourceService<HeartbeatEvent> {
    private HeartbeatEvent heartbeatEvent = new HeartbeatEvent();
    private long publishTime = -1;
    
    // Add counters for message rate tracking
    private int messageCount = 0;
    private long lastPrintTime = System.currentTimeMillis();

    public HeartBeatEventFeed() {
        super("HeartBeatService2");
    }

    @Start
    public void start() {
//        heartbeat();
    }

    private void heartbeat() {
        scheduler.scheduleAfterDelay(1, this::heartbeat);
        heartbeatEvent.setTimestamp(System.nanoTime());
        System.out.println("publish");
        output.publish(heartbeatEvent);
    }

    @Override
    public int doWork() throws Exception {
        long currentNanoTime = System.nanoTime();
        if (currentNanoTime - publishTime > 450) {
            publishTime = currentNanoTime;
            heartbeatEvent.setTimestamp(System.nanoTime());
            output.publish(heartbeatEvent);

            // Increment message counter
            messageCount++;

            // Print rate every second
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastPrintTime >= 1000) {  // Check if 1 second has passed
                System.out.printf("Heartbeat messages per second: %d%n", messageCount);
                messageCount = 0;
                lastPrintTime = currentTime;
            }
            return 1;
        }
        return 0;
    }
}