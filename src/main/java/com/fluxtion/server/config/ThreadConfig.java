/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.config;

import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.agrona.concurrent.YieldingIdleStrategy;
import lombok.Data;

@Data
public class ThreadConfig {
    private String agentName;
    private IdleStrategy idleStrategy = new YieldingIdleStrategy();

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String agentName;
        private IdleStrategy idleStrategy;

        private Builder() {}
        public Builder agentName(String agentName) { this.agentName = agentName; return this; }
        public Builder idleStrategy(IdleStrategy idleStrategy) { this.idleStrategy = idleStrategy; return this; }
        public ThreadConfig build() {
            ThreadConfig cfg = new ThreadConfig();
            cfg.setAgentName(agentName);
            if (idleStrategy != null) cfg.setIdleStrategy(idleStrategy);
            return cfg;
        }
    }
}
