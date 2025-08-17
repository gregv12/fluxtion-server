# Fluxtion Server

Fluxtion Server is a high‑performance, event‑driven server framework for building scalable, composable event processing
applications. It wires event sources, processors, sinks, and services, and manages their lifecycle and threading so you
can focus on business logic.

## Why Fluxtion Server?

- Performance: Agent‑based concurrency with configurable idle strategies enables very high throughput and predictable
  latency.
- Ease of development: Compose processors and services, configure via YAML or Java, and get built‑in lifecycle and
  service injection.
- Plugin architecture: Clean extension points for event feeds, sinks, services, and dispatch strategies so you can
  tailor the runtime.
- Operational control: Admin commands, scheduling, logging/audit support, and dynamic registration make operations
  simpler.

## Quick start

- Read the Detailed Overview for concepts, architecture, and examples: docs/guide/overview.md
- See the Architecture docs and sequence diagrams for internals: docs/architecture/index.md
- Check coding standards if you plan to contribute: docs/standards/coding-standards.md

Minimal bootstrap from code:

```java
FluxtionServer server = FluxtionServer.bootServer(appConfig, logRecordListener);
```

To configure with YAML, point the JVM at a config file:

```bash
java -Dfluxtionserver.config.file=path/to/config.yaml
```

## Documentation

- Detailed overview and usage: [docs/guide/overview.md](docs/guide/overview.md)
- Architecture overview and components: [docs/architecture/overview.md](docs/architecture/overview.md)
  and [docs/architecture/components.md](docs/architecture/components.md)
- Event flow: [docs/architecture/event-flow.md](docs/architecture/event-flow.md)
- Sequence diagrams: [docs/architecture/sequence-diagrams/index.md](docs/architecture/sequence-diagrams/index.md)
- Coding standards: [docs/standards/coding-standards.md](docs/standards/coding-standards.md)

## License

AGPL-3.0-only. See LICENSE for details.

