package org.jetbrains.exposed.v1.plugin.core.tables.schema

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.statements.api.ExposedMetadataUtils
import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.core.vendors.ColumnMetadata
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcResult
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata
import org.jetbrains.exposed.v1.plugin.core.migration.MigrationLogger
import org.jetbrains.exposed.v1.plugin.core.tables.ColumnMapping
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.DialectSchemaSupport
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.DialectSyntax
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.SequenceReference
import org.jetbrains.exposed.v1.plugin.core.tables.warn
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet

// TODO Reassess how much here might be shared by exposed-jdbc and attempt to reduce?
/**
 * Reads standard metadata for tables of one or more schemas of a JDBC [connection] into [TableMetadata] instances.
 *
 * By default, only the schema that the connection currently points to is read.
 * [schemaPatterns] may name other schemas, or match several with glob patterns, but system schemas are never read.
 * This must always be invoked inside an active transaction, so that the dialect-aware column metadata helpers
 * and the identifier manager can be reused.
 */
@OptIn(InternalApi::class)
internal class SchemaReader(
    private val connection: Connection,
    private val schemaPatterns: List<String>,
    private val logger: MigrationLogger,
    columnMappings: List<ColumnMapping> = emptyList(),
) {
    private val metadata: DatabaseMetaData = connection.metaData
    private val catalog: String? = connection.catalog
    private val currentSchema: String? = connection.schema
    private val identifierManager: IdentifierManagerApi = TransactionManager.current().db.identifierManager
    private val dialectSupport: DialectSchemaSupport = DialectSchemaSupport.forDialect(currentDialect, identifierManager)
    private val mappings: ColumnMappingMatcher = ColumnMappingMatcher(columnMappings, identifierManager)
    private val sequencesInUse = hashSetOf<String>()
    private val enumerationTypesBySchema = hashMapOf<String, Map<String, List<String>>>()

    /**
     * Lists every user table in the searched schemas, sorted by schema and table name (ignoring case), without reading
     * any column metadata. Tables of the connection's current schema come first and carry no schema prefix.
     *
     * @throws IllegalStateException if a configured column mapping names a table that is not among them.
     */
    fun listTables(): List<TableReference> {
        val tables = resolveSchemas().flatMap { schema ->
            val schemaName = schema.takeIf { it != currentSchema }?.let(identifierManager::toExposedIdentifier)
            val tableNames = metadata.getTables(catalog, schema, "%", arrayOf("TABLE")).use { rs ->
                rs.collect { getString("TABLE_NAME") }
            }
            tableNames.sortedWith(String.CASE_INSENSITIVE_ORDER).map { tableName ->
                TableReference(
                    schema,
                    tableName,
                    schemaName = schemaName,
                    simpleName = identifierManager.toExposedIdentifier(tableName),
                )
            }
        }
        mappings.checkTablesExist(tables)
        return tables
    }

    /**
     * Reads the full metadata of [tables], in the given order, together with any sequences that none of them references.
     *
     * @throws IllegalStateException if a configured column mapping matches no column, or more than one, of these tables.
     */
    fun readAllTables(tables: List<TableReference>): SchemaSnapshot {
        val tableMetadata = tables.map(::readTable)
        mappings.checkColumnsExist(tableMetadata)
        val unreferencedSequences = currentDialectMetadata.sequences().filterNot { it in sequencesInUse }.sorted()
        return SchemaSnapshot(tableMetadata, unreferencedSequences, dialectSupport.syntax)
    }

    /**
     * Resolves [schemaPatterns] to the existing schema names they match, excluding system schemas.
     * Without patterns, only the connection's current schema is searched. Patterns are matched both as reported and in
     * the database's default identifier case, so that (on H2 for example) `sales*` matches `SALES` and `SALES_ARCHIVE`.
     */
    private fun resolveSchemas(): List<String> {
        // a driver that reports no current schema gets the JDBC "any schema" pattern instead of an empty name
        if (schemaPatterns.isEmpty()) return listOf(currentSchema ?: "%")

        val resolved = linkedSetOf<String>()
        schemaPatterns.forEach { pattern ->
            val literal = pattern.trim().removeSurrounding(identifierManager.quoteString)
            val candidates = listOf(literal, identifierManager.inProperCase(literal)).distinct()
            val matches = candidates.flatMap { candidate ->
                metadata.getSchemas(catalog, candidate.globToSearchPattern()).use { rs ->
                    rs.collect { getString("TABLE_SCHEM") }
                }
            }.filterNot(dialectSupport::isSystemSchema)
            if (matches.isEmpty()) {
                logger.warn("No schema matches '$pattern'; it was ignored")
            }
            resolved.addAll(matches)
        }
        // the current schema is generated without a prefix, so it must be listed first for a stable, readable order
        return resolved.sortedWith(compareBy<String> { it != currentSchema }.thenBy(String.CASE_INSENSITIVE_ORDER) { it })
    }

    /**
     * Converts a glob pattern (`*` matches any sequence of characters, `?` a single character) to the `LIKE`-style
     * search pattern expected by [DatabaseMetaData]. The `%` and `_` wildcards of that syntax are escaped so that
     * a literal schema name such as `SALES_ARCHIVE` matches only itself.
     */
    private fun String.globToSearchPattern(): String {
        val escape = metadata.searchStringEscape.orEmpty()
        return buildString {
            for (char in this@globToSearchPattern) {
                when (char) {
                    '*' -> append('%')
                    '?' -> append('_')
                    '%', '_' -> append(escape).append(char)
                    else -> append(char)
                }
            }
        }
    }

    private fun readTable(table: TableReference): TableMetadata {
        val (schema, tableName) = table
        val primaryKey = readPrimaryKey(schema, tableName)
        val (singleColumnForeignKeys, compositeForeignKeys) = readForeignKeys(schema, tableName)
            .partition { it.columnNames.size == 1 }
        val foreignKeyColumnSets = (singleColumnForeignKeys + compositeForeignKeys).map { it.columnNames.toSet() }
        val (singleColumnIndices, compositeIndices) = readIndices(schema, tableName, primaryKey, foreignKeyColumnSets)
            .partition { it.columnNames.size == 1 }
        val generatedColumns = dialectSupport.generatedColumns(connection, schema, tableName)
        val ownedSequences = dialectSupport.ownedSequences(connection, schema, tableName)
        sequencesInUse.addAll(ownedSequences.values)
        val enumerationTypes = enumerationTypesBySchema.getOrPut(schema) {
            dialectSupport.enumerationTypes(connection, schema)
        }
        val columns = readColumns(schema, tableName).map { (column, reportedDefault) ->
            val name = identifierManager.toExposedIdentifier(column.name)
            val sequence = reportedDefault?.let(dialectSupport.syntax::sequenceInDefault)
            val ownsSequence = sequence != null && ownedSequences[column.name] == sequence.name
            val metadata = when {
                ownsSequence -> column.copy(name = name, autoIncrement = true)
                sequence != null -> column.copy(name = name, autoIncrement = false)
                else -> column.copy(name = name)
            }
            val referencedSequence = sequence?.takeUnless { ownsSequence }?.also { sequencesInUse.add(it.name) }
            ColumnInfo(
                metadata = metadata,
                sequenceName = referencedSequence?.toExposedSequenceName(),
                sequenceParameters = referencedSequence?.let {
                    dialectSupport.sequenceParameters(
                        connection,
                        it.schema ?: schema,
                        it.name,
                        currentDialect.sequenceMaxValue
                    )
                },
                generationExpression = generatedColumns[column.name],
                indices = singleColumnIndices.filter { it.columnNames == listOf(name) },
                mapping = mappings.mappingFor(table, column.name),
                enumeration = enumerationTypes[column.sqlType]?.let {
                    EnumerationType(
                        sql = column.sqlType,
                        values = it
                    )
                },
                reportedDefault = reportedDefault?.takeUnless {
                    it.equals(
                        "NULL",
                        ignoreCase = true
                    ) || metadata.autoIncrement || sequence != null
                },
                foreignKey = singleColumnForeignKeys.firstOrNull { it.columnNames == listOf(name) },
            )
        }
        return TableMetadata(
            reference = table,
            columns = columns,
            primaryKey = primaryKey,
            compositeIndices = compositeIndices,
            compositeForeignKeys = compositeForeignKeys,
        )
    }

    private fun readForeignKeys(schema: String, tableName: String): List<ForeignKeyInfo> {
        val rows = metadata.getImportedKeys(catalog, schema, tableName).use { rs ->
            rs.collect {
                ForeignKeyRow(
                    name = getString("FK_NAME").orEmpty(),
                    position = getShort("KEY_SEQ").toInt(),
                    columnName = getString("FKCOLUMN_NAME"),
                    targetSchema = getString("PKTABLE_SCHEM").orEmpty(),
                    targetTable = getString("PKTABLE_NAME"),
                    targetColumnName = getString("PKCOLUMN_NAME"),
                    deleteRule = getShort("DELETE_RULE").toInt(),
                    updateRule = getShort("UPDATE_RULE").toInt(),
                )
            }
        }
        val defaultRule = currentDialect.defaultReferenceOption
        return rows
            .groupBy { it.name }
            .map { (name, keyRows) ->
                val ordered = keyRows.sortedBy { it.position }
                val columnNames = ordered.map { it.columnName }
                val targetColumnNames = ordered.map { it.targetColumnName }
                ForeignKeyInfo(
                    customName = name.takeIf { it.isCustomForeignKeyName(tableName, columnNames, targetColumnNames) }
                        ?.let(identifierManager::toExposedIdentifier),
                    columnNames = columnNames.map(identifierManager::toExposedIdentifier),
                    targetSchema = ordered.first().targetSchema,
                    targetTable = ordered.first().targetTable,
                    targetColumnNames = targetColumnNames.map(identifierManager::toExposedIdentifier),
                    onDelete = dialectSupport.syntax.referenceOption(ordered.first().deleteRule)
                        .takeIf { it != defaultRule },
                    onUpdate = dialectSupport.syntax.referenceOption(ordered.first().updateRule)
                        .takeIf { it != defaultRule },
                )
            }
            .sortedBy { it.customName ?: it.columnNames.joinToString() }
    }

    /** A column with its raw name, plus the default, exactly as the driver reports it in `COLUMN_DEF`. */
    private data class ReadColumn(val metadata: ColumnMetadata, val reportedDefault: String?)

    private fun readColumns(schema: String, tableName: String): List<ReadColumn> {
        val declaredColumnTypes = dialectSupport.declaredColumnTypes(connection, schema, tableName)
        return metadata.getColumns(catalog, schema, tableName, "%").use { rs ->
            val row = JdbcResult(rs)
            rs.collect {
                val column = with(ExposedMetadataUtils) { row.asColumnMetadata(declaredColumnTypes) }
                ReadColumn(column.withDeclaredType(declaredColumnTypes), getString("COLUMN_DEF"))
            }
        }
    }

    private fun ColumnMetadata.withDeclaredType(declaredColumnTypes: Map<String, String>): ColumnMetadata {
        if (sqlType.isNotEmpty()) return this
        val declaredType = declaredColumnTypes[name] ?: return this
        return copy(sqlType = declaredType)
    }

    private fun SequenceReference.toExposedSequenceName(): String {
        val sequenceName = identifierManager.toExposedIdentifier(name)
        return if (schema == null || schema.equals(currentSchema, ignoreCase = true)) {
            sequenceName
        } else {
            "${identifierManager.toExposedIdentifier(schema)}.$sequenceName"
        }
    }

    private fun readPrimaryKey(schema: String, tableName: String): PrimaryKeyInfo? {
        val keyColumns = metadata.getPrimaryKeys(catalog, schema, tableName).use { rs ->
            rs.collect { Triple(getString("PK_NAME").orEmpty(), getShort("KEY_SEQ").toInt(), getString("COLUMN_NAME")) }
        }
        if (keyColumns.isEmpty()) return null
        val keyName = keyColumns.first().first
        val rawColumnNames = keyColumns.sortedBy { it.second }.map { it.third }
        return PrimaryKeyInfo(
            columnNames = rawColumnNames.map(identifierManager::toExposedIdentifier),
            customName = keyName.takeIf { it.isCustomPrimaryKeyName(tableName, rawColumnNames) }
                ?.let(identifierManager::toExposedIdentifier),
        )
    }

    private fun readIndices(
        schema: String,
        tableName: String,
        primaryKey: PrimaryKeyInfo?,
        foreignKeyColumnSets: List<Set<String>>,
    ): List<IndexMetadata> {
        val rows = metadata.getIndexInfo(catalog, schema, tableName, false, false).use { rs ->
            rs.collect {
                val name = getString("INDEX_NAME")
                val columnName = getString("COLUMN_NAME")
                if (name == null || columnName == null) {
                    null
                } else {
                    IndexRow(
                        name,
                        unique = !getBoolean("NON_UNIQUE"),
                        position = getShort("ORDINAL_POSITION").toInt(),
                        columnName
                    )
                }
            }
        }
        val constraintNames = dialectSupport.uniqueConstraintNames(connection, schema, tableName)
        val primaryKeyColumns = primaryKey?.columnNames?.toSet().orEmpty()
        return rows
            .groupBy { it.name }
            .map { (indexName, indexRows) ->
                val columnNames = indexRows.sortedBy { it.position }.map { it.columnName }
                val unique = indexRows.first().unique
                val constraintName = constraintNames[indexName]
                val name = constraintName ?: indexName
                IndexMetadata(
                    name = identifierManager.toExposedIdentifier(name),
                    columnNames = columnNames.map(identifierManager::toExposedIdentifier),
                    unique = unique,
                    isDefaultName = name.isDefaultIndexName(tableName, columnNames, unique) ||
                        (
                            constraintName != null && dialectSupport.syntax.isGeneratedConstraintName(
                                constraintName,
                                tableName,
                                columnNames
                            )
                            ),
                )
            }
            .filterNot { it.unique && it.columnNames.toSet() == primaryKeyColumns }
            .filterNot { dialectSupport.createsIndexForForeignKeys && it.columnNames.toSet() in foreignKeyColumnSets }
            .sortedBy { it.name }
    }

    private fun String.isExposedDefaultName(defaultName: String): Boolean = this == identifierManager.inProperCase(defaultName)

    private fun String.isCustomConstraintName(defaultName: String, tableName: String, columnNames: List<String>): Boolean =
        isNotEmpty() && !dialectSupport.syntax.isGeneratedConstraintName(this, tableName, columnNames) && !isExposedDefaultName(defaultName)

    private fun String.isCustomPrimaryKeyName(tableName: String, columnNames: List<String>): Boolean =
        isCustomConstraintName("pk_$tableName", tableName, columnNames)

    private fun String.isCustomForeignKeyName(
        tableName: String,
        columnNames: List<String>,
        targetColumnNames: List<String>
    ): Boolean =
        isCustomConstraintName(
            "fk_${tableName}_${columnNames.joinToString("_")}__${targetColumnNames.joinToString("_")}",
            tableName,
            columnNames
        )

    private fun String.isDefaultIndexName(tableName: String, columnNames: List<String>, unique: Boolean): Boolean {
        if (!identifierManager.isInDefaultCase(tableName) || columnNames.any { !identifierManager.isInDefaultCase(it) }) {
            return false
        }
        val suffix = if (unique) "_unique" else ""
        return isExposedDefaultName("${tableName}_${columnNames.joinToString("_")}$suffix")
    }

    /** Index metadata directly from a ResultSet. */
    private data class IndexRow(val name: String, val unique: Boolean, val position: Int, val columnName: String)

    /** Foreign key metadata directly from a ResultSet. */
    private data class ForeignKeyRow(
        val name: String,
        val position: Int,
        val columnName: String,
        val targetSchema: String,
        val targetTable: String,
        val targetColumnName: String,
        val deleteRule: Int,
        val updateRule: Int,
    )

    private fun <T : Any> ResultSet.collect(body: ResultSet.() -> T?): List<T> {
        val result = arrayListOf<T>()
        while (next()) {
            body()?.let(result::add)
        }
        return result
    }
}

/** Snapshot of all [SchemaReader] findings. */
internal data class SchemaSnapshot(
    /** The user tables of the searched schemas sorted by schema and table name (ignoring case). */
    val tables: List<TableMetadata>,

    /**
     * Raw names of the database sequences that none of [tables] use as a column default sorted by name.
     */
    val unreferencedSequences: List<String>,

    /** Exactly how to interpret strings and codes reported by the database. */
    val syntax: DialectSyntax,
)
