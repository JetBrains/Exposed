package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.plugin.core.tables.schema.toExposedIdentifier
import java.sql.Connection

/**
 * Responsible for obtaining non-standard metadata from an H2 database.
 */
internal class H2SchemaSupport(private val identifierManager: IdentifierManagerApi) : DialectSchemaSupport {
    override val syntax: DialectSyntax = H2Syntax

    // Every foreign key is backed with an index named `CONSTRAINT_INDEX_<n>` or `<constraint>_INDEX_<n>`
    override val createsIndexForForeignKeys: Boolean = true

    override fun isSystemSchema(name: String): Boolean = name.equals("INFORMATION_SCHEMA", ignoreCase = true)

    // Reads the declared types through `SHOW COLUMNS`, mirroring the prefetch that is done by `exposed-jdbc`.
    // Unlike the JDBC `TYPE_NAME`, the declared type keeps length and precision arguments and preserves distinctions
    // such as `FLOAT` versus `DOUBLE PRECISION` or `CHARACTER` versus `CHARACTER(1)`.
    override fun declaredColumnTypes(connection: Connection, schema: String, tableName: String): Map<String, String> {
        val qualifiedName = listOf(schema, tableName).joinToString(".") {
            identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(identifierManager.toExposedIdentifier(it))
        }
        return connection.createStatement().use { statement ->
            statement.executeQuery("SHOW COLUMNS FROM $qualifiedName").use { rs ->
                buildMap {
                    while (rs.next()) {
                        put(rs.getString("FIELD"), rs.getString("TYPE"))
                    }
                }
            }
        }
    }

    override fun generatedColumns(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return connection.prepareStatement(
            "SELECT COLUMN_NAME, GENERATION_EXPRESSION FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND IS_GENERATED = 'ALWAYS'"
        ).use { statement ->
            statement.setString(1, schema)
            statement.setString(2, tableName)
            statement.executeQuery().use { rs ->
                buildMap {
                    while (rs.next()) {
                        put(rs.getString("COLUMN_NAME"), rs.getString("GENERATION_EXPRESSION").orEmpty())
                    }
                }
            }
        }
    }

    // A `UNIQUE` constraint is backed with an index named `<constraint>_INDEX_<n>` (`CONSTRAINT_INDEX_<n>` for an unnamed
    // constraint), and that is the name `getIndexInfo` reports. The constraint's own name is only in this view
    override fun uniqueConstraintNames(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return connection.prepareStatement(
            "SELECT INDEX_NAME, CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'UNIQUE'"
        ).use { statement ->
            statement.setString(1, schema)
            statement.setString(2, tableName)
            statement.executeQuery().use { rs ->
                buildMap {
                    while (rs.next()) {
                        rs.getString("INDEX_NAME")?.let { put(it, rs.getString("CONSTRAINT_NAME")) }
                    }
                }
            }
        }
    }

    override fun sequenceParameters(
        connection: Connection,
        schema: String,
        sequenceName: String,
        sequenceMaxValue: Long
    ): SequenceParameters? {
        return connection.prepareStatement(
            "SELECT START_VALUE, INCREMENT, MINIMUM_VALUE, MAXIMUM_VALUE, CYCLE_OPTION, CACHE FROM INFORMATION_SCHEMA.SEQUENCES " +
                "WHERE SEQUENCE_SCHEMA = ? AND SEQUENCE_NAME = ?"
        ).use { statement ->
            statement.setString(1, schema)
            statement.setString(2, sequenceName)
            statement.executeQuery().use { rs ->
                if (!rs.next()) return null
                SequenceParameters(
                    startWith = rs.getLong("START_VALUE").takeIf { it != IMPLIED_SEQUENCE_START },
                    incrementBy = rs.getLong("INCREMENT").takeIf { it != DEFAULT_SEQUENCE_INCREMENT },
                    minValue = rs.getLong("MINIMUM_VALUE").takeIf { it != IMPLIED_SEQUENCE_MINIMUM },
                    maxValue = rs.getLong("MAXIMUM_VALUE").takeIf { it != sequenceMaxValue },
                    cycle = true.takeIf { rs.getString("CYCLE_OPTION") == "YES" },
                    cache = rs.getLong("CACHE").takeIf { it != DEFAULT_SEQUENCE_CACHE },
                ).takeUnless { it.isDefault }
            }
        }
    }

    // Identity columns never own a separate sequence object
    override fun ownedSequences(connection: Connection, schema: String, tableName: String): Map<String, String> = emptyMap()

    // Enumeration values are directly in the column type, see [H2Syntax.enumerationValues]
    override fun enumerationTypes(connection: Connection, schema: String): Map<String, List<String>> = emptyMap()

    private companion object {
        const val IMPLIED_SEQUENCE_START: Long = 1
        const val IMPLIED_SEQUENCE_MINIMUM: Long = 1
        const val DEFAULT_SEQUENCE_INCREMENT: Long = 1
        const val DEFAULT_SEQUENCE_CACHE: Long = 32
    }
}
