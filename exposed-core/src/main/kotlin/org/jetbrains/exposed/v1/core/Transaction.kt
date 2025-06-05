package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.transactions.TransactionInterface
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/** Represents a key for a value of type [T]. */
class Key<T>

/**
 * Class for storing transaction data that should remain available to the transaction scope even
 * after the transaction is committed.
 */
@Suppress("UNCHECKED_CAST")
open class UserDataHolder {
    /** A mapping of a [Key] to any data value. */
    protected val userdata = ConcurrentHashMap<Key<*>, Any?>()

    /** Maps the specified [key] to the specified [value]. */
    fun <T : Any> putUserData(key: Key<T>, value: T) {
        userdata[key] = value
    }

    /** Removes the specified [key] and its corresponding value. */
    fun <T : Any> removeUserData(key: Key<T>) = userdata.remove(key)

    /** Returns the value to which the specified [key] is mapped, as a value of type [T]. */
    fun <T : Any> getUserData(key: Key<T>): T? = userdata[key] as T?

    /**
     * Returns the value for the specified [key]. If the [key] is not found, the [init] function is called,
     * then its result is mapped to the [key] and returned.
     */
    fun <T : Any> getOrCreate(key: Key<T>, init: () -> T): T = userdata.getOrPut(key, init) as T
}

/** Base class representing a unit block of work that is performed on a database. */
abstract class Transaction : UserDataHolder(), TransactionInterface {
    /** The current number of statements executed in this transaction. */
    var statementCount: Int = 0

    /** The current total amount of time, in milliseconds, spent executing statements in this transaction. */
    var duration: Long = 0

    /** The threshold in milliseconds for query execution to exceed before logging a warning. */
    // TODO fix unused assignment (getter needs to check field if mutable)
    // TODO add unit tests
    var warnLongQueriesDuration: Long? = null
        get() = db.config.warnLongQueriesDuration

    /** Whether tracked values like [statementCount] and [duration] should be stored in [statementStats] for debugging. */
    var debug = false

    /**
     * The number of seconds the driver should wait for a statement to execute in a transaction before timing out.
     * Note Not all drivers implement this limit. Please check the driver documentation.
     */
    var queryTimeout: Int? = null

    /** The unique ID for this transaction. */
    val id by lazy { UUID.randomUUID().toString() }

    /**
     * A [StringBuilder] containing string representations of previously executed statements
     * prefixed by their execution time in milliseconds.
     *
     * **Note:** [Transaction.debug] must be set to `true` for execution strings to be appended.
     */
    val statements = StringBuilder()

    /**
     * A mapping of previously executed statements in this transaction, with a string representation of
     * the prepared statement as the key and the statement count to execution time as the value.
     *
     * **Note:** [Transaction.debug] must be set to `true` for this mapping to be populated.
     */
    val statementStats by lazy { hashMapOf<String, Pair<Int, Long>>() }

    /** Returns the string identifier of a [table], based on its [Table.tableName] and [Table.alias], if applicable. */
    fun identity(table: Table): String =
        (table as? Alias<*>)?.let { "${identity(it.delegate)} ${db.identifierManager.quoteIfNecessary(it.alias)}" }
            ?: db.identifierManager.quoteIfNecessary(table.tableName.inProperCase())

    /** Returns the complete string identifier of a [column], based on its [Table.tableName] and [Column.name]. */
    fun fullIdentity(column: Column<*>): String = QueryBuilder(false).also {
        fullIdentity(column, it)
    }.toString()

    internal fun fullIdentity(column: Column<*>, queryBuilder: QueryBuilder) = queryBuilder {
        if (column.table is Alias<*>) {
            append(db.identifierManager.quoteIfNecessary(column.table.alias))
        } else {
            append(db.identifierManager.quoteIfNecessary(column.table.tableName.inProperCase()))
        }
        append('.')
        append(identity(column))
    }

    /** Returns the string identifier of a [column], based on its [Column.name]. */
    fun identity(column: Column<*>): String = db.identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(column.name)
}
