# How-to: Use HandlerPipe for in-VM communication between handlers

HandlerPipe is a lightweight, in-VM pipe for sending messages from one handler (or service) to other handlers via Fluxtion's event flow, without external IO.

It couples:
- a publish-side MessageSink (sink()) that you call to send data, and
- a receive-side InMemoryEventSource (getSource()) that integrates with the event flow, allowing processors to subscribe.

## When to use
- You want handlers to talk to each other inside the same JVM without setting up external transports.
- You want lifecycle-aware dispatch: cache events before startComplete and replay them once the system is ready.
- You want to reuse Fluxtion’s subscription, wrapping, and data-mapping features.

## Quick start

```java
// Create a pipe for a logical feed name
HandlerPipe<String> pipe = HandlerPipe.<String>of("ordersFeed").cacheEventLog(true);

// Wire the receive-side into your server configuration (pseudo-code):
AppConfig cfg = new AppConfig();
cfg.addService("ordersFeedService", pipe.getSource());

// In your processor(s), subscribe to the source by service name
pipe.getSource().subscribe(); // typically invoked during composition/registration

// Publish from anywhere within the JVM
pipe.sink().accept("order-123-created");
```

Notes:
- cacheEventLog(true) will cache any events published before startComplete and replay them automatically when the source calls startComplete().
- You can customize data mapping and wrapping like other EventSource services.

## Lifecycle semantics
HandlerPipe delegates lifecycle to InMemoryEventSource:
- start(): If cacheEventLog is true, publishes are cached (not dispatched).
- startComplete(): Cached events are replayed to subscribers, subsequent publishes dispatch immediately.

You can still push items before start() using pipe.sink().accept(...); they will be cached if caching is enabled and replayed later.

## Controlling wrapping and mapping

- Wrapping: choose how events are wrapped for subscribers.
```java
HandlerPipe<String> pipe = HandlerPipe.of("myFeed", EventSource.EventWrapStrategy.SUBSCRIPTION_NOWRAP);
```

- Data mapping: transform outgoing items before dispatch.
```java
pipe.dataMapper((String s) -> s.toUpperCase());
```

## Testing and local observation
To observe dispatches without a full server, attach a queue to the publisher used by the source. In tests, we use EventToQueuePublisher and a OneToOneConcurrentArrayQueue:

```java
HandlerPipe<String> pipe = HandlerPipe.<String>of("handlerPipeFeed").cacheEventLog(true);
EventToQueuePublisher<String> eventToQueue = new EventToQueuePublisher<>("handlerPipeFeed");
OneToOneConcurrentArrayQueue<Object> targetQueue = new OneToOneConcurrentArrayQueue<>(128);
eventToQueue.addTargetQueue(targetQueue, "outputQueue");
pipe.getSource().setOutput(eventToQueue); // test hook on source for injection

pipe.getSource().start();
pipe.sink().accept("a");
pipe.sink().accept("b");

// No items dispatched until startComplete when caching
targetQueue.drainTo(new ArrayList<>(), 100); // empty

pipe.getSource().startComplete();
// Now queue drains ["a", "b"]
```

For simplistic in-memory collection of published values, consider InMemoryMessageSink. You can replace the default sink by wrapping or delegating to pipe.sink().

## Tips
- Use a descriptive feed name; processors subscribe by service name.
- Prefer small, concise data mappers on the source side when transforming events.
- For backpressure/slow-consumer concerns, see EventToQueuePublisher settings (wrapping, logging). The pipe uses the same underlying publisher infrastructure via the source.
