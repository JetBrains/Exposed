package org.jetbrains.exposed.v1.core.utils

import org.jetbrains.exposed.v1.core.InternalApi

interface CacheWithDefault<K, V> {
    fun get(key: K): V
}

@InternalApi
class CachableMapWithDefault<K, V>(
    private val map: MutableMap<K, V> = mutableMapOf(),
    val default: (K) -> V
) : CacheWithDefault<K, V> {
    override fun get(key: K): V = map.getOrPut(key) { default(key) }
}
