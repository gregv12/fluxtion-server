/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.config;

import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.output.MessageSink;
import com.fluxtion.runtime.service.Service;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.util.function.Function;

@Experimental
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventSinkConfig<T extends MessageSink<T>> {

    private T instance;
    private String name;
    private Function<? super T, ?> valueMapper = Function.identity();

    @SneakyThrows
    @SuppressWarnings({"unchecked", "all"})
    public Service<T> toService() {
        instance.setValueMapper(valueMapper);
        Service svc = new Service(instance, MessageSink.class, name);
        return svc;
    }
}
