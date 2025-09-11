/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.pool;

import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.server.service.pool.ObjectPool;
import com.fluxtion.server.service.pool.ObjectPoolsRegistry;
import lombok.Getter;

import java.util.function.Function;

public class PoolingDataMapper implements Function<PooledMessage, MappedPoolMessage> {

    @Getter
    private ObjectPool<MappedPoolMessage> pool;

    @ServiceRegistered
    public void registerObjectPool(ObjectPoolsRegistry objectPoolsRegistry, String name){
        this.pool = objectPoolsRegistry.getOrCreate(
                MappedPoolMessage.class,
                MappedPoolMessage::new,
                MappedPoolMessage::reset);
    }

    @Override
    public MappedPoolMessage apply(PooledMessage pooledMessage) {
        MappedPoolMessage mappedPoolMessage = pool.acquire();
        mappedPoolMessage.setValue(pooledMessage.value);
        return mappedPoolMessage;
    }
}
