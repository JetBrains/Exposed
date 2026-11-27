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
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.ColumnOverride
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.DialectSchemaSupport
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.DialectSyntax
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.ReportedForeignKeyColumn
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
    private val identifierManager: IdentifierManagerApi = TransactionManager.current().db.identifierManager
    private val dialectSupport: DialectSchemaSupport = DialectSchemaSupport.forDialect(currentDialect, identifierManager)
    private val currentSchema: String? = when {
        !dialectSupport.supportsSchemas -> NO_SCHEMA
        dialectSupport.schemasAreCatalogs -> catalog
        else -> connection.schema
    }
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
            val tableNames = metadata.getTables(catalogOf(schema), schema.orAnySchema(), "%", arrayOf("TABLE")).use { rs ->
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
        if (!dialectSupport.supportsSchemas) {
            check(schemaPatterns.isEmpty()) {
                "Generation aborted (no files written) because ${currentDialect.name} has no schemas. The configured schemas " +
                    "(${schemaPatterns.joinToString { "'$it'" }}) cannot be searched. Remove the schemas configuration to generate " +
                    "the tables of the connected database"
            }
            return listOf(NO_SCHEMA)
        }

        if (schemaPatterns.isEmpty()) {
            check(!dialectSupport.schemasAreCatalogs || currentSchema != null) {
                "Generation aborted (no files written) because the connection has no current database. Name a database in the " +
                    "database URL, or configure the schemas to search"
            }
            // a driver that reports no current schema gets the JDBC "any schema" pattern instead of an empty name
            return listOf(currentSchema ?: "%")
        }

        val resolved = linkedSetOf<String>()
        schemaPatterns.forEach { pattern ->
            val literal = pattern.trim().removeSurrounding(identifierManager.quoteString)
            val candidates = listOf(literal, identifierManager.inProperCase(literal)).distinct()
            val matches = candidates.flatMap { candidate ->
                if (dialectSupport.schemasAreCatalogs) {
                    catalogsMatching(candidate)
                } else {
                    metadata.getSchemas(catalog, candidate.globToSearchPattern()).use { rs ->
                        rs.collect { getString("TABLE_SCHEM") }
                    }
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
        val columns = readColumns(schema, tableName).map { (column, reportedDefault, unsupportedNote) ->
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
                unsupportedNote = unsupportedNote,
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
        val rows = dialectSupport.foreignKeys(connection, schema, tableName) ?: readImportedKeys(schema, tableName)
        val defaultRule = currentDialect.defaultReferenceOption
        return rows
            .groupBy { it.groupKey }
            .map { (_, keyRows) ->
                val name = keyRows.first().name
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

    private fun readImportedKeys(schema: String, tableName: String): List<ReportedForeignKeyColumn> =
        metadata.getImportedKeys(catalogOf(schema), schema.orAnySchema(), tableName).use { rs ->
            rs.collect {
                val name = getString("FK_NAME").orEmpty()
                ReportedForeignKeyColumn(
                    groupKey = name,
                    name = name,
                    position = getShort("KEY_SEQ").toInt(),
                    columnName = getString("FKCOLUMN_NAME"),
                    targetSchema = getString(if (dialectSupport.schemasAreCatalogs) "PKTABLE_CAT" else "PKTABLE_SCHEM").orEmpty(),
                    targetTable = getString("PKTABLE_NAME"),
                    targetColumnName = getString("PKCOLUMN_NAME"),
                    deleteRule = getShort("DELETE_RULE").toInt(),
                    updateRule = getShort("UPDATE_RULE").toInt(),
                )
            }
        }

    /** A column with its raw name, plus the default, exactly as the driver reports it in `COLUMN_DEF`. */
    private data class ReadColumn(
        val metadata: ColumnMetadata,
        val reportedDefault: String?,
        val unsupportedNote: String?,
    )

    private fun readColumns(schema: String, tableName: String): List<ReadColumn> {
        val declaredColumnTypes = dialectSupport.declaredColumnTypes(connection, schema, tableName)
        val overrides = dialectSupport.columnOverrides(connection, schema, tableName)
        val defaults = dialectSupport.columnDefaults(connection, schema, tableName)
        return metadata.getColumns(catalogOf(schema), schema.orAnySchema(), tableName, "%").use { rs ->
            val row = JdbcResult(rs)
            rs.collect {
                val column = with(ExposedMetadataUtils) { row.asColumnMetadata(declaredColumnTypes) }
                    .withDeclaredType(declaredColumnTypes)
                val override = overrides[column.name]
                val reportedDefault = if (defaults != null) defaults[column.name] else getString("COLUMN_DEF")
                ReadColumn(column.withOverride(override), reportedDefault, override?.unsupportedNote)
            }
        }
    }

    private fun ColumnMetadata.withOverride(override: ColumnOverride?): ColumnMetadata {
        if (override == null) return this

        val autoIncrement = override.autoIncrement ?: this.autoIncrement
        val nullable = override.nullable ?: this.nullable
        if (autoIncrement != this.autoIncrement) override.autoIncrementNote?.let(logger::lifecycle)
        if (nullable != this.nullable) override.nullableWarning?.let { logger.warn(it) }
        override.unsupportedNote?.let { logger.warn(it) }
        return copy(autoIncrement = autoIncrement, nullable = nullable)
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
        val keyColumns = metadata.getPrimaryKeys(catalogOf(schema), schema.orAnySchema(), tableName).use { rs ->
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
        val rows = metadata.getIndexInfo(catalogOf(schema), schema.orAnySchema(), tableName, false, false).use { rs ->
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
                    isInternal = dialectSupport.syntax.isInternalIndexName(indexName),
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
        val defaultName = "${tableName}_${columnNames.joinToString("_")}"
        if (!unique) return isExposedDefaultName(defaultName)
        return isExposedDefaultName("${defaultName}_unique") ||
            (dialectSupport.syntax.omitsUniqueSuffixInIndexNames && isExposedDefaultName(defaultName))
    }

    /**
     * The schema argument for [DatabaseMetaData], which is `null` (no schema filter) for a database without schemas
     * and for one whose schemas are catalogs.
     */
    private fun String.orAnySchema(): String? = if (dialectSupport.schemasAreCatalogs) null else ifEmpty { null }

    /** The catalog argument for [DatabaseMetaData], which is [schema] itself for a database whose schemas are catalogs. */
    private fun catalogOf(schema: String): String? = if (dialectSupport.schemasAreCatalogs) schema else catalog

    /** The existing catalogs whose name matches the glob [pattern], for a database whose schemas are catalogs. */
    private fun catalogsMatching(pattern: String): List<String> {
        val regex = Regex(
            pattern.split('*').joinToString(".*") { part ->
                part.split('?').joinToString(".") { Regex.escape(it) }
            }
        )
        return metadata.catalogs.use { rs -> rs.collect { getString("TABLE_CAT") } }.filter(regex::matches)
    }

    /** Index metadata directly from a ResultSet. */
    private data class IndexRow(val name: String, val unique: Boolean, val position: Int, val columnName: String)

    private fun <T : Any> ResultSet.collect(body: ResultSet.() -> T?): List<T> {
        val result = arrayListOf<T>()
        while (next()) {
            body()?.let(result::add)
        }
        return result
    }

    private companion object {
        /** The empty name of the single namespace of a database without schemas. */
        const val NO_SCHEMA: String = ""
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
