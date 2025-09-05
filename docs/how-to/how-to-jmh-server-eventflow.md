# How-to: JMH benchmark for server event flow with object pooling

This guide describes a JMH micro-benchmark that measures the Fluxtion Server end-to-end event delivery path, from an in-VM `EventSource` to a handler, using the Object Pool service for zero-GC operation.

What this benchmark demonstrates:
- Event source publishes pooled messages (no per-op allocations).
- Handler consumes events via the standard event flow.
- Server is booted via the fluent `AppConfig` APIs.
- Agent threads are busy-spin and core pinned (best-effort) using `ThreadConfig.coreId`.

## Benchmark location

- Source: `src/test/java/com/fluxtion/server/benchmark/e2e/BenchmarkServerEventFlowJmh.java`
- Class: `com.fluxtion.server.benchmark.e2e.BenchmarkServerEventFlowJmh`

## Key design points

- Pooled message extends `BasePoolAware` so the framework manages references and return-to-pool automatically.
- The event source extends `AbstractEventSourceService` and acquires pooled instances from `ObjectPoolsRegistry` using `@ServiceRegistered` injection.
- No allocation per operation in the benchmark path: we acquire a pooled `Msg`, set a primitive field, and publish it.
- `EventFeedConfig.wrapWithNamedEvent(false)` and `broadcast(false)` for minimal wrapping overhead.
- Agent threads configured with `BusySpinIdleStrategy` and pinned to specific cores via `ThreadConfig` to minimize scheduling jitter.

## How to run

You can run JMH from your IDE or CLI. First ensure test annotation processing is done:

```bash
mvn -q test-compile
```

Then run with JMH main:

```bash
# From IDE, run the main() of BenchmarkServerEventFlowJmh
# Or from CLI (example with 8 threads)
mvn -q -Dtest=com.fluxtion.server.benchmark.e2e.BenchmarkServerEventFlowJmh test

# Or package and run JMH via its standard Main if you have a JMH runner setup
```

You can also execute the benchmark directly via the included `main()` method:

```bash
java -cp target/test-classes:target/classes:<deps> com.fluxtion.server.benchmark.e2e.BenchmarkServerEventFlowJmh
```

Common system properties for JMH:

- `-Dthreads=8` sets number of worker threads.
- `-Dwarmups=1`, `-Dmeas=3`, `-Dforks=1` control warmup, measurement iterations, and forks.

## Configuration snippets

The benchmark builds the server using fluent APIs:

```java
EventProcessorGroupConfig processors = EventProcessorGroupConfig.builder()
    .agentName("processor-agent")
    .put("counter", new EventProcessorConfig(handler))
    .build();

EventFeedConfig<?> feed = EventFeedConfig.builder()
    .instance(source)
    .name("benchPooledSource")
    .broadcast(false)
    .wrapWithNamedEvent(false)
    .agent("source-agent", new BusySpinIdleStrategy())
    .build();

AppConfig appConfig = AppConfig.builder()
    .addProcessorGroup(processors)
    .addEventFeed(feed)
    .addThread(ThreadConfig.builder().agentName("source-agent").idleStrategy(new BusySpinIdleStrategy()).coreId(0).build())
    .addThread(ThreadConfig.builder().agentName("processor-agent").idleStrategy(new BusySpinIdleStrategy()).coreId(1).build())
    .build();
```

## Zero-GC considerations

- The pooled `Msg` object is created during pool warm-up or the first few iterations; the steady-state benchmark operations do not allocate.
- The framework manages reference counting across queues and the handler; objects are returned to the pool at end-of-cycle.
- Avoid creating Strings or autoboxed types in the hot path; use primitive fields where possible.

## Report

A sample report page is provided here: [Server EventFlow JMH report](../benchmark/reports/server-eventflow-jmh-report.md). Update it with your run environment and results.
