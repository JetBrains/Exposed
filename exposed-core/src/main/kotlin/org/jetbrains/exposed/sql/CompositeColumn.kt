package org.jetbrains.exposed.sql

/**
 * Composite column represents multiple tightly related standard columns, which behave like a single column for the user
 *
 * @author Vladislav Kisel
 */
abstract class CompositeColumn<T> : Expression<T>() {
    internal var nullable: Boolean = false
    /**
     * Parse values from [compositeValue] and return list of real columns with its values
     *
     * @return key - real column, value - its parsed value
     */
    abstract fun getRealColumnsWithValues(compositeValue : T) : Map<Column<*>, Any?>

    /**
     * Return list of real columns, wrapped by this composite column
     */
    abstract fun getRealColumns() : List<Column<*>>

    /**
     * Restore the composite value from its parts loaded from the DB
     */
    abstract fun restoreValueFromParts(parts : Map<Column<*>, Any?>) : T

    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        getRealColumns().appendTo { +it }
    }

}

/**
 * Extension of [CompositeColumn] which consists of two columns
 */
abstract class BiCompositeColumn<C1, C2, T>(
        protected val column1: Column<C1>,
        protected val column2: Column<C2>,
        val transformFromValue : (T) -> Pair<C1?, C2?>,
        val transformToValue: (Any?, Any?) -> T
) : CompositeColumn<T>() {

    override fun getRealColumns(): List<Column<*>> = listOf(column1, column2)

    override fun getRealColumnsWithValues(compositeValue: T): Map<Column<*>, Any?> {
        require (compositeValue != null || nullable) {
            "Can't set null value to non-nullable ${this::class.simpleName} column"
        }
        val (v1, v2) = transformFromValue(compositeValue)
        return mapOf(column1 to v1, column2 to v2)
    }

    override fun restoreValueFromParts(parts: Map<Column<*>, Any?>): T {
        val v1 = parts[column1]
        val v2 = parts[column2]
        val result = transformToValue(v1, v2)
        check(result != null || nullable) {
            "Null value received from DB for non-nullable ${this::class.simpleName} column"
        }
        return result as T
    }

}
