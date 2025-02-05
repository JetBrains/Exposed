package org.jetbrains.exposed.r2dbc.sql.mtc

import java.util.*
import kotlin.concurrent.getOrSet

internal object MappedThreadContext {
    private const val HT_SIZE = 7
    private val tlm = ThreadLocalMap()

    fun put(key: String, value: Any?) {
        // Hashtable does not allow set tx value to be null - put() throws NPE
        value?.let {
            val ht = tlm.getOrSet { Hashtable(HT_SIZE) }
            ht[key] = value
        }
    }

    fun get(key: String): Any? {
        val ht: Hashtable<String, Any?>? = tlm.get()
        return ht?.getOrElse(key) { null }
    }

    fun remove(key: String) {
        val ht: Hashtable<String, Any?>? = tlm.get()
        ht?.remove(key)
        if (ht?.isEmpty == true) {
            clear()
        }
    }

    fun clear() {
        tlm.get()?.clear()
    }
}
