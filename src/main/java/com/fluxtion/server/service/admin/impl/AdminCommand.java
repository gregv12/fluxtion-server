/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.service.admin.impl;

import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.server.dispatch.EventToQueuePublisher;
import com.fluxtion.server.service.admin.AdminCommandRequest;
import com.fluxtion.server.service.admin.AdminFunction;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Experimental
@Data
@AllArgsConstructor
public class AdminCommand {
    private Consumer<Object> output;
    private Consumer<Object> errOutput;
    private AdminFunction<Object, Object> commandWithOutput;
    private final EventToQueuePublisher<AdminCommand> targetQueue;
    private final Semaphore semaphore = new Semaphore(1);
    private transient List<String> args;

    public AdminCommand(AdminFunction<Object, Object> commandWithOutput, EventToQueuePublisher<AdminCommand> targetQueue) {
        this.commandWithOutput = commandWithOutput;
        this.output = System.out::println;
        this.errOutput = System.err::println;
        this.targetQueue = targetQueue;
    }

    public AdminCommand(AdminFunction<Object, Object> commandWithOutput) {
        this.commandWithOutput = commandWithOutput;
        this.output = System.out::println;
        this.errOutput = System.err::println;
        this.targetQueue = null;
    }

    public AdminCommand(AdminCommand adminCommand, AdminCommandRequest adminCommandRequest) {
        this.commandWithOutput = adminCommand.commandWithOutput;
        this.targetQueue = adminCommand.targetQueue;
        this.output = adminCommandRequest.getOutput();
        this.errOutput = adminCommandRequest.getErrOutput();
        this.args = new ArrayList<>(adminCommandRequest.getArguments());
        this.args.add(0, adminCommandRequest.getCommand());
    }

    public void publishCommand(AdminCommandRequest adminCommandRequest) {
        AdminCommand adminCommand = new AdminCommand(this, adminCommandRequest);
        adminCommand.publishCommand(adminCommand.args);
    }

    public void publishCommand(List<String> value) {
        if (targetQueue == null) {
            commandWithOutput.processAdminCommand(value, output, errOutput);
        } else {
            try {
                if (semaphore.tryAcquire(1, TimeUnit.SECONDS)) {
                    args = value;
                    targetQueue.publish(this);
                    semaphore.acquire();
                    semaphore.release();
                } else {
                    output.accept("command is busy try again");
                }
            } catch (InterruptedException e) {
                throw new com.fluxtion.server.exception.AdminCommandException("Interrupted while publishing admin command", e);
            }
        }
    }

    public void executeCommand() {
        try {
            commandWithOutput.processAdminCommand(args, output, errOutput);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            errOutput.accept("problem executing command exception:" + e.getMessage() + "\n" + sw);
        } finally {
            semaphore.release();
        }
    }
}
