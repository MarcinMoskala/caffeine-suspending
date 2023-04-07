import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

class SuspendingCache<P : Any, T>(
    private val delegate: Cache<P, Deferred<T>>,
    private val scope: CoroutineScope,
) : Cache<P, Deferred<T>> by delegate {

    suspend fun get(key: P, build: suspend (key: P) -> T): T {
        val async = getAsync(key, build)
        return if (async.isCancelled) {
            invalidate(key)
            getAsync(key, build).await()
        } else {
            async.await()
        }
    }

    private fun getAsync(key: P, build: suspend (key: P) -> T) = delegate
        .get(key) { scope.async { build(it) } }!!
}

inline fun <reified K: Any, reified V> Caffeine<in K, in Deferred<V>>.buildSuspending(
    scope: CoroutineScope
): SuspendingCache<K, V> {
    val delegate = build<K, Deferred<V>>()
    return SuspendingCache(delegate, scope)
}