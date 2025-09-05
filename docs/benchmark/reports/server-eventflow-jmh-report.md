# Server EventFlow JMH Benchmark Report

This page captures sample results for the end-to-end server event flow benchmark using object pooling.

Update this page with your environment and results after running the benchmark.

## Environment
- CPU: <fill-in>
- Cores/Threads: <fill-in>
- JVM: <fill-in>
- OS: <fill-in>
- Flags: `-Dthreads=<N> -Dwarmups=1 -Dmeas=3 -Dforks=1`

## Results (sample format)

| Benchmark                                           | Threads | Units   | Score  | Error | Notes                   |
|-----------------------------------------------------|---------|---------|--------|-------|-------------------------|
| publish_through_server                              | 8       | ops/ms  | 12345  | 1.2%  | zero-GC steady state    |
| publish_latencyAvg                                  | 8       | ns/op   | 450.0  | 1.5%  | avg per publish latency |

> Replace with your actual measured values.

## Notes
- Ensure annotation processing runs before executing JMH (`mvn -q test-compile`).
- On systems without explicit core pinning support, pinning is best-effort and silently ignored.
- Keep work in handler minimal to measure core pipeline overhead.
