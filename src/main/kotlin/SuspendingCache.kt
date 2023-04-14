package com.marcinmoskala

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class SuspendingCache<K : Any, V>(
    private val cache: AsyncCache<K, V>,
) {

    suspend fun get(
        key: K,
        build: suspend (key: K) -> V
    ): V = supervisorScope {
        try {
            cache.get(key) { k, _ ->
                async { build(k) }.asCompletableFuture()
            }!!.await()
        } catch (_: CancellationException) {
            ensureActive()
            get(key, build)
        }
    }

    fun asAsyncCache() = cache

    fun contains(key: K): Boolean {
        val async = cache.getIfPresent(key)
        return async != null && !async.isCancelled
    }

    suspend fun getIfPresent(key: K): V? = cache.getIfPresent(key)?.await()

    fun put(key: K, value: V) {
        cache.put(key, CompletableFuture.completedFuture(value))
    }

    operator fun set(key: K, value: V) = put(key, value)

    suspend fun asMap(): Map<K, V> {
        return cache.asMap().mapValues { it.value.await() }
    }

    fun asDeferredMap(): Map<K, Deferred<V>> {
        return cache.asMap().mapValues { it.value.asDeferred() }
    }

    fun invalidate(key: K) {
        cache.synchronous().invalidate(key)
    }

    fun invalidateAll() {
        cache.synchronous().invalidateAll()
    }
}

fun <K : Any, V> Caffeine<in K, in V>.buildSuspending(): SuspendingCache<K, V> {
    val delegate = buildAsync<K, V>()
    return SuspendingCache(delegate)
}