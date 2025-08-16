/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server.internal;

import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.runtime.audit.EventLogControlEvent;
import com.fluxtion.runtime.audit.LogRecordListener;
import com.fluxtion.runtime.service.Service;
import com.fluxtion.server.FluxtionServer;
import com.fluxtion.server.config.AppConfig;
import com.fluxtion.server.config.EventProcessorConfig;
import com.fluxtion.server.config.ServiceConfig;
import com.fluxtion.server.dutycycle.GlobalErrorHandler;
import com.fluxtion.server.service.servercontrol.FluxtionServerController;

import java.util.Objects;

/**
 * Helper responsible for applying AppConfig to a FluxtionServer instance.
 * Keeps FluxtionServer focused and allows easier testing of configuration logic.
 */
public final class ServerConfigurator {

    private ServerConfigurator() {}

    public static FluxtionServer bootFromConfig(AppConfig appConfig, LogRecordListener logRecordListener) {
        Objects.requireNonNull(appConfig, "appConfig must be non-null");
        Objects.requireNonNull(logRecordListener, "logRecordListener must be non-null");

        FluxtionServer fluxtionServer = new FluxtionServer(appConfig);
        fluxtionServer.setDefaultErrorHandler(new GlobalErrorHandler());

        //root server controller
        fluxtionServer.registerService(new Service<>(fluxtionServer, FluxtionServerController.class, FluxtionServerController.SERVICE_NAME));

        //event sources
        if (appConfig.getEventFeeds() != null) {
            appConfig.getEventFeeds().forEach(server -> {
                if (server.isAgent()) {
                    fluxtionServer.registerWorkerService(server.toServiceAgent());
                } else {
                    fluxtionServer.registerService(server.toService());
                }
            });
        }

        //event sinks
        if (appConfig.getEventSinks() != null) {
            appConfig.getEventSinks().forEach(server -> {
                if (server.isAgent()) {
                    fluxtionServer.registerWorkerService(server.toServiceAgent());
                } else {
                    fluxtionServer.registerService(server.toService());
                }
            });
        }

        //services
        if (appConfig.getServices() != null) {
            for (ServiceConfig<?> serviceConfig : appConfig.getServices()) {
                if (serviceConfig.isAgent()) {
                    fluxtionServer.registerWorkerService(serviceConfig.toServiceAgent());
                } else {
                    fluxtionServer.registerService(serviceConfig.toService());
                }
            }
        }

        //event handlers
        if (appConfig.getEventHandlers() != null) {
            appConfig.getEventHandlers().forEach(cfg -> {
                final EventLogControlEvent.LogLevel defaultLogLevel = cfg.getLogLevel() == null ? EventLogControlEvent.LogLevel.INFO : cfg.getLogLevel();
                String groupName = cfg.getAgentName();
                IdleStrategy idleStrategy1 = cfg.getIdleStrategy();
                IdleStrategy idleStrategy = appConfig.lookupIdleStrategyWhenNull(idleStrategy1, cfg.getAgentName());
                cfg.getEventHandlers().entrySet().forEach(handlerEntry -> {
                    String name = handlerEntry.getKey();
                    try {
                        fluxtionServer.addEventProcessor(
                                name,
                                groupName,
                                idleStrategy,
                                () -> {
                                    var eventProcessorConfig = handlerEntry.getValue();
                                    var eventProcessor = eventProcessorConfig.getEventHandler() == null
                                            ? eventProcessorConfig.getEventHandlerBuilder().get()
                                            : eventProcessorConfig.getEventHandler();
                                    var logLevel = eventProcessorConfig.getLogLevel() == null ? defaultLogLevel : eventProcessorConfig.getLogLevel();
                                    @SuppressWarnings("unckecked")
                                    var configMap = eventProcessorConfig.getConfig();

                                    eventProcessor.setAuditLogProcessor(logRecordListener);
                                    eventProcessor.setAuditLogLevel(logLevel);
                                    eventProcessor.init();

                                    eventProcessor.consumeServiceIfExported(com.fluxtion.server.config.ConfigListener.class, l -> l.initialConfig(configMap));
                                    return eventProcessor;
                                });
                    } catch (Exception e) {
                        // keep behavior consistent with previous implementation (log handled by FluxtionServer)
                    }
                });
            });
        }

        //start
        fluxtionServer.init();
        fluxtionServer.start();

        return fluxtionServer;
    }
}
