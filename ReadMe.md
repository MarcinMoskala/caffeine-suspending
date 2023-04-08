# Wrapper over Caffeine cache library supporting suspending functions

This library is a wrapper over [Caffeine](), a popular cache library. It adds support for suspending functions, so that you can use it with coroutines.

## Usage

To create a cache, use Caffeine builder, but then create it using `buildSuspending` method. It takes a scope, in which the suspending functions will be executed.

```kotlin
val cache: SuspendingCache<String, String> = Caffeine.newBuilder()
    // ...
    .buildSuspending<String, String>(backgroundScope)
```

Then you can use it as a regular cache, but with suspending function `get`. It takes a key and a function that will be executed, if the key is not present in the cache. The function will be executed only once, even if there are multiple calls to `get` for the same key.

```kotlin
var calledTimes = 0
val result = cache.get(key) {
    delay(1000)
    calledTimes++
    "ABC"
}
println(result) // ABC

val result2 = cache.get(key) {
    calledTimes++
    "ABC"
}
// (1 sec)
println(result) // ABC

println(calledTimes) // 1
```

Library also supports all the features of Caffeine, like expiration, refresh, etc.

## Design choices

It is not an easy task to make proper design choices for a cache. My observation is, that most of the creators do not even consider most of the problems. Here are the most important problems worth considering, and my answers to them, that are implemented in this library:

1. **What should be the result of a call to `get` method, when its "request" function throws an exception?**

I assume, that the typical use case is to have a cache for a remote resource (API or DB). When a request fails, the caller should be acknowledged about it, to show appropriate information to user. 

```kotlin
suspend fun request(key: String): String {
    delay(1000)
    throw RuntimeException()
}

val cache = Caffeine.newBuilder()
    .buildSuspending<String, String>(backgroundScope)
val result = runCatching { cache.get(key, ::request) }
println(result) // Failure(java.lang.RuntimeException)
```

2. **Consider two requests for the same key. The second one will wait to the result of the first one. The first one sends a request that fails. What should be the result of the second call?**

If two requests for the same key are made, the second one will wait for the result of the first one. If the first one fails, the second one should also fail.

```kotlin
var isFirst = true
fun request(key: String): String {
    delay(1000)
    if (isFirst) {
        isFirst = false
        throw RuntimeException()
    } else {
        return "Result"
    }
}

launch {
    val result1 = runCatching { cache.get(key, ::request) } // (1 sec)
    println(result1) // Failure(java.lang.RuntimeException)
}
launch {
    delay(500) // (0.5 sec)
    val result2 = runCatching { cache.get(key, ::request) }
    // (0.5 sec)
    println(result2) // Failure(java.lang.RuntimeException)
}
```

The reason behind it is simple: Consider that 10 clients are requesting the same resource at the same time. If the first one fails, the second one should also fail, because the resource is not available. It would make little sense to make next calls request one after another. If we wanted such behavior, we should use a retry mechanism. We want to make a new request when a cororoutine asks for the resource **after** the first request is finished, what is explained in the next point. 

3. **Consider you have a read that starts a request that fails. Then you have another read before value expire time. Should it automatically fail, or should it start the request again?**

A cache might keep values for a long time, and we do not want to treat a failing request as a permanent failure. We want to retry the request, when a coroutine asks for the resource **after** the first request is finished. In other words, a failing request should not be treated as a valid cache entry. 

```kotlin
var isFirst = true
fun request(key: String): String {
    delay(1000)
    if (isFirst) {
        isFirst = false
        throw RuntimeException()
    } else {
        return "Result"
    }
}

val result1 = runCatching { cache.get(key, ::request) }
// (1 sec)
println(result1) // Failure(java.lang.RuntimeException)
val result2 = runCatching { cache.get(key, ::request) }
// (1 sec)
println(result2) // Result
```