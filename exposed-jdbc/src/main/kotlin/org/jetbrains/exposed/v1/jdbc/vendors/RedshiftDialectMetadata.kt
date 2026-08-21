package org.jetbrains.exposed.v1.jdbc.vendors

import org.jetbrains.exposed.v1.core.Index
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/**
 * Amazon Redshift dialect metadata implementation.
 */
open class RedshiftDialectMetadata : DatabaseDialectMetadata() {
    override fun supportsLimitWithUpdateOrDelete(): Boolean = false

    @OptIn(InternalApi::class)
    override fun existingIndices(vararg tables: Table): Map<Table, List<Index>> {
        val transaction = TransactionManager.current()
        return tables.associateWith { table ->
            val schemaName = table.schemaName ?: transaction.connection.schema.ifEmpty {
                transaction.exec("SELECT current_schema()") { result ->
                    check(result.next())
                    result.getString(1)
                }.orEmpty()
            }
            val constraints = linkedMapOf<String, MutableList<String>>()
            transaction.exec(
                """
                SELECT tc.constraint_name, kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_catalog = kcu.constraint_catalog
                 AND tc.constraint_schema = kcu.constraint_schema
                 AND tc.constraint_name = kcu.constraint_name
                WHERE tc.table_schema = '${schemaName.escapeLiteral()}'
                  AND tc.table_name = '${table.nameInDatabaseCaseUnquoted().escapeLiteral()}'
                  AND tc.constraint_type = 'UNIQUE'
                ORDER BY tc.constraint_name, kcu.ordinal_position
                """.trimIndent()
            ) { result ->
                while (result.next()) {
                    constraints.getOrPut(result.getString(1)) { arrayListOf() }.add(result.getString(2))
                }
            }

            val columnsByName = table.columns.associateBy { it.name.lowercase() }
            constraints.mapNotNull { (constraintName, columnNames) ->
                columnNames.mapNotNull { columnsByName[it.lowercase()] }
                    .takeIf { it.size == columnNames.size }
                    ?.let { Index(it, unique = true, customName = constraintName) }
            }
        }
    }
}

private fun String.escapeLiteral(): String = replace("'", "''")
