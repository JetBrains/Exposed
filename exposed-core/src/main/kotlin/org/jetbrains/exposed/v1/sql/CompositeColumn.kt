package org.jetbrains.exposed.v1.sql

/**
 * Composite column represents multiple tightly related standard columns, which behave like a single column for the user
 *
 * @author Vladislav Kisel
 */
abstract class CompositeColumn<T> : Expression<T>() {
    internal var nullable: Boolean = false

    /**
     * Parses the [compositeValue] and returns a list of real columns with their values.
     *
     * @return Map of real columns as keys to their parsed values.
     */
    abstract fun getRealColumnsWithValues(compositeValue: T): Map<Column<*>, Any?>

    /**
     * Returns a list of real columns, wrapped by this composite column.
     */
    abstract fun getRealColumns(): List<Column<*>>

    /**
     * Restores the composite value based on its component column values loaded from the database.
     */
    abstract fun restoreValueFromParts(parts: Map<Column<*>, Any?>): T

    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        getRealColumns().appendTo { +it }
    }
}

/**
 * Extension of [CompositeColumn] that consists of two columns, [column1] and [column2].
 */
abstract class BiCompositeColumn<C1, C2, T>(
    protected val column1: Column<C1>,
    protected val column2: Column<C2>,
    /** Transformation that receives the column's composite value and returns the parsed values of the underlying columns. */
    val transformFromValue: (T) -> Pair<C1?, C2?>,
    /** Transformation that receives the retrieved values of [column1] and [column2] and returns a composite value. */
    val transformToValue: (Any?, Any?) -> T,
    nullable: Boolean = false
) : CompositeColumn<T>() {
    init {
        this.nullable = nullable
    }

    override fun getRealColumns(): List<Column<*>> = listOf(column1, column2)

    override fun getRealColumnsWithValues(compositeValue: T): Map<Column<*>, Any?> {
        require(compositeValue != null || nullable) {
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
        return result
    }
}
