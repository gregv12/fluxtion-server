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

- Start with the [Overview](guide/overview.md) to learn concepts and architecture. 
- See [Examples](guide/file-and-memory-feeds-example.md) for quick hands-on guidance.
- See [Plugins](plugin/writing-a-message-sink-plugin.md) for advice on writing plugins.
- Use [How-to guides](how-to/how-to-subscribing-to-named-event-feeds.md) for common tasks and extensions.

## Architecture and Threading model for internals.

- Threading model → [architecture/threading-model.md](architecture/threading-model.md)
- Architecture → [architecture/index.md](architecture/architecture_index.md)
- Event flow → [architecture/event-flow.md](architecture/event-flow.md)
- Sequence diagrams → [architecture/sequence-diagrams/index.md](architecture/sequence-diagrams/index.md)

If you find an issue or want to improve the docs, click “Edit this page” in the top right or open a PR on GitHub.
