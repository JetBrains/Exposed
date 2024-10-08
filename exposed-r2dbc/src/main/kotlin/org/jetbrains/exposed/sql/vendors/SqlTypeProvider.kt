package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.ReferenceOption

/** Base class responsible for providing details and string builders for all supported SQL types in a database. */
@Suppress("MagicNumber")
abstract class SqlTypeProvider {
    /** A mapping of available [ReferenceOption]s to the matching database constant. */
    abstract val referenceOptions: Map<ReferenceOption, Int>

    /** A string representation of the column, function, or value to use when querying the column size for a numeric type. */
    open val numericPrecision: String
        get() = "NUMERIC_PRECISION"

    /** A string representation of the column, function, or value to use when querying the column size for a character type. */
    open val characterPrecision: String
        get() = "CHARACTER_MAXIMUM_LENGTH"

    /** A string representation of the column, function, or value to use when querying the maximum (undefined) column size. */
    open val maxPrecision: String
        get() = Int.MAX_VALUE.toString()

    /** Any additional [DataType] specific to a database, which is not a constant in the standard java.sql.Types. */
    open val additionalTypes: Set<DataType>
        get() = emptySet()

    /** Data for the generic SQL type ARRAY. */
    open val arrayType: DataType
        get() = DataType("ARRAY", 2003, "NULL")

    /** Data for the generic SQL type BIGINT. */
    open val bigIntType: DataType
        get() = DataType("BIGINT", -5, numericPrecision)

    /** Data for the generic SQL type BINARY. */
    open val binaryType: DataType
        get() = DataType("BINARY", -2, characterPrecision)

    /** Data for the generic SQL type BIT. */
    open val bitType: DataType
        get() = DataType("BIT", -7, numericPrecision)

    /** Data for the generic SQL type BLOB. */
    open val blobType: DataType
        get() = DataType("BLOB", 2004, characterPrecision)

    /** Data for the generic SQL type BOOLEAN. */
    open val booleanType: DataType
        get() = DataType("BOOLEAN", 16, numericPrecision)

    /** Data for the generic SQL type CHAR. */
    open val charType: DataType
        get() = DataType("CHAR", 1, characterPrecision)

    /** Data for the generic SQL type CLOB. */
    open val clobType: DataType
        get() = DataType("CLOB", 2005, characterPrecision)

    /** Data for the generic SQL type DATALINK. */
    open val dataLinkType: DataType
        get() = DataType("DATALINK", 70, "NULL")

    /** Data for the generic SQL type DATE. */
    open val dateType: DataType
        get() = DataType("DATE", 91, "10")

    /** Data for the generic SQL type DECIMAL. */
    open val decimalType: DataType
        get() = DataType("DECIMAL", 3, numericPrecision)

    /** Data for the generic SQL type DISTINCT. */
    open val distinctType: DataType
        get() = DataType("DISTINCT", 2001, "NULL")

    /** Data for the generic SQL type DOUBLE. */
    open val doubleType: DataType
        get() = DataType("DOUBLE", 8, numericPrecision)

    /** Data for the generic SQL type FLOAT. */
    open val floatType: DataType
        get() = DataType("FLOAT", 6, numericPrecision)

    /** Data for the generic SQL type INTEGER. */
    open val integerType: DataType
        get() = DataType("INTEGER", 4, numericPrecision)

    /** Data for the generic SQL type JAVA OBJECT. */
    open val javaObjectType: DataType
        get() = DataType("JAVA_OBJECT", 2000, characterPrecision)

    /** Data for the generic SQL type LONGNVARCHAR. */
    open val longNVarcharType: DataType
        get() = DataType("LONGNVARCHAR", -16, characterPrecision)

    /** Data for the generic SQL type LONGVARBINARY. */
    open val longVarbinaryType: DataType
        get() = DataType("LONGVARBINARY", -4, characterPrecision)

    /** Data for the generic SQL type LONGVARCHAR. */
    open val longVarcharType: DataType
        get() = DataType("LONGVARCHAR", -1, characterPrecision)

    /** Data for the generic SQL type NCHAR. */
    open val nCharType: DataType
        get() = DataType("NCHAR", -15, characterPrecision)

    /** Data for the generic SQL type NCLOB. */
    open val nClobType: DataType
        get() = DataType("NCLOB", 2011, characterPrecision)

    /** Data for the generic SQL type NULL. */
    open val nullType: DataType
        get() = DataType("NULL", 0, "NULL")

    /** Data for the generic SQL type NUMERIC. */
    open val numericType: DataType
        get() = DataType("NUMERIC", 2, numericPrecision)

    /** Data for the generic SQL type NVARCHAR. */
    open val nVarcharType: DataType
        get() = DataType("NVARCHAR", -9, characterPrecision)

    /** Data for the generic SQL type OTHER. */
    open val otherType: DataType
        get() = DataType("OTHER", 1111, characterPrecision)

