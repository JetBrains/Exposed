package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.sql.InternalApi
import org.jetbrains.exposed.sql.Key
import org.jetbrains.exposed.sql.Transaction
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Returns the result of reading/writing transaction data stored within the scope of the current transaction.
 *
 * If no data is found, the specified [init] block is called with the current transaction as its receiver and
 * the result is returned.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> transactionScope(init: Transaction.() -> T) = TransactionStore(init) as ReadWriteProperty<Any?, T>

/**
 * Returns the result of reading/writing transaction data stored within the scope of the current transaction,
 * or `null` if no data is found.
 */
fun <T : Any> nullableTransactionScope() = TransactionStore<T>()

/**
 * Class responsible for implementing property delegates of read-write properties in
 * the current transaction's `UserDataHolder`.
 */
class TransactionStore<T : Any>(val init: (Transaction.() -> T)? = null) : ReadWriteProperty<Any?, T?> {

    private val key = Key<T>()

    override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        @OptIn(InternalApi::class)
        val currentOrNullTransaction = CoreTransactionManager.currentTransactionOrNull()
        return currentOrNullTransaction?.getUserData(key)
            ?: init?.let {
                val value = currentOrNullTransaction?.it() ?: error("Can't init value outside the transaction")
                currentOrNullTransaction.putUserData(key, value)
                value
            }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        @OptIn(InternalApi::class)
        CoreTransactionManager.currentTransactionOrNull()?.let {
            if (value == null) {
                it.removeUserData(key)
            } else {
                it.putUserData(key, value)
            }
        }
    }
}
