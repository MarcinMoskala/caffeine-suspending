import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SuspendingCacheTest {
    @Test
    fun `should return value from cache`() = runTest {
        val cache = Caffeine.newBuilder()
            .buildSuspending<String, String>(backgroundScope)
        val value = cache.get("key") { "value" }
        assertEquals("value", value)
    }

    @Test
    fun `should return value from cache after suspending`() = runTest {
        val cache = Caffeine.newBuilder()
            .buildSuspending<String, String>(backgroundScope)
        val value = cache.get("key") { "value" }
        assertEquals("value", value)
        val value2 = cache.get("key") { "value2" }
        assertEquals("value", value2)
    }

    fun `should throw exception when failed`() = runTest {
        val cache = Caffeine.newBuilder()
            .buildSuspending<String, String>(backgroundScope)
        val exception: Throwable = object : Exception() {}
        val result = runCatching {
            cache.get("key") {
                throw exception
            }
        }
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `should retry after failing request`() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob())
        val cache = Caffeine.newBuilder()
            .buildSuspending<String, String>(scope)
        val exception: Throwable = object : Exception() {}

        val result1 = runCatching {
            cache.get("key") {
                throw exception
            }
        }
        delay(1000)
        assertEquals(exception, result1.exceptionOrNull())
        val result2 = runCatching {
            cache.get("key") {
                "ABC"
            }
        }
        assertEquals("ABC", result2.getOrNull())
    }

    @Test
    fun `should fail all requests waiting for response`() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob())
        val cache = Caffeine.newBuilder()
            .buildSuspending<String, String>(scope)
        val exception: Throwable = object : Exception() {}

        launch {
            val result = runCatching {
                cache.get("key") {
                    delay(1000)
                    throw exception
                }
            }
            assertEquals(exception, result.exceptionOrNull())
        }

        delay(1)
        repeat(5) {
            launch {
                val result = runCatching {
                    cache.get("key") { "ABC" }
                }
                assertEquals(exception, result.exceptionOrNull())
            }
        }
    }
}