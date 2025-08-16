/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.service.admin.impl;

import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.server.dispatch.CallBackType;
import com.fluxtion.server.dispatch.EventFlowManager;
import com.fluxtion.server.dispatch.EventFlowService;
import com.fluxtion.server.dispatch.EventSource;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.admin.AdminCommandRequest;
import com.fluxtion.server.service.admin.AdminFunction;
import com.fluxtion.server.service.metrics.EventProcessingMetrics;
import com.fluxtion.server.service.metrics.MetricsRegistry;
import lombok.extern.java.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Experimental
@Log
public class AdminCommandProcessor implements EventFlowService, AdminCommandRegistry, Lifecycle, EventSource<AdminCommand> {

    private final Map<String, AdminCommand> registeredCommandMap = new HashMap<>();
    private EventFlowManager eventFlowManager;

    private static final String HELP_MESSAGE = """
            default commands:
            ---------------------------
            quit         - exit the console
            help/?       - this message
            commands     - registered service commands
            eventSources - list event sources
            metrics      - show event processing metrics (optional filter: metrics <substring>)
            metrics_on   - metrics recording on
            metrics_off  - metrics recording off
            """;

    @Override
    public void init() {
        log.info("init");
    }

    @Override
    public void setEventFlowManager(EventFlowManager eventFlowManager, String serviceName) {
        this.eventFlowManager = eventFlowManager;
        eventFlowManager.registerEventMapperFactory(AdminCommandInvoker::new, AdminCallbackType.class);
    }

    @Override
    public void start() {
        log.info("start");
        registerCommand("help", this::printHelp);
        registerCommand("?", this::printHelp);
        registerCommand("eventSources", this::printQueues);
        registerCommand("commands", this::registeredCommands);
        registerCommand("metrics", this::printMetrics);
        registerCommand("metrics_on", this::metricsOn);
        registerCommand("metrics_off", this::metricsOff);
    }

    @Override
    public void processAdminCommandRequest(AdminCommandRequest command) {
        String commandName = command.getCommand().trim();
        log.info("processing: " + command + " name: '" + commandName + "'");
        AdminCommand adminCommand = registeredCommandMap.get(commandName);
        if (adminCommand != null) {
            adminCommand.publishCommand(command);
        } else {
            log.info("command not found: " + commandName);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <OUT, ERR> void registerCommand(String name, AdminFunction<OUT, ERR> command) {
        if (com.fluxtion.server.dispatch.ProcessorContext.currentProcessor() == null) {
            registeredCommandMap.put(name, new AdminCommand((AdminFunction<Object, Object>) command));
        } else {
            String queueKey = "adminCommand." + name;
            addCommand(
                    name,
                    queueKey,
                    new AdminCommand((AdminFunction<Object, Object>) command, eventFlowManager.registerEventSource(queueKey, this)));
        }
    }

    @Override
    public List<String> commandList() {
        return registeredCommandMap.keySet().stream().sorted().collect(Collectors.toList());
    }

    @Override
    public void stop() {
        log.info("stop");
    }

    @Override
    public void tearDown() {
        log.info("stop");
    }

    private void printHelp(List<String> args, Consumer<String> out, Consumer<String> err) {
        out.accept(HELP_MESSAGE);
    }

    private void printQueues(List<String> args, Consumer<String> out, Consumer<String> err) {
        StringBuilder sb = new StringBuilder();
        eventFlowManager.appendQueueInformation(sb);
        out.accept(sb.toString());
    }

    private void registeredCommands(List<String> args, Consumer<String> out, Consumer<String> err) {
        String commandsString = registeredCommandMap.keySet().stream()
                .sorted()
                .collect(Collectors.joining(
                        "\n",
                        "Service commands:\n---------------------------\n",
                        "\n"));
        out.accept(commandsString);
    }

    private void printMetrics(List<String> args, Consumer<String> out, Consumer<String> err) {
        Map<String, EventProcessingMetrics> all = MetricsRegistry.all();
        if (all.isEmpty()) {
            out.accept("No metrics available\n");
            return;
        }
        String filter = null;
        if (args != null && args.size() > 1) {
            // args[0] is the command name, args[1] optional filter substring
            filter = args.get(1).toLowerCase();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Metrics:\n");
        sb.append("---------------------------\n");
        final String filterFinal = filter;
        all.entrySet().stream()
//                .filter(e -> e.getValue().getProcessedCount() > 0)
                .filter(e -> e.getValue().isEnabled())
                .filter(e -> filterFinal == null || e.getKey().toLowerCase().contains(filterFinal))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    EventProcessingMetrics m = entry.getValue();
                    sb.append(entry.getKey()).append(':').append('\n');
                    sb.append("  processed: ").append(m.getProcessedCount()).append('\n');
                    sb.append("  failed: ").append(m.getFailedCount()).append('\n');
                    sb.append("  lastLatencyNanos: ").append(m.getLastLatencyNanos()).append('\n');
                    sb.append("  avgLatencyNanos: ").append(m.getAverageLatencyNanos()).append('\n');
                    sb.append("  throughputEps: ")
                            .append(String.format("%.2f", m.getThroughputEventsPerSecond()))
                            .append('\n');
                });
        out.accept(sb.toString());
    }

    public void metricsOn(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args != null && args.size() > 1) {
            // args[0] is the command name, args[1] optional filter substring
            String filter = args.get(1).toLowerCase();
            eventFlowManager.metricsOn(filter);
            out.accept("metrics on for queue:" + filter);
        } else {
            eventFlowManager.metricsOn(null);
            out.accept("metrics on for all queue");
        }
    }

    public void metricsOff(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args != null && args.size() > 1) {
            // args[0] is the command name, args[1] optional filter substring
            String filter = args.get(1).toLowerCase();
            eventFlowManager.metricsOff(filter);
            out.accept("metrics off for queue:" + filter);
        }else{
            eventFlowManager.metricsOn(null);
            out.accept("metricsOff off for all queue");
        }
    }

    private void addCommand(String name, String queueKey, AdminCommand adminCommand) {
        StaticEventProcessor staticEventProcessor = com.fluxtion.server.dispatch.ProcessorContext.currentProcessor();
        log.info("registered command:" + name + " queue:" + queueKey + " processor:" + staticEventProcessor);

        registeredCommandMap.put(name, adminCommand);

        com.fluxtion.server.dispatch.EventSubscriptionKey<?> subscriptionKey = new com.fluxtion.server.dispatch.EventSubscriptionKey<>(
                new com.fluxtion.server.dispatch.EventSourceKey<>(queueKey),
                AdminCallbackType.class
        );

        staticEventProcessor.getSubscriptionManager().subscribe(subscriptionKey);
    }

    @Override
    public void subscribe(com.fluxtion.server.dispatch.EventSubscriptionKey<AdminCommand> eventSourceKey) {
    }

    @Override
    public void unSubscribe(com.fluxtion.server.dispatch.EventSubscriptionKey<AdminCommand> eventSourceKey) {
    }

    @Override
    public void setEventToQueuePublisher(com.fluxtion.server.dispatch.EventToQueuePublisher<AdminCommand> targetQueue) {
    }

    private static class AdminCallbackType implements CallBackType {

        @Override
        public String name() {
            return "AdminCallback";
        }

    }
}

