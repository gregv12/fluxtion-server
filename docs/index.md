# Mongoose Server

Mongoose Server is a high‑performance, event‑driven server framework for building scalable, composable event processing
applications. It wires event sources, processors, sinks, and services, and manages their lifecycle and threading so you
can focus on business logic.

## Why Mongoose Server?

* Performance: Agent‑based concurrency with configurable idle strategies enables very high throughput and predictable
  latency.
* Ease of development: Compose processors and services, configure via YAML or Java, and get built‑in lifecycle and
  service injection.
* Plugin architecture: Clean extension points for event feeds, sinks, services, and dispatch strategies so you can
  tailor the runtime.
* Operational control: Admin commands, scheduling, logging/audit support, and dynamic registration make operations
  simpler.

## Documentation is organized into the following sections:

- Start with the Overview to learn concepts and architecture. [Overview](guide/overview.md)
- See Examples for quick hands-on guidance.
- See Plugins for advice on writing plugins.
- Use How-to guides for common tasks and extensions.

## Architecture and Threading model for internals.

- Threading model → [guide/threading-model.md](guide/threading-model.md)
- Architecture → [architecture/index.md](architecture/index.md)
- Event flow → [architecture/event-flow.md](architecture/event-flow.md)
- Sequence diagrams → [architecture/sequence-diagrams/index.md](architecture/sequence-diagrams/index.md)

If you find an issue or want to improve the docs, click “Edit this page” in the top right or open a PR on GitHub.
