/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.config;

import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Data
public class GetDefaultHandlerGroupConfigTest {

    private AppConfig appConfig;

    @BeforeEach
    public void setup() {
        appConfig = new AppConfig();
    }

    @Test
    public void testRetrieveExistingGroup() {
        //Given
        String groupName = "testGroup";
        EventProcessorGroupConfig existingGroup = EventProcessorGroupConfig.builder()
                .agentName(groupName)
                .build();
        appConfig.getEventHandlers().add(existingGroup);

        //When
        EventProcessorGroupConfig retrievedGroup = appConfig.getGroupConfig(groupName);

        //Then
        assertEquals(existingGroup, retrievedGroup);
    }

    @Test
    public void testCreateNewGroup() {
        //Given
        String groupName = "newGroup";

        //When
        EventProcessorGroupConfig newGroup = appConfig.getGroupConfig(groupName);

        //Then
        assertNotNull(newGroup);
        assertEquals(groupName, newGroup.getAgentName());
        assertTrue(appConfig.getEventHandlers().contains(newGroup));
    }
}
