package org.jetbrains.exposed.v1.sql.vendors

import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.sql.*
import org.jetbrains.exposed.v1.sql.Function
import java.nio.ByteBuffer
import java.util.*

/**
 * Provides definitions for all the supported SQL data types.
 * By default, definitions from the SQL standard are provided but if a vendor doesn't support a specific type, or it is
 * implemented differently, the corresponding function should be overridden.
 */
abstract class DataTypeProvider {
    // Numeric types

    /** Numeric type for storing 1-byte integers. */
    open fun byteType(): String = "TINYINT"

    /** Numeric type for storing 1-byte unsigned integers.
     *
     * **Note:** If the database being used is not MySQL, MariaDB, or SQL Server, this will represent the 2-byte
     * integer type.
     */
    open fun ubyteType(): String = "SMALLINT"

    /** Numeric type for storing 2-byte integers. */
    open fun shortType(): String = "SMALLINT"

    /** Numeric type for storing 2-byte unsigned integers.
     *
     * **Note:** If the database being used is not MySQL or MariaDB, this will represent the 4-byte integer type.
     */
    open fun ushortType(): String = "INT"

    /** Numeric type for storing 4-byte integers. */
    open fun integerType(): String = "INT"

    /** Numeric type for storing 4-byte unsigned integers.
     *
     * **Note:** If the database being used is not MySQL or MariaDB, this will represent the 8-byte integer type.
     */
    open fun uintegerType(): String = "BIGINT"

    /** Numeric type for storing 4-byte integers, marked as auto-increment. */
    open fun integerAutoincType(): String = "INT AUTO_INCREMENT"

    /** Numeric type for storing 4-byte unsigned integers, marked as auto-increment.
     *
     * **Note:** If the database being used is not MySQL or MariaDB, this will represent the 8-byte integer type.
     */
    open fun uintegerAutoincType(): String = "BIGINT AUTO_INCREMENT"

    /** Numeric type for storing 8-byte integers. */
    open fun longType(): String = "BIGINT"

    /** Numeric type for storing 8-byte unsigned integers. */
    open fun ulongType(): String = "NUMERIC(20)"

    /** Numeric type for storing 8-byte integers, and marked as auto-increment. */
    open fun longAutoincType(): String = "BIGINT AUTO_INCREMENT"

    /** Numeric type for storing 8-byte unsigned integers, marked as auto-increment. */
    open fun ulongAutoincType(): String = "NUMERIC(20) AUTO_INCREMENT"

    /** Numeric type for storing 4-byte (single precision) floating-point numbers. */
    open fun floatType(): String = "FLOAT"

    /** Numeric type for storing 8-byte (double precision) floating-point numbers. */
    open fun doubleType(): String = "DOUBLE PRECISION"

    // Character types

    /** Character type for storing strings of variable length up to a maximum. */
    open fun varcharType(colLength: Int): String = "VARCHAR($colLength)"

    /** Character type for storing strings of variable length.
     * Some database (postgresql) use the same data type name to provide virtually _unlimited_ length. */
    open fun textType(): String = "TEXT"

    /** Character type for storing strings of _medium_ length. */
    open fun mediumTextType(): String = "TEXT"

    /** Character type for storing strings of variable and _large_ length. */
    open fun largeTextType(): String = "TEXT"

    // Binary data types

    /** Binary type for storing binary strings of variable and _unlimited_ length. */
    abstract fun binaryType(): String

    /** Binary type for storing binary strings of a specific [length]. */
    open fun binaryType(length: Int): String = if (length == Int.MAX_VALUE) "VARBINARY(MAX)" else "VARBINARY($length)"

    /** Binary type for storing BLOBs. */
    open fun blobType(): String = "BLOB"

    /** Binary type for storing [UUID]. */
    open fun uuidType(): String = "BINARY(16)"

    /** Returns a database-compatible object from the specified UUID [value]. */
    @Suppress("MagicNumber")
    open fun uuidToDB(value: UUID): Any =
        ByteBuffer.allocate(16).putLong(value.mostSignificantBits).putLong(value.leastSignificantBits).array()

    // Date/Time types

    /** Data type for storing both date and time without a time zone. */
    open fun dateTimeType(): String = "DATETIME"

    open fun timestampType(): String = dateTimeType()

    /** Data type for storing both date and time with a time zone. */
    open fun timestampWithTimeZoneType(): String = "TIMESTAMP WITH TIME ZONE"

    /** Time type for storing time without a time zone. */
    open fun timeType(): String = "TIME"

    /** Data type for storing date without time or a time zone. */
    open fun dateType(): String = "DATE"

    // Boolean type

    /** Data type for storing boolean values. */
    open fun booleanType(): String = "BOOLEAN"

    /** Returns the SQL representation of the specified [bool] value. */
    open fun booleanToStatementString(bool: Boolean): String = bool.toString().uppercase()

    /** Returns the boolean value of the specified SQL [value]. */
    open fun booleanFromStringToBoolean(value: String): Boolean = value.toBoolean()

    // JSON types

    /** Data type for storing JSON in a non-binary text format. */
    open fun jsonType(): String = "JSON"

    /** Data type for storing JSON in a decomposed binary format. */
    open fun jsonBType(): String =
        throw UnsupportedByDialectException("This vendor does not support binary JSON data type", currentDialect)

    // Misc.

    /** Returns the SQL representation of the specified expression, for it to be used as a column default value. */
    open fun processForDefaultValue(e: Expression<*>): String = when {
        e is LiteralOp<*> -> (e.columnType as IColumnType<Any?>).valueAsDefaultString(e.value)
        e is Function<*> -> "$e"
        currentDialect is MysqlDialect -> "$e"
        currentDialect is SQLServerDialect -> "$e"
        else -> "($e)"
    }

    /** Returns the SQL representation of the specified [expression], to be used in an ORDER BY clause. */
    open fun precessOrderByClause(queryBuilder: QueryBuilder, expression: Expression<*>, sortOrder: SortOrder) {
        queryBuilder.append((expression as? IExpressionAlias<*>)?.alias ?: expression, " ", sortOrder.code)
    }

    /** Returns the hex-encoded value to be inserted into the database. */
    abstract fun hexToDb(hexString: String): String
}
