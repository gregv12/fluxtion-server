/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * Package: dutycycle
 * <p>
 * Responsibility
 * - Hosts Agent-based components that perform work on threads and coordinate
 * event consumption and service lifecycles:
 * - EventQueueToEventProcessor(Agent) for queue polling and dispatch.
 * - ComposingEventProcessorAgent and ComposingServiceAgent for group management.
 * - Scheduler (DeadWheelScheduler) service agent.
 * <p>
 * Public API (consumed by other packages)
 * - EventQueueToEventProcessor (Agent interface)
 * - ServiceAgent (holder for agent-backed services)
 * <p>
 * Allowed dependencies
 * - May depend on: com.fluxtion.server.dispatch (to obtain mapping agents, subscriptions),
 * com.fluxtion.server.service.scheduler for scheduler integration,
 * com.fluxtion.server.exception for domain errors.
 * - Must not depend on: com.fluxtion.server.config (no configuration model coupling).
 * <p>
 * Notes
 * - Keep this package free from configuration transformation; it should operate
 * based on runtime objects supplied by FluxtionServer.
 */
package com.fluxtion.server.dutycycle;