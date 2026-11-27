package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet

/**
 * The `INFORMATION_SCHEMA` reads that MySQL and MariaDB answer in the same way, shared by [MySqlSchemaSupport] and
 * [MariaDbSchemaSupport]. What the two databases report differently (declared types, defaults, generated names,
 * sequences) stays with each of them.
 */
internal object MySqlFamilyCatalog {
    /** The databases that hold system objects rather than user tables. */
    val SYSTEM_DATABASES: Set<String> = setOf("information_schema", "mysql", "performance_schema", "sys")

    /** A column of a table exactly as `INFORMATION_SCHEMA.COLUMNS` reports it. */
    data class Column(
        val name: String,
        val columnType: String,
        val dataType: String,
        val default: String?,
        val extra: String,
        val generationExpression: String,
    ) {
        // `EXTRA` is `VIRTUAL GENERATED` or `STORED GENERATED` for a generated column. On MySQL it is `DEFAULT_GENERATED`
        // for a column that merely has an expression as its default, which is not a generated column
        val isGenerated: Boolean
            get() = extra.contains("VIRTUAL GENERATED", ignoreCase = true) || extra.contains("STORED GENERATED", ignoreCase = true)

        /** The `ON UPDATE ...` clause of the column in upper case, or `null` when it has none. */
        val onUpdateClause: String?
            get() = extra
                .indexOf(ON_UPDATE_CLAUSE, ignoreCase = true)
                .takeIf { it >= 0 }
                ?.let { extra.substring(it).uppercase() }
    }

    fun columns(connection: Connection, schema: String, tableName: String): List<Column> {
        return connection.query(
            "SELECT COLUMN_NAME, COLUMN_TYPE, DATA_TYPE, COLUMN_DEFAULT, EXTRA, GENERATION_EXPRESSION " +
                "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
            schema,
            tableName,
        ) { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        Column(
                            name = rs.getString("COLUMN_NAME"),
                            columnType = rs.getString("COLUMN_TYPE"),
                            dataType = rs.getString("DATA_TYPE"),
                            default = rs.getString("COLUMN_DEFAULT"),
                            extra = rs.getString("EXTRA").orEmpty(),
                            generationExpression = rs.getString("GENERATION_EXPRESSION").orEmpty(),
                        )
                    )
                }
            }
        }
    }

    /** What the generated definition cannot say about every column of [columns] that has an `ON UPDATE` clause. */
    fun onUpdateOverrides(columns: List<Column>, tableName: String): Map<String, ColumnOverride> =
        columns.mapNotNull { column ->
            val clause = column.onUpdateClause ?: return@mapNotNull null
            column.name to ColumnOverride(
                unsupportedNote = "Column '${column.name}' of table '$tableName' is declared with $clause, which the " +
                    "generated definition does not declare. If the intention is to create new schema from this definition, " +
                    "withDefinition(\"$clause\") should be manually added, with the fractional seconds precision of " +
                    "the column type that Exposed declares"
            )
        }.toMap()

    // A `UNIQUE` constraint and the index that backs it share one name
    fun uniqueConstraintNames(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return connection.query(
            "SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'UNIQUE'",
            schema,
            tableName,
        ) { rs ->
            buildMap {
                while (rs.next()) {
                    put(rs.getString("CONSTRAINT_NAME"), rs.getString("CONSTRAINT_NAME"))
                }
            }
        }
    }

    // The catalog is what Exposed's migration utilities compare a key's rules against, and it tells apart what the
    // drivers do not: connector-j reports `RESTRICT` for a rule that was not declared at all, and MariaDB Connector/J
    // leaves out keys altogether
    fun foreignKeys(connection: Connection, schema: String, tableName: String): List<ReportedForeignKeyColumn> {
        return connection.query(
            "SELECT k.CONSTRAINT_NAME, k.ORDINAL_POSITION, k.COLUMN_NAME, k.REFERENCED_TABLE_SCHEMA, k.REFERENCED_TABLE_NAME, " +
                "k.REFERENCED_COLUMN_NAME, r.UPDATE_RULE, r.DELETE_RULE " +
                "FROM information_schema.KEY_COLUMN_USAGE k " +
                "JOIN information_schema.REFERENTIAL_CONSTRAINTS r " +
                "ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME AND r.TABLE_NAME = k.TABLE_NAME " +
                "WHERE k.TABLE_SCHEMA = ? AND k.TABLE_NAME = ? AND k.REFERENCED_TABLE_NAME IS NOT NULL " +
                "ORDER BY k.CONSTRAINT_NAME, k.ORDINAL_POSITION",
            schema,
            tableName,
        ) { rs ->
            buildList {
                while (rs.next()) {
                    val name = rs.getString("CONSTRAINT_NAME")
                    add(
                        ReportedForeignKeyColumn(
                            groupKey = name,
                            name = name,
                            position = rs.getInt("ORDINAL_POSITION"),
                            columnName = rs.getString("COLUMN_NAME"),
                            targetSchema = rs.getString("REFERENCED_TABLE_SCHEMA"),
                            targetTable = rs.getString("REFERENCED_TABLE_NAME"),
                            targetColumnName = rs.getString("REFERENCED_COLUMN_NAME"),
                            deleteRule = ruleCode(rs.getString("DELETE_RULE")),
                            updateRule = ruleCode(rs.getString("UPDATE_RULE")),
                        )
                    )
                }
            }
        }
    }

    inline fun <T> Connection.query(sql: String, vararg parameters: String, read: (ResultSet) -> T): T =
        prepareStatement(sql).use { statement ->
            parameters.forEachIndexed { index, parameter -> statement.setString(index + 1, parameter) }
            statement.executeQuery().use(read)
        }

    private fun ruleCode(rule: String): Int = when (rule.uppercase()) {
        "CASCADE" -> DatabaseMetaData.importedKeyCascade
        "RESTRICT" -> DatabaseMetaData.importedKeyRestrict
        "SET NULL" -> DatabaseMetaData.importedKeySetNull
        "SET DEFAULT" -> DatabaseMetaData.importedKeySetDefault
        else -> DatabaseMetaData.importedKeyNoAction
    }

    private const val ON_UPDATE_CLAUSE: String = "on update"
}
