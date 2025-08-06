package org.jetbrains.exposed.v1.core.statements.api

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.H2Dialect.H2CompatibilityMode
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.core.vendors.h2Mode
import java.sql.Types

/**
 * Base class responsible for shared utility methods needed for retrieving and storing information about
 * the underlying driver and associated [database].
 */
abstract class ExposedDatabaseMetadata(val database: String) {
    /** Clears and resets any stored information about the database's current schema to default values. */
    abstract fun resetCurrentScheme()

    @Suppress("ForbiddenComment")
    // TODO: THIS should become protected after the usage in DatabaseDialect is fully deprecated
    /**
     * Returns the corresponding [ReferenceOption] for the specified [refOption] result,
     * or `null` if the database result is an invalid string without a corresponding match.
     */
    @InternalApi
    abstract fun resolveReferenceOption(refOption: String): ReferenceOption?

    /** Clears any cached values. */
    abstract fun cleanCache()

    /** The database-specific and metadata-reliant implementation of [IdentifierManagerApi]. */
    abstract val identifierManager: IdentifierManagerApi

    fun areEquivalentColumnTypes(columnMetadataSqlType: String, columnMetadataType: Int, columnType: String): Boolean {
        return when {
            columnMetadataSqlType.equals(columnType, ignoreCase = true) -> true
            currentDialect is H2Dialect -> areEquivalentColumnTypesH2(columnMetadataSqlType, columnMetadataType, columnType)
            else -> false
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun areEquivalentColumnTypesH2(columnMetadataSqlType: String, columnMetadataJdbcType: Int, columnType: String): Boolean {
        val dialect = currentDialect

        val columnMetadataSqlType = columnMetadataSqlType.uppercase()
        val columnType = columnType.uppercase()

        if (columnMetadataJdbcType == Types.ARRAY) {
            val baseType = columnMetadataSqlType.substringBefore(" ARRAY")
            return areEquivalentColumnTypes(baseType, Types.OTHER, columnType.substringBefore(" ARRAY")) &&
                areEquivalentColumnTypes(columnMetadataSqlType.replaceBefore("ARRAY", ""), Types.OTHER, columnType.replaceBefore("ARRAY", ""))
        }

        if (columnType == "TEXT" && columnMetadataSqlType == "VARCHAR") {
            return true
        }

        if (listOf(columnMetadataSqlType, columnType).all { it.matches(Regex("VARCHAR(?:\\((?:MAX|\\d+)\\))?")) }) {
            return true
        }

        if (listOf(columnMetadataSqlType, columnType).all { it.matches(Regex("VARBINARY(?:\\((?:MAX|\\d+)\\))?")) }) {
            return true
        }

        return when (dialect.h2Mode) {
            H2CompatibilityMode.PostgreSQL -> {
                when {
                    // Auto-increment difference is dealt with elsewhere
                    (columnType == "SERIAL" && columnMetadataSqlType == "INT") || (columnType == "BIGSERIAL" && columnMetadataSqlType == "BIGINT") -> true
                    else -> false
                }
            }
            H2CompatibilityMode.Oracle -> {
                when {
                    columnType == "DATE" && columnMetadataSqlType == "TIMESTAMP(0)" -> true
                    // Unlike Oracle, H2 Oracle mode does not distinguish between VARCHAR2(4000) and VARCHAR2(4000 CHAR).
                    // It treats the length as a character count and does not enforce a separate byte limit.
                    listOf(columnMetadataSqlType, columnType).all { it.matches(Regex("VARCHAR2(?:\\((?:MAX|\\d+)(?:\\s+CHAR)?\\))?")) } -> true
                    else -> {
                        // H2 maps NUMBER to NUMERIC
                        val numberRegex = Regex("NUMBER(?:\\((\\d+)(?:,\\s?(\\d+))?\\))?")
                        val numericRegex = Regex("NUMERIC(?:\\((\\d+)(?:,\\s?(\\d+))?\\))?")
                        val numberMatch = numberRegex.find(columnType)
                        val numericMatch = numericRegex.find(columnMetadataSqlType)
                        if (numberMatch != null && numericMatch != null) {
                            numberMatch.groupValues[1] == numericMatch.groupValues[1] // compare precision
                        } else {
                            false
                        }
                    }
                }
            }
            H2CompatibilityMode.SQLServer ->
                when {
                    columnType.equals("uniqueidentifier", ignoreCase = true) && columnMetadataSqlType == "UUID" -> true
                    // Auto-increment difference is dealt with elsewhere
                    columnType.contains(" IDENTITY") ->
                        areEquivalentColumnTypes(columnMetadataSqlType, columnMetadataJdbcType, columnType.substringBefore(" IDENTITY"))
                    // H2 maps DATETIME2 to TIMESTAMP
                    columnType.matches(Regex("DATETIME2(?:\\(\\d+\\))?")) &&
                        columnMetadataSqlType.matches(Regex("TIMESTAMP(?:\\(\\d+\\))?")) -> true
                    // H2 maps NVARCHAR to VARCHAR
                    columnType.matches(Regex("NVARCHAR(?:\\((\\d+|MAX)\\))?")) &&
                        columnMetadataSqlType.matches(Regex("VARCHAR(?:\\((\\d+|MAX)\\))?")) -> true
                    else -> false
                }
            null, H2CompatibilityMode.MySQL, H2CompatibilityMode.MariaDB ->
                when {
                    // Auto-increment difference is dealt with elsewhere
                    columnType.contains(" AUTO_INCREMENT") ->
                        areEquivalentColumnTypes(columnMetadataSqlType, columnMetadataJdbcType, columnType.substringBefore(" AUTO_INCREMENT"))
                    // H2 maps DATETIME to TIMESTAMP
                    columnType.matches(Regex("DATETIME(?:\\(\\d+\\))?")) &&
                        columnMetadataSqlType.matches(Regex("TIMESTAMP(?:\\(\\d+\\))?")) -> true
                    else -> false
                }
        }
    }
}
