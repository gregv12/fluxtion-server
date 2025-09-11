/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server.example.datamapper;

import com.fluxtion.server.service.extension.AbstractEventSourceService;

/**
 * Simple event source for TestEvent_In used by examples/tests.
 */
public class TestEventSource extends AbstractEventSourceService<TestEvent_In> {
    public TestEventSource(String name) {
        super(name);
    }

    public void publishEvent(TestEvent_In event) {
        if (output != null) {
            output.publish(event);
        }
    }
}