    /** Data for the generic SQL type REAL. */
    open val realType: DataType
        get() = DataType("REAL", 7, numericPrecision)

    /** Data for the generic SQL type REF. */
    open val refType: DataType
        get() = DataType("REF", 2006, "NULL")

    /** Data for the generic SQL type REF CURSOR. */
    open val refCursorType: DataType
        get() = DataType("REF_CURSOR", 2012, "NULL")

    /** Data for the generic SQL type ROWID. */
    open val rowIdType: DataType
        get() = DataType("ROWID", -8, numericPrecision)

    /** Data for the generic SQL type SMALLINT. */
    open val smallIntType: DataType
        get() = DataType("SMALLINT", 5, numericPrecision)

    /** Data for the generic SQL type SQLXML. */
    open val sqlXmlType: DataType
        get() = DataType("SQLXML", 2009, characterPrecision)

    /** Data for the generic SQL type STRUCT. */
    open val structType: DataType
        get() = DataType("STRUCT", 2002, "NULL")

    /** Data for the generic SQL type TIME. */
    open val timeType: DataType
        get() = DataType("TIME", 92, "8")

    /** Data for the generic SQL type TIMESTAMP. */
    open val timestampType: DataType
        get() = DataType("TIMESTAMP", 93, "29")

    /** Data for the generic SQL type TIMESTAMP WITH TIMEZONE. */
    open val timestampWithTimezoneType: DataType
        get() = DataType("TIMESTAMP_WITH_TIMEZONE", 2014, "35")

    /** Data for the generic SQL type TIME WITH TIMEZONE. */
    open val timeWithTimezoneType: DataType
        get() = DataType("TIME_WITH_TIMEZONE", 2013, numericPrecision)

    /** Data for the generic SQL type TINYINT. */
    open val tinyIntType: DataType
        get() = DataType("TINYINT", -6, numericPrecision)

    /** Data for the generic SQL type VARBINARY. */
    open val varbinaryType: DataType
        get() = DataType("VARBINARY", -3, characterPrecision)

    /** Data for the generic SQL type VARCHAR. */
    open val varcharType: DataType
        get() = DataType("VARCHAR", 12, characterPrecision)

    /**
     * Appends a switch clause to a [stringBuilder] for the value stored in [columnName]. Each branch uses a value
     * mapped in [referenceOptions].
     */
    internal open fun appendReferenceOptions(columnName: String, alias: String, stringBuilder: StringBuilder) {
        with(stringBuilder) {
            append("CASE $columnName ")
            referenceOptions.forEach { (option, value) ->
                append("WHEN '$option' THEN $value ")
            }
            append("ELSE NULL END $alias")
        }
    }

    /**
     * Appends a switch clause to a [stringBuilder] for the value stored in [columnName]. Each branch uses available
     * SQL types mapped to their [DataType.code], so as to query the code associated with the type.
     */
    internal fun appendDataTypes(columnName: String, alias: String, stringBuilder: StringBuilder) {
        with(stringBuilder) {
            append("CASE UPPER($columnName) ")
            allDataTypes.forEach {
                append("WHEN '${it.name}' THEN ${it.code} ")
            }
            append("ELSE 0 END $alias")
        }
    }

    /**
     * Appends a switch clause to a [stringBuilder] for the value stored in [columnName]. Each branch uses available
     * SQL types mapped to their [DataType.precision], so as to query the column size associated with the type.
     */
    internal fun appendDataPrecisions(columnName: String, alias: String, stringBuilder: StringBuilder) {
        with(stringBuilder) {
            append("CASE UPPER($columnName) ")
            allDataTypes.forEach {
                append("WHEN '${it.name}' THEN ${it.precision} ")
            }
            append("ELSE NULL END $alias")
        }
    }

    /**
     * Class representing an SQL type in a database.
     *
     * @property name The string representation of this SQL type. This value should be unique in a database.
     * @property code The type code or constant that identifies this SQL type.
     * @property precision The string representation of the column, function, or value to use when querying the
     * column size for this SQL type.
     */
    data class DataType(val name: String, val code: Int, val precision: String) {
        override fun equals(other: Any?): Boolean {
            if (other !is DataType) return false

            return this.name == other.name
        }

        override fun hashCode(): Int = name.hashCode()
    }

    private val allDataTypes: Set<DataType> by lazy {
        additionalTypes + setOf(
            arrayType, bigIntType, bitType, binaryType, blobType, booleanType, charType, clobType,
            dataLinkType, dateType, decimalType, distinctType, doubleType, floatType, integerType, javaObjectType,
            longNVarcharType, longVarbinaryType, longVarcharType, nCharType, nClobType, nullType, numericType, nVarcharType,
            otherType, realType, refType, refCursorType, rowIdType, smallIntType, sqlXmlType, structType,
            timeType, timestampType, timestampWithTimezoneType, timeWithTimezoneType, tinyIntType,
            varbinaryType, varcharType
        )
    }
}
