# How-to: Measure server latency with HdrHistogram

This guide shows how to measure end-to-end latency through the Fluxtion Server using the HdrHistogram library.

Key points:
- The server is booted using the fluent AppConfig APIs.
- Event publishing uses the Object Pool (ObjectPoolsRegistry) to avoid per-operation allocations.
- Agent threads use BusySpin idle strategy and are core pinned (best-effort) using ThreadConfig.coreId to reduce jitter.
- Latency is measured from publish-time (nanoTime) to handler receipt and recorded in an HdrHistogram.

## Test location

- Source: `src/test/java/com/fluxtion/server/benchmark/hdr/ServerLatencyHdrHistogramTest.java`
- Class: `com.fluxtion.server.benchmark.hdr.ServerLatencyHdrHistogramTest`

## How it works

- A pooled `TimedMsg` extends `BasePoolAware` and carries a `sendNano` timestamp.
- A `PooledEventSource` acquires `TimedMsg` from the pool, sets the timestamp just before publishing, and publishes into the server flow.
- A `LatencyHandler` records the end-to-end latency upon receipt into an HdrHistogram.
- The test warms up, measures a configurable number of messages, and writes a markdown report into `docs/benchmark/reports/server-latency-hdrhistogram.md`.

## Running the test

From the project root:

```bash
# Compile tests
mvn -q test-compile

# Run the single test class
mvn -q -Dtest=com.fluxtion.server.benchmark.hdr.ServerLatencyHdrHistogramTest test
```

On success, a report is generated at:

```
docs/benchmark/reports/server-latency-hdrhistogram.md
```

This report includes summary statistics and the percentile distribution printed by HdrHistogram.

## Configuration aspects

- Object Pool: The event source uses `ObjectPoolsRegistry.getOrCreate` to obtain the pool for `TimedMsg`, ensuring zero-GC publishes under steady state.
- Core pinning: ThreadConfig is used to associate agent names with core IDs (0 and 1 in the test). On systems that do not support pinning, this is best-effort and safely ignored.
- Wrapping: The event feed is configured with `wrapWithNamedEvent(false)` and `broadcast(false)` to minimize wrapping overhead.

## Notes

- HdrHistogram records values (ns) in a high dynamic range with fixed memory overhead; we configure a 1s highestTrackableValue and 3 significant digits in the test.
- Ensure your system has available CPU cores for pinning to have effect. If not, performance is still measured without pinning.
