package com.marcinmoskala

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import kotlinx.coroutines.supervisorScope

class SuspendingCache<P : Any, T>(
    private val delegate: AsyncCache<P, T>,
) : Cache<P, T> by delegate.synchronous() {

    suspend fun get(
        key: P,
        build: suspend (key: P) -> T
    ): T = supervisorScope {
        try {
            delegate.get(key) { k, _ ->
                async { build(k) }.asCompletableFuture()
            }!!.await()
        } catch (_: CancellationException) {
            ensureActive()
            get(key, build)
        }
    }

    fun asyncCache() = delegate
}

fun <K : Any, V> Caffeine<in K, in V>.buildSuspending(): SuspendingCache<K, V> {
    val delegate = buildAsync<K, V>()
    return SuspendingCache(delegate)
}