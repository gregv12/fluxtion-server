/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.config;

import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.agrona.concurrent.YieldingIdleStrategy;
import lombok.Data;

/**
 * Configuration for an agent thread.
 * Supports per-agent idle strategy and optional CPU core pinning.
 */
@Data
public class ThreadConfig {
    private String agentName;
    private IdleStrategy idleStrategy = new YieldingIdleStrategy();
    /** Optional zero-based CPU core index to pin the agent thread to. */
    private Integer coreId;

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String agentName;
        private IdleStrategy idleStrategy;
        private Integer coreId;

        private Builder() {
        }

        public Builder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public Builder idleStrategy(IdleStrategy idleStrategy) {
            this.idleStrategy = idleStrategy;
            return this;
        }

        /**
         * Set zero-based CPU core index to pin the agent thread to.
         */
        public Builder coreId(Integer coreId) {
            this.coreId = coreId;
            return this;
        }

        public ThreadConfig build() {
            ThreadConfig cfg = new ThreadConfig();
            cfg.setAgentName(agentName);
            if (idleStrategy != null) cfg.setIdleStrategy(idleStrategy);
            if (coreId != null) cfg.setCoreId(coreId);
            return cfg;
        }
    }
}
