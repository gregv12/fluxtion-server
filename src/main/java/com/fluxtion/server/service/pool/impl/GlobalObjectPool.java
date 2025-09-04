/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server.service.pool.impl;

import com.fluxtion.server.service.pool.ObjectPool;
import com.fluxtion.server.service.pool.ObjectPoolsRegistry;
import com.fluxtion.server.service.pool.PoolAware;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared registry of {@link ObjectPoolManager} instances, one per class.
 * Instance-based to support dependency injection and testing.
 */
final class GlobalObjectPool implements ObjectPoolsRegistry {

    private final Map<Class<?>, ObjectPool<?>> pools = new ConcurrentHashMap<>();

    public GlobalObjectPool() {
    }

    /**
     * Get (or create) the pool for the specified type.
     */
    @Override
    public <T extends PoolAware> ObjectPool<T> getOrCreate(Class<T> type, Supplier<T> factory) {
        return getOrCreate(type, factory, null, ObjectPoolManager.DEFAULT_CAPACITY);
    }

    @Override
    public <T extends PoolAware> ObjectPool<T> getOrCreate(Class<T> type, Supplier<T> factory, Consumer<T> reset) {
        return getOrCreate(type, factory, reset, ObjectPoolManager.DEFAULT_CAPACITY);
    }

    @Override
    public <T extends PoolAware> ObjectPool<T> getOrCreate(Class<T> type, Supplier<T> factory, Consumer<T> reset, int capacity) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        @SuppressWarnings("unchecked")
        ObjectPool<T> pool = (ObjectPool<T>) pools.computeIfAbsent(type, k -> new ObjectPoolManager<>(factory, reset, capacity));
        return pool;
    }

    /**
     * New overload: allow specifying partitions.
     */
    public <T extends PoolAware> ObjectPool<T> getOrCreate(Class<T> type, Supplier<T> factory, Consumer<T> reset, int capacity, int partitions) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        if (partitions <= 0) throw new IllegalArgumentException("partitions must be > 0");
        @SuppressWarnings("unchecked")
        ObjectPool<T> pool = (ObjectPool<T>) pools.computeIfAbsent(type, k -> new ObjectPoolManager<>(factory, reset, capacity, partitions));
        return pool;
    }

    /**
     * For tests/maintenance: remove a pool.
     */
    @Override
    public void remove(Class<?> type) {
        pools.remove(type);
    }
}
