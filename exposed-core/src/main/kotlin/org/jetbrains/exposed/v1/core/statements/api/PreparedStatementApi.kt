package org.jetbrains.exposed.v1.core.statements.api

import org.jetbrains.exposed.v1.core.ArrayColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import java.io.InputStream

/** Represents a precompiled SQL statement. */
@Suppress("TooManyFunctions")
interface PreparedStatementApi {
    /**
     * Sets the value for each column or expression in [args] into the appropriate statement parameter and
     * returns the number of parameters filled.
     */
    fun fillParameters(args: Iterable<Pair<IColumnType<*>, Any?>>): Int {
        args.forEachIndexed { index, (c, v) ->
            c.setParameter(this, index + 1, (c as IColumnType<Any>).valueToDB(v))
        }

        return args.count() + 1
    }

    operator fun set(index: Int, value: Any)

    // TODO Could we add method `set()` that could pass `columnType` (actually for r2dbc mappers now)?
    //  fun set(index: Int, value: Any, columnType: IColumnType<*>) = set(index, value)

    /** Sets the statement parameter at the [index] position to SQL NULL, if allowed wih the specified [columnType]. */
    fun setNull(index: Int, columnType: IColumnType<*>)

    /**
     * Sets the statement parameter at the [index] position to the provided [inputStream],
     * either directly as a BLOB if `setAsBlobObject` is `true` or as determined by the driver.
     */
    fun setInputStream(index: Int, inputStream: InputStream, setAsBlobObject: Boolean)

    /** Sets the statement parameter at the [index] position to the provided [array] of SQL [type]. */
    fun setArray(index: Int, type: ArrayColumnType<*, *>, array: Array<*>)
}
