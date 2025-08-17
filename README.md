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

- Read the Detailed Overview for concepts, architecture: [overview](docs/guide/overview.md)
- For sample code see: [example](docs/guide/file-and-memory-feeds-example.md)
- For writing plugins see:
    - [event sink plugin](docs/guide/writing-a-message-sink-plugin.md)
    - [event source plugin](docs/guide/writing-an-event-source-plugin.md)
    - [service plugin](docs/guide/writing-a-service-plugin.md)
    - [admin command](docs/guide/writing-an-admin-command.md)
- How-to guides:
    - [using the scheduler service](docs/guide/using-the-scheduler-service.md)
- For internals see:
    - [Architecture docs](docs/architecture/index.md)
    - [Sequence diagrams](docs/architecture/sequence-diagrams/index.md)

Minimal bootstrap from code:

```java
EventProcessorGroupConfig processorGroup = EventProcessorGroupConfig.builder()
        .agentName("processor-agent")
        .put("example-processor", new EventProcessorConfig(new BuilderApiExampleHandler()))
        .build();

EventFeedConfig<?> fileFeedCfg = EventFeedConfig.builder()
        .instance(fileSource)
        .name("fileFeed")
        .broadcast(true)
        .agent("file-source-agent", new BusySpinIdleStrategy())
        .build();

EventFeedConfig<?> memFeedCfg = EventFeedConfig.builder()
        .instance(inMemSource)
        .name("inMemFeed")
        .broadcast(true)
        .agent("memory-source-agent", new BusySpinIdleStrategy())
        .build();

EventSinkConfig<FileMessageSink> sinkCfg = EventSinkConfig.<FileMessageSink>builder()
        .instance(fileSink)
        .name("fileSink")
        .build();

AppConfig appConfig = AppConfig.builder()
        .addProcessorGroup(processorGroup)
        .addEventFeed(fileFeedCfg)
        .addEventFeed(memFeedCfg)
        .addEventSink(sinkCfg)
        .build();

FluxtionServer server = FluxtionServer.bootServer(appConfig, logRecordListener);
```

## Key Concepts

### Core Components

- Event Feeds: Sources that generate events into the system, such as network connections, files, or message queues
- Event Sinks: Destinations for processed events, like databases, message brokers, or external services
- Event Processors: Components containing business logic that transform and process events
- Services: Reusable components providing shared functionality across processors such as caching, persistence, or
  authentication
- Dispatch Strategies: Components that determine how events are dispatched to sinks and processors

### System Features

- Lifecycle Management: Handles startup/shutdown sequences and dependencies between components
- Plugin Architecture: Extensible framework for adding new event sources, sinks, services, and processors
- Agent Threading: Single-threaded event processing per agent with configurable idle strategies for optimal performance

## Documentation

- Detailed overview and usage: [docs/guide/overview.md](docs/guide/overview.md)
- Architecture: [docs/architecture/index.md](docs/architecture/index.md)
- Sequence diagrams: [docs/architecture/sequence-diagrams/index.md](docs/architecture/sequence-diagrams/index.md)
- Event flow: [docs/architecture/event-flow.md](docs/architecture/event-flow.md)

## License

AGPL-3.0-only. See LICENSE for details.

