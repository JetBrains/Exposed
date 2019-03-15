package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.sql.Key
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Suppress("UNCHECKED_CAST")
fun <T:Any> transactionScope(init: () -> T) = TransactionStore(init) as ReadWriteProperty<Any?, T>
fun <T:Any> nullableTransactionScope() = TransactionStore<T>()

class TransactionStore<T:Any>(val init: (() -> T)? = null) : ReadWriteProperty<Any?, T?> {

    private val key = Key<T>()

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        val currentOrNullTransaction = TransactionManager.currentOrNull()
        return init?.let { currentOrNullTransaction!!.getOrCreate(key, init) } ?: currentOrNullTransaction?.getUserData(key)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        TransactionManager.currentOrNull()?.let{
            if (value == null)
                it.removeUserData(key)
            else
                it.putUserData(key, value)
        }
    }
}
