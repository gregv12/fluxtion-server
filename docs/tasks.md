# Fluxtion Server Improvement Tasks

The following is an actionable, logically ordered checklist of improvements covering architecture, code quality, testing, performance, observability, security, documentation, tooling, and CI/CD. Each item is scoped to be independently completable and is phrased to enable clear acceptance.

## 1. Architecture & Module Boundaries
1. [ ] Document high-level architecture: event sources → dispatch → processors → services → admin/scheduler; add/update a C4 model diagram in docs/index.md and docs/guide. Acceptance: diagram + 1–2 page overview.
2. [ ] Define clear module boundaries and ownership for packages under com.fluxtion.server.* (dispatch, dutycycle, scheduler, admin, config). Acceptance: module boundary doc + package-level javadocs.
3. [ ] Establish extension points and SPIs for EventSource, EventToInvokeStrategy, and Service injection (documenting lifecycle hooks, threading, and error handling). Acceptance: docs/guide/plugin_extension_architecture.md updated with SPI tables.
4. [ ] Introduce architectural decision records (ADRs) in docs/adr/ with an index; backfill 3–5 key decisions (queueing model, idle strategies, logging approach, scheduling wheel). Acceptance: ADRs committed.
5. [ ] Create a versioning and compatibility policy for public APIs (semantic versioning, deprecation window). Acceptance: VERSIONING.md.

## 2. Concurrency, Dispatch, and Backpressure
6. [ ] Review EventToQueuePublisher queueing semantics: ensure writeToQueue handles full queues (drop policy, block, retry with backoff) consistently and is configurable. Acceptance: policy enum + tests.
7. [ ] Add backpressure signaling from queues to event sources (e.g., feedback interface in EventSource). Acceptance: interface + example implementation + test.
8. [ ] Audit thread-safety in CopyOnWriteArrayList usage for targetQueues and eventLog; replace with appropriate concurrent structures or guards where needed. Acceptance: concurrency review notes + code changes + tests.
9. [ ] Make eventWrapStrategy behavior explicit and covered by tests for all strategies (NOWRAP, SUBSCRIPTION, REPLAY). Acceptance: parameterized tests demonstrating correct wrapping.
10. [ ] Ensure memory visibility guarantees for sequenceNumber and cacheReadPointer (volatile/AtomicLong or synchronized access) where required. Acceptance: concurrency tests (JCstress-like or deterministic unit tests).

## 3. Configuration and Bootstrapping
11. [ ] Validate EventProcessorGroupConfig builder null-handling (eventHandlers map initialization) and immutability of built config. Acceptance: defensive copies + tests.
12. [ ] Provide YAML schema or example set for AppConfig with validation errors surfaced at boot (SnakeYAML + schema/constraints). Acceptance: validation step with clear error messages.
13. [ ] Add config for idle strategies per group with sensible defaults and doc examples. Acceptance: docs + example YAML + tests.

## 4. Error Handling and Resilience
14. [ ] Standardize error handling: define ErrorHandler policy per component (dispatch, processors, services). Acceptance: central error policy + integration tests.
15. [ ] Implement retry and dead-letter strategy for failed event processing (configurable). Acceptance: dead-letter interface + persistence stub + tests.
16. [ ] Ensure graceful shutdown: drain queues or snapshot state; verify LifecycleManager interactions. Acceptance: shutdown tests verifying no lost in-flight events under policy.

## 5. Observability and Diagnostics
17. [ ] Introduce structured logging (key-value) around critical paths (publish, dispatch, start/stop). Acceptance: consistent log format + togglable verbosity.
18. [ ] Add lightweight metrics counters (published, dropped, retried, processing latency) with pluggable sink (JMX/console). Acceptance: Metrics facade + default sink + tests.
19. [ ] Provide correlation/trace IDs through event path; propagate via NamedFeedEvent or context. Acceptance: context propagation + test asserting presence across components.

