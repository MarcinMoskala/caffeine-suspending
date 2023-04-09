package com.marcinmoskala

import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.*
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SuspendingCacheTest {
    
    private val cache = Caffeine.newBuilder()
        .buildSuspending<String, String>()
    
    @AfterEach
    fun clear() {
        cache.invalidateAll()
    }
    
    @Test
    fun `should return value from cache`() = runTest {
        val value = cache.get("key") { "value" }
        assertEquals("value", value)
    }
    
    @Test
    fun `should return value from cache after suspending`() = runTest {
        val value = cache.get("key") { "value" }
        assertEquals("value", value)
        val value2 = cache.get("key") { "value2" }
        assertEquals("value", value2)
    }
    
    @Test
    fun `should not make unnecessary calls`() = runTest {
        var calls = 0
        suspend fun request(key: String): String {
            calls++
            delay(1000)
            return "Result for $key"
        }
        
        val result1 = cache.get("ABC", ::request)
        assertEquals(1000L, currentTime)
        assertEquals("Result for ABC", result1)
        
        val result2 = cache.get("ABC", ::request)
        assertEquals(1000L, currentTime)
        assertEquals("Result for ABC", result2)
        
        val result3 = cache.get("DEF", ::request)
        assertEquals(2000L, currentTime)
        assertEquals("Result for DEF", result3)
        assertEquals(2, calls)
    }
    
    @Test
    fun `should use caller scope`() = runTest {
        suspend fun request(key: String): String {
            delay(1000)
            return "Result(${currentCoroutineContext()[CoroutineName]?.name})"
        }
        
        val result1 = withContext(CoroutineName("Request1")) {
            cache.get("1", ::request)
        }
        assertEquals(1000L, currentTime)
        assertEquals("Result(Request1)", result1)
        
        val result2 = withContext(CoroutineName("Request2")) {
            cache.get("1", ::request)
        }
        assertEquals(1000L, currentTime)
        assertEquals("Result(Request1)", result2)
        
        cache.invalidate("1")
        
        val result3 = withContext(CoroutineName("Request3")) {
            cache.get("1", ::request)
        }
        assertEquals(2000L, currentTime)
        assertEquals("Result(Request3)", result3)
        
        val result4 = withContext(CoroutineName("Request4")) {
            cache.get("2", ::request)
        }
        assertEquals(3000L, currentTime)
        assertEquals("Result(Request4)", result4)
    }
    
    @Test
    fun `should caller cancel not cancel other waiting`() = runTest {
        suspend fun request(key: String): String {
            delay(1000)
            return "Result(${currentCoroutineContext()[CoroutineName]?.name})"
        }
        
        val job = launch(CoroutineName("Request1")) {
            cache.get("1", ::request)
        }
        
        val res2 = async(CoroutineName("Request2")) {
            delay(10)
            cache.get("1", ::request)
        }
        
        val res3 = async(CoroutineName("Request3")) {
            delay(20)
            cache.get("1", ::request)
        }
        
        delay(500)
        job.cancel()
        
        assertEquals(500L, currentTime)
        assertEquals("Result(Request2)", res2.await())
        assertEquals(1500L, currentTime)
        assertEquals("Result(Request2)", res3.await())
        assertEquals(1500L, currentTime)
    }
    
    @Test
    fun `should throw exception when failed`() = runTest {
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
        val exception: Throwable = object : Exception() {}
        
        launch {
            val result = runCatching {
                cache.get("key") {
                    delay(1000)
                    throw exception
                }
            }
            assertEquals(exception, result.exceptionOrNull())
            assertEquals(1000L, currentTime)
        }
        
        delay(1)
        repeat(5) {
            launch {
                val result = runCatching {
                    cache.get("key") { "ABC" }
                }
                assertEquals(exception, result.exceptionOrNull())
                assertEquals(1000L, currentTime)
            }
        }
    }
}