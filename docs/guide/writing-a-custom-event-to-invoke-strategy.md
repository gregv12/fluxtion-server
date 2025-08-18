# How to write a custom EventToInvokeStrategy

This guide shows how to build and plug in your own EventToInvokeStrategy to control how events are dispatched from queues to StaticEventProcessor instances.

When to customize:
- Filter which processors can receive events
- Transform events before delivery
- Route or multiplex events differently than the default onEvent dispatch
- Provide custom handling for wall-clock timestamps (synthetic clock)

## 1) Choose a base: implement the interface or extend the helper

You can:
- Implement the low-level interface directly: com.fluxtion.server.service.EventToInvokeStrategy
- Or extend the convenience base class: com.fluxtion.server.dispatch.AbstractEventToInvocationStrategy

The helper already manages:
- Registration/deregistration of processors (thread-safe list)
- Per-dispatch ProcessorContext current-processor handling
- Synthetic clock wiring when you call processEvent(event, time)

With the helper, you only implement:
- protected void dispatchEvent(Object event, StaticEventProcessor eventProcessor)
- protected boolean isValidTarget(StaticEventProcessor eventProcessor)

## 2) Example: filter targets and transform events (strongly-typed callback)

The following example accepts only processors that implement a MarkerProcessor interface and uppercases String events before delivering them via a strongly-typed callback, not onEvent(Object):

```java
import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.server.dispatch.AbstractEventToInvocationStrategy;

public interface MarkerProcessor {
    void onString(String s);
}

public class UppercaseStringStrategy extends AbstractEventToInvocationStrategy {
    @Override
    protected void dispatchEvent(Object event, StaticEventProcessor eventProcessor) {
        if (event instanceof String s && eventProcessor instanceof MarkerProcessor marker) {
            marker.onString(s.toUpperCase());
        }
        // ignore non-String events or non-marker processors
    }

    @Override
    protected boolean isValidTarget(StaticEventProcessor eventProcessor) {
        return eventProcessor instanceof MarkerProcessor;
    }
}
```

Notes:
- Using an invoker strategy allows your event processors to be strongly typed (e.g., MarkerProcessor.onString), while the strategy takes responsibility for mapping inbound events to the correct callback. This reduces boilerplate and centralizes dispatch logic, which can make future maintenance easier.
- ProcessorContext is automatically set to the current target processor during dispatch. Inside the processor, you can call ProcessorContext.currentProcessor() if needed.
- If you call processEvent(event, time), AbstractEventToInvocationStrategy wires a synthetic clock into each target processor via setClockStrategy so that processors can use a provided time source.

## 3) Wire your strategy into the runtime

Register your strategy as a factory for a CallBackType. The ON_EVENT_CALL_BACK delivers raw events to processors.

Via EventFlowManager:
```java
EventFlowManager flow = new EventFlowManager();
flow.registerEventMapperFactory(UppercaseStringStrategy::new, CallBackType.ON_EVENT_CALL_BACK);

// Create an event source and subscribe a mapping agent
EventToQueuePublisher<Object> publisher = flow.registerEventSource("mySource", eventSource);
Agent subscriber = ...;
EventQueueToEventProcessor agent = flow.getMappingAgent(new EventSourceKey<>("mySource"), CallBackType.ON_EVENT_CALL_BACK, subscriber);

// Register processors
agent.registerProcessor(myProcessorImplementingMarker);

// Publish and drive the agent
publisher.publish("hello");
agent.doWork(); // drains the queue and invokes your strategy
```

Via FluxtionServer (if you bootstrap a server):
```java
FluxtionServer server = FluxtionServer.bootServer(appConfig);
server.registerEventMapperFactory(UppercaseStringStrategy::new, CallBackType.ON_EVENT_CALL_BACK);
```

## 4) Testing tips

- Use a RecordingProcessor that implements StaticEventProcessor (and your marker if filtering) to capture received events.
- Assert listenerCount() after registering processors to ensure your isValidTarget filter works.
- Publish test events through EventToQueuePublisher and call agent.doWork() to force processing.
- If you need timestamp semantics, publish a ReplayRecord through the EventToQueuePublisher or use processEvent(event, time) inside a controlled driver and have your processor consult its clock strategy.

See src/test/java/com/fluxtion/server/dispatch/CustomEventToInvokeStrategyTest.java for a complete, runnable example.
