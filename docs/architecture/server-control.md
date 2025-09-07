# Server control and admin commands

This document describes how runtime control is exposed in Mongoose server via admin commands and how you can add your
own commands on the fly. It also lists the default commands that are available out of the box.

Related reading:

- How-to: Add an admin command → ../how-to/writing-an-admin-command.md
- Programmatic control
  API → [FluxtionServerController.java](../../src/main/java/com/fluxtion/server/service/servercontrol/FluxtionServerController.java)

## Overview

Mongoose server exposes an administrative command plane that lets you:

- Inspect and operate on the running system (e.g., list queues/event sources)
- Register custom commands from your processors and services
- Route command execution through the event flow so results are produced on the correct processor thread

The core types are:

- AdminCommandRegistry — a registry where commands can be registered and invoked at runtime.
  Source: [AdminCommandRegistry.java](../../src/main/java/com/fluxtion/server/service/admin/AdminCommandRegistry.java)
- AdminFunction — the functional interface you implement for a command handler.
  Source: [AdminFunction.java](../../src/main/java/com/fluxtion/server/service/admin/AdminFunction.java)
- AdminCommandProcessor — the default registry implementation and dispatcher that wires into the event flow.
  Source: [AdminCommandProcessor.java](../../src/main/java/com/fluxtion/server/service/admin/impl/AdminCommandProcessor.java)
- Optional CLI driver (example) that reads commands and sends them to the registry.
  Source: [CliAdminCommandProcessor.java](../../src/main/java/com/fluxtion/server/service/admin/impl/CliAdminCommandProcessor.java)

## Registering commands on the fly

Any service or processor can register admin commands at startup (or later) using AdminCommandRegistry. Registration can
occur either inside or outside of a processor thread:

- Outside a processor thread: the command is registered directly in-memory and executed inline when invoked.
- Inside a processor thread: the command is bound to that processor via an internal queue so that when the command is
  invoked, the work happens on the owning processor’s single-threaded event loop. This preserves thread-affinity and
  avoids locking in your handler code.

Code sketch (service or handler):

```java
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.server.service.admin.AdminCommandRegistry;

public class MyHandler /* extends ObjectEventHandlerNode, etc. */ {
    private AdminCommandRegistry admin;

    @ServiceRegistered
    public void admin(AdminCommandRegistry admin, String name) {
        this.admin = admin;
        // Register at lifecycle start or here
        admin.registerCommand("echo", (args, out, err) -> {
            out.accept(String.join(" ", args));
        });
    }
}
```

Notes:

- AdminFunction’s signature is: void processAdminCommand(List<String> args, Consumer<OUT> out, Consumer<ERR> err)
  See: [AdminFunction.java](../../src/main/java/com/fluxtion/server/service/admin/AdminFunction.java)
- To invoke a command you typically create an AdminCommandRequest and call
  AdminCommandRegistry.processAdminCommandRequest(request). The CLI example shows how to parse user input and route it
  to the registry.
  See: [CliAdminCommandProcessor.java](../../src/main/java/com/fluxtion/server/service/admin/impl/CliAdminCommandProcessor.java)

## Dispatching and threading model for commands

When your command is registered from within a processor context, AdminCommandProcessor wires an event queue per
command (keyed as "adminCommand.<name>") and subscribes the owning processor. When invoked, the command is delivered via
that queue and executed on the correct processor thread. Implementation reference:

- queue registration and subscription: AdminCommandProcessor.addCommand(...)
- registration behavior based on ProcessorContext: AdminCommandProcessor.registerCommand(...)

Source: [AdminCommandProcessor.java](../../src/main/java/com/fluxtion/server/service/admin/impl/AdminCommandProcessor.java)

## Built-in commands (default)

AdminCommandProcessor registers several default commands during start():

- help, ?
    - Prints the help message including the default commands.
- commands
    - Lists all registered command names (including user-registered ones).
- eventSources
    - Prints information about queues/event sources known to the EventFlowManager.

References in code:

- Registration: start() → registerCommand("help"), registerCommand("?"), registerCommand("eventSources"),
  registerCommand("commands")
- Help text: HELP_MESSAGE constant

Source: [AdminCommandProcessor.java](../../src/main/java/com/fluxtion/server/service/admin/impl/AdminCommandProcessor.java)

## Relationship to server management APIs

For broader operational control (adding/stopping processors, starting/stopping services, etc.), use the server
controller API:

- FluxtionServerController interface
  Source: [FluxtionServerController.java](../../src/main/java/com/fluxtion/server/service/servercontrol/FluxtionServerController.java)

Admin commands can be used to expose safe operational actions that call into such management APIs, or to implement
application-specific controls.

## End-to-end example (CLI)

The CLI admin component demonstrates wiring stdin to admin commands:

- Parses a line into command + args
- Creates an AdminCommandRequest (with output and error consumers)
- Calls AdminCommandRegistry.processAdminCommandRequest(request)

Source: [CliAdminCommandProcessor.java](../../src/main/java/com/fluxtion/server/service/admin/impl/CliAdminCommandProcessor.java)

See also the how-to guide for more patterns and a step-by-step walkthrough:

- ../how-to/writing-an-admin-command.md
