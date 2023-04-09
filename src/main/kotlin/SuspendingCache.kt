import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.*

class SuspendingCache<P : Any, T>(
    private val delegate: Cache<P, Deferred<T>>,
) : Cache<P, Deferred<T>> by delegate {
    
    suspend fun get(key: P, build: suspend (key: P) -> T): T = supervisorScope {
        fun getAsync() = delegate.get(key) { async { build(it) } }!!
        var async: Deferred<T> = getAsync()
        
        // Failed request is not considered a valid cache entry
        if (async.isCancelled) {
            invalidate(key)
            async = getAsync()
        }
        
        try {
            async.await()
        } catch (e: CancellationException) {
            ensureActive()
            // Start again if cancellation was caused by a different caller
            get(key, build)
        } catch (e: Throwable) {
            // Failed request is not considered a valid cache entry
            invalidate(key)
            throw e
        }
    }
    
    override fun cleanUp() {
        invalidateAll(delegate.asMap().filter { (_, v) -> v.isCancelled }.keys)
        delegate.cleanUp()
    }
}

fun <K : Any, V> Caffeine<in K, in Deferred<V>>.buildSuspending(): SuspendingCache<K, V> {
    val delegate = build<K, Deferred<V>>()
    return SuspendingCache(delegate)
}