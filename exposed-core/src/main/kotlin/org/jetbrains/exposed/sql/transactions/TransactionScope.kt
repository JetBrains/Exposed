package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.sql.Key
import org.jetbrains.exposed.sql.Transaction
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Suppress("UNCHECKED_CAST")
fun <T:Any> transactionScope(init: Transaction.() -> T) = TransactionStore(init) as ReadWriteProperty<Any?, T>
fun <T:Any> nullableTransactionScope() = TransactionStore<T>()

class TransactionStore<T:Any>(val init: (Transaction.() -> T)? = null) : ReadWriteProperty<Any?, T?> {

    private val key = Key<T>()

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        val currentOrNullTransaction = TransactionManager.currentOrNull()
        return currentOrNullTransaction?.getUserData(key)
            ?: init?.let {
                val value = currentOrNullTransaction!!.it()
                currentOrNullTransaction.putUserData(key, value)
                value
            }
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
