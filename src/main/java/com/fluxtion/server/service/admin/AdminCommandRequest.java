/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.service.admin;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Data
public class AdminCommandRequest {

    private String command;
    private List<String> arguments = new ArrayList<>();
    private Consumer<Object> output;
    private Consumer<Object> errOutput;
}