## 6. Performance and Benchmarking
20. [ ] Add microbenchmarks for EventToQueuePublisher (mapping cost, queue throughput) using JMH. Acceptance: jmh module + baseline report.
21. [ ] Profile dutycycle agents under load; identify hotspots and propose tuning (idle strategy, batching). Acceptance: profiling notes + tuning recommendations.
22. [ ] Evaluate and document memory footprint of eventLog caching; add size limits and eviction policy. Acceptance: configurable limits + tests.

## 7. API and Type Safety
23. [ ] Tighten generics in EventToQueuePublisher (avoid raw Object where feasible) and validate dataMapper types at registration. Acceptance: compile-time safety + tests.
24. [ ] Add nullability annotations (e.g., JetBrains or JSR-305) across public APIs. Acceptance: annotations + static analysis passing.
25. [ ] Provide clear equals/hashCode/identity semantics for NamedQueue; ensure uniqueness by name vs instance. Acceptance: explicit contract + tests.

## 8. Testing Strategy
26. [ ] Establish unit test conventions and coverage targets; enable code coverage reporting (JaCoCo). Acceptance: coverage pipeline + threshold.
27. [ ] Add tests for AppConfig loading paths (file, reader) and error scenarios in FluxtionServer.bootServer. Acceptance: tests passing.
28. [ ] Create integration tests simulating multiple event sources feeding processors with backpressure. Acceptance: green integration tests.
29. [ ] Add concurrency tests verifying no lost/duplicated events under contention. Acceptance: deterministic tests.

## 9. Security and Reliability
30. [ ] Review and restrict reflective operations or dynamic loading (plugin catalog) with allowlists. Acceptance: documented allowlist + enforcement.
31. [ ] Sanitize and validate external inputs (admin commands, configs). Acceptance: validation layer + tests.
32. [ ] Add dependency vulnerability scanning and update policy in CI (OWASP/OSS Index). Acceptance: CI job + badge.

## 10. Documentation and Examples
33. [ ] Consolidate and cross-link guides (read-strategy, file/memory feeds, plugin catalog) from docs/index.md. Acceptance: navigation updated.
34. [ ] Add runnable end-to-end example with two processors and a scheduler, including how to run locally. Acceptance: docs/run_local_guide.md updated + example sources.
35. [ ] Provide upgrade notes for next minor release (breaking changes, deprecations). Acceptance: UPGRADE_NOTES.md.

## 11. Tooling and Build
36. [ ] Enforce code style (Spotless/Checkstyle) aligned with existing conventions. Acceptance: build fails on violations.
37. [ ] Add Error Prone or SpotBugs to catch concurrency and nullability issues early. Acceptance: CI integrated checks.
38. [ ] Speed up Maven build with sensible defaults (parallel, incremental), cache settings in CI. Acceptance: documented settings + CI config.

## 12. CI/CD and Release
39. [ ] Expand GitHub Actions to run unit + integration tests, coverage upload, and docs build preview. Acceptance: passing workflow with artifacts.
40. [ ] Automate release notes generation and tagging. Acceptance: release workflow + changelog generation.

## 13. Data/State Management
41. [ ] Define replay and recovery semantics using ReplayRecord: ordering, idempotency, and dedup behavior. Acceptance: spec + tests.
42. [ ] Add snapshotting hooks for processors and sources to enable fast restarts. Acceptance: interfaces + sample impl + tests.

## 14. Deprecations and Cleanup
43. [ ] Identify and deprecate unused public methods/APIs; mark with @Deprecated and document alternatives. Acceptance: deprecation list + docs.
44. [ ] Remove or gate verbose logging in hot paths (guarded by log level checks). Acceptance: audit complete + PR.

## 15. Developer Experience
45. [ ] Provide a quick-start developer script or Makefile targets for common tasks (build, test, run example). Acceptance: README section + scripts.
46. [ ] Add CONTRIBUTING.md with PR guidelines, branch naming, and review checklist. Acceptance: file published.

---

Notes:
- Prioritize sections 1–4 and 6–8 for immediate reliability/throughput wins.
- Each task should be completed with accompanying tests and documentation updates where applicable.
