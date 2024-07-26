/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.service.admin;

import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.event.Signal;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.server.subscription.EventFlowManager;
import lombok.extern.java.Log;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Experimental
@Log
public class CliAdmin implements com.fluxtion.server.subscription.EventFlowService, Admin, Lifecycle, com.fluxtion.server.subscription.EventSource<com.fluxtion.server.service.admin.AdminCommand> {

    private static final AtomicBoolean runLoop = new AtomicBoolean(true);
    private ExecutorService executorService;
    private final CommandProcessor commandProcessor = new CommandProcessor();
    private final Map<String, com.fluxtion.server.service.admin.AdminCommand> registeredCommandMap = new HashMap<>();
    private com.fluxtion.server.subscription.EventFlowManager eventFlowManager;
    private String serviceName;

    private static class AdminCallbackType implements com.fluxtion.server.subscription.CallBackType {

        @Override
        public String name() {
            return "AdminCallback";
        }
    }

    @Override
    public void setEventFlowManager(com.fluxtion.server.subscription.EventFlowManager eventFlowManager, String serviceName) {
        this.eventFlowManager = eventFlowManager;
        this.serviceName = serviceName;
        eventFlowManager.registerEventMapperFactory(AdminCommandInvoker::new, AdminCallbackType.class);
    }

    @Override
    public void init() {
        log.info("init");
        executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public void start() {
        log.info("start");
        executorService.submit(() -> {
            log.info("starting");

            commandProcessor.help(null);
            commandProcessor.registeredCommands(null);

            Scanner scanner = new Scanner(System.in);

            while (runLoop.get()) {
                // Prompt the user
                System.out.print("Command > ");

                // Read user input as String
                String[] commandArgs = scanner.nextLine().trim().split(" ");
                if (commandArgs.length > 0) {
                    switch (commandArgs[0]) {
                        case "eventSources": {
                            commandProcessor.eventQueues(null);
                            break;
                        }
                        case "commands": {
                            commandProcessor.registeredCommands(null);
                            break;
                        }
                        case "?":
                        case "help": {
                            commandProcessor.help(null);
                            break;
                        }
                        default: {
                            commandProcessor.anyControlMethod(Arrays.asList(commandArgs));
                        }
                    }
                }
            }
        });
    }

    @Override
    public void stop() {
        log.info("stop");
        runLoop.set(false);
    }

    @Override
    public void tearDown() {
        log.info("stop");
        runLoop.set(false);
        executorService.shutdown();
    }

    @Override
    public void registerCommand(String name, Consumer<List<String>> command) {
        if (com.fluxtion.server.subscription.EventFlowManager.currentProcessor() == null) {
            registeredCommandMap.put(name, new com.fluxtion.server.service.admin.AdminCommand(command));
        } else {
            String queueKey = "adminCommand." + name;
            addCommand(
                    name,
                    queueKey,
                    new com.fluxtion.server.service.admin.AdminCommand(command, eventFlowManager.registerEventSource(queueKey, this)));
        }
    }

    @Override
    public void registerCommand(String name, AdminFunction command) {
        if (com.fluxtion.server.subscription.EventFlowManager.currentProcessor() == null) {
            registeredCommandMap.put(name, new com.fluxtion.server.service.admin.AdminCommand(command));
        } else {
            String queueKey = "adminCommand." + name;
            addCommand(
                    name,
                    queueKey,
                    new com.fluxtion.server.service.admin.AdminCommand(command, eventFlowManager.registerEventSource(queueKey, this)));
        }
    }

    private void addCommand(String name, String queueKey, com.fluxtion.server.service.admin.AdminCommand adminCommand) {
        StaticEventProcessor staticEventProcessor = EventFlowManager.currentProcessor();
        log.info("registered command:" + name + " queue:" + queueKey + " processor:" + staticEventProcessor);

        registeredCommandMap.put(name, adminCommand);

        com.fluxtion.server.subscription.EventSubscriptionKey<?> subscriptionKey = new com.fluxtion.server.subscription.EventSubscriptionKey<>(
                new com.fluxtion.server.subscription.EventSourceKey<>(queueKey),
                AdminCallbackType.class,
                queueKey
        );

        staticEventProcessor.getSubscriptionManager().subscribe(subscriptionKey);
    }

    @Override
    public void subscribe(com.fluxtion.server.subscription.EventSubscriptionKey<com.fluxtion.server.service.admin.AdminCommand> eventSourceKey) {
    }

    @Override
    public void unSubscribe(com.fluxtion.server.subscription.EventSubscriptionKey<com.fluxtion.server.service.admin.AdminCommand> eventSourceKey) {
    }

    @Override
    public void setEventToQueuePublisher(com.fluxtion.server.subscription.EventToQueuePublisher<AdminCommand> targetQueue) {
    }

    private class CommandProcessor {

        private static final String help = """
                default commands:
                ---------------------------
                quit         - exit the console
                help/?       - this message
                name         - service name
                commands     - registered service commands
                eventSources - list event sources
                """;

        public void controlMethod(Signal<List<String>> publishSignal) {
            System.out.println("service name: " + serviceName);
        }

        public void quit(Signal<List<String>> publishSignal) {
            log.info("quit");
            runLoop.set(false);
        }

        public void help(Signal<List<String>> publishSignal) {
            System.out.println(help);
        }

        public void eventQueues(Signal<List<String>> publishSignal) {
            eventFlowManager.appendQueueInformation(System.out);
        }

        public void registeredCommands(Signal<List<String>> publishSignal) {
            String commandsString = registeredCommandMap.keySet().stream()
                    .sorted()
                    .collect(Collectors.joining(
                            "\n",
                            "Service commands:\n---------------------------\n",
                            "\n"));
            System.out.println(commandsString);
        }

        public void anyControlMethod(List<String> publishSignal) {
            String command = publishSignal.get(0);
            if (registeredCommandMap.containsKey(command)) {
                registeredCommandMap.get(command).publishCommand(publishSignal);
            } else {
                System.out.println("unknown command: " + command);
                System.out.println(help);
            }
        }
    }
}

