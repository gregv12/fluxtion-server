/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * Package: config
 *
 * Responsibility
 * - Provides configuration data structures used to assemble a server instance
 *   (AppConfig and related *Config types).
 * - Supplies builder APIs to construct complex configurations fluently.
 *
 * Public API (consumed by other packages)
 * - AppConfig (input to FluxtionServer)
 * - ServiceConfig, EventFeedConfig, EventSinkConfig, EventProcessorGroupConfig,
 *   EventProcessorConfig, ThreadConfig.
 *
 * Allowed dependencies
 * - May depend on: com.fluxtion.server.dispatch.EventSource (for EventWrapStrategy),
 *   com.fluxtion.runtime (Service, MessageSink, EventProcessor) for typing.
 * - Must not depend on: dutycycle, server orchestration, or admin/service impls.
 *
 * Notes
 * - Keep these classes as pure data/transformers; no runtime orchestration logic here.
 */
package com.fluxtion.server.config;