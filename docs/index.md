# Mongoose Server

Mongoose Server is a high‑performance, event‑driven framework for building scalable event processing applications fast.
It wires sources, processors, sinks, and services for you, handling threading and lifecycle behind the scenes, so you
can
focus on business logic.

Its plugin architecture lets you assemble pipelines from reusable components, including third‑party plugins from the
broader ecosystem. You can mix and match existing sources, transforms, and sinks, add your own logic where needed, and
get to a working system quickly without reinventing common building blocks.

## Why Mongoose Server?

* Process multiple event feeds: Merge data from many real-time sources and process in a single-threaded application
  handler.
* Performance: Agent‑based concurrency with configurable idle strategies enables very high throughput and predictable
  latency.
* ZeroGc: Built in object pooling to support zero gc event processing.
* Ease of development: Compose processors and services, configured via YAML or Java with built in service injection.
* Plugin ecosystem: community plugins, including support for Kafka, aeron, chronicle, and more.
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
