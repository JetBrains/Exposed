package org.jetbrains.exposed.v1.core.utils

import org.jetbrains.exposed.v1.core.InternalApi
import java.util.concurrent.ConcurrentHashMap

interface CacheWithSuspendableDefault<K, V> {
    suspend fun get(key: K): V
}

/** @suppress */
@InternalApi
class CachableMapWithSuspendableDefault<K, V>(
    private val map: MutableMap<K, V> = ConcurrentHashMap<K, V>(),
    val default: suspend (K) -> V
) : CacheWithSuspendableDefault<K, V> {
    override suspend fun get(key: K): V {
        return map.getOrPut(key) { default(key) }
    }
}
