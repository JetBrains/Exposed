package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.withDialect
import java.sql.ResultSet

class ResultRow(val fieldIndex: Map<Expression<*>, Int>) {
    private val database : Database? = TransactionManager.currentOrNull()?.db
    private val data = arrayOfNulls<Any?>(fieldIndex.size)

    /**
     * Retrieves value of a given expression on this row.
     *
     * @param c expression to evaluate
     * @throws IllegalStateException if expression is not in record set or if result value is uninitialized
     *
     * @see [getOrNull] to get null in the cases an exception would be thrown
     */
    operator fun <T> get(c: Expression<T>): T {
        val d = getRaw(c)

        if (d == null && c is Column<*> && c.dbDefaultValue != null && !c.columnType.nullable) {
            exposedLogger.warn("Column ${TransactionManager.current().fullIdentity(c)} is marked as not null, " +
                    "has default db value, but returns null. Possible have to re-read it from DB.")
        }

        return database?.dialect?.let {
            withDialect(it) {
                rawToColumnValue(d, c)
            }
        } ?: rawToColumnValue(d, c)
    }

    operator fun <T> set(c: Expression<out T>, value: T) {
        val index = fieldIndex[c] ?: error("$c is not in record set")
        data[index] = value
    }

    fun <T> hasValue(c: Expression<T>): Boolean = fieldIndex[c]?.let{ data[it] != NotInitializedValue } ?: false

    fun <T> getOrNull(c: Expression<T>): T? = if (hasValue(c)) rawToColumnValue(getRaw(c), c) else null

    @Deprecated("Replaced with getOrNull to be more kotlinish", replaceWith = ReplaceWith("getOrNull(c)"))
    fun <T> tryGet(c: Expression<T>): T? = getOrNull(c)

    @Suppress("UNCHECKED_CAST")
    private fun <T> rawToColumnValue(raw: T?, c: Expression<T>): T {
        return when {
            raw == null -> null
            raw == NotInitializedValue -> error("$c is not initialized yet")
            c is ExpressionAlias<T> && c.delegate is ExpressionWithColumnType<T> -> c.delegate.columnType.valueFromDB(raw)
            c is ExpressionWithColumnType<T> -> c.columnType.valueFromDB(raw)
            else -> raw
        } as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getRaw(c: Expression<T>): T? {
        if (c is CompositeColumn<T>) {
            val rawParts = c.getRealColumns().associateWith { getRaw(it) }
            return c.restoreValueFromParts(rawParts)
        }

        val index = fieldIndex[c]
            ?: ((c as? Column<*>)?.columnType as? EntityIDColumnType<*>)?.let { fieldIndex[it.idColumn] }
            ?: fieldIndex.keys.firstOrNull {
                ((it as? Column<*>)?.columnType as? EntityIDColumnType<*>)?.idColumn == c
            }?.let { fieldIndex[it] }
            ?: error("$c is not in record set")

        return data[index] as T?
    }

    override fun toString(): String =
            fieldIndex.entries.joinToString { "${it.key}=${data[it.value]}" }

    internal object NotInitializedValue

    companion object {

        @Deprecated(level = DeprecationLevel.ERROR, message = "Consider to use [create] with map param instead")
        fun create(rs: ResultSet, fields: List<Expression<*>>): ResultRow {
            return create(rs, fields.distinct().mapIndexed { index, field -> field to index }.toMap())
        }

        fun create(rs: ResultSet, fieldsIndex: Map<Expression<*>, Int>): ResultRow {
            return ResultRow(fieldsIndex).apply {
                fieldsIndex.forEach{ (field, index) ->
                    val value = (field as? Column<*>)?.columnType?.readObject(rs, index + 1) ?: rs.getObject(index + 1)
                    data[index] = value
                }
            }
        }

        fun createAndFillValues(data: Map<Expression<*>, Any?>) : ResultRow =
                ResultRow(data.keys.mapIndexed { i, c -> c to i }.toMap()).also { row ->
                    data.forEach { (c, v) -> row[c] = v }
                }

        fun createAndFillDefaults(columns : List<Column<*>>): ResultRow =
                ResultRow(columns.mapIndexed { i, c -> c to i }.toMap()).apply {
                    columns.forEach {
                        this[it] = it.defaultValueFun?.invoke() ?: if (!it.columnType.nullable) NotInitializedValue else null
                    }
                }
    }
}