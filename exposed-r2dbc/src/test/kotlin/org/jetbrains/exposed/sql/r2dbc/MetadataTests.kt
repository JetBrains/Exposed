package org.jetbrains.exposed.sql.r2dbc

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.r2dbc.asInt
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.*
import org.junit.Test
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import kotlin.test.assertContentEquals

// This class includes sanity tests to ensure hard-coded metadata queries match actual metadata results.
// This should be ideally be replaced with actual integration tests when r2dbc testing is set up.
class MetadataTests : DatabaseTestsBase() {
    private object TestTableA : Table("test_table_a") {
        val id = integer("id").autoIncrement()
        val item = varchar("item", 32).uniqueIndex()
        val amount = long("amount").nullable()
        val available = bool("available").default(true)

        override val primaryKey = PrimaryKey(id)
    }

    private object TestTableB : Table("test_table_b") {
        val id = uuid("id")
        val aId = reference("a_id", TestTableA.id)

        override val primaryKey = PrimaryKey(id, name = "my_custom_key_name")
    }

    @Test
    fun testIdentifierMetadata() {
        withJdbcMetadata { _, metadata, provider ->
            assertEquals(metadata.identifierQuoteString, provider.propertyProvider.identifierQuoteString)
            assertEquals(metadata.storesUpperCaseIdentifiers(), provider.propertyProvider.storesUpperCaseIdentifiers)
            assertEquals(metadata.storesUpperCaseQuotedIdentifiers(), provider.propertyProvider.storesUpperCaseQuotedIdentifiers)
            assertEquals(metadata.storesLowerCaseIdentifiers(), provider.propertyProvider.storesLowerCaseIdentifiers)
            assertEquals(metadata.storesLowerCaseQuotedIdentifiers(), provider.propertyProvider.storesLowerCaseQuotedIdentifiers)
            assertEquals(metadata.supportsMixedCaseIdentifiers(), provider.propertyProvider.supportsMixedCaseIdentifiers)
            assertEquals(metadata.supportsMixedCaseQuotedIdentifiers(), provider.propertyProvider.supportsMixedCaseQuotedIdentifiers)
            assertEquals(metadata.extraNameCharacters, provider.propertyProvider.extraNameCharacters)
            assertEquals(metadata.maxColumnNameLength, provider.propertyProvider.maxColumnNameLength)
        }
    }

    @Test
    fun testKeywords() {
        withJdbcMetadata { _, metadata, provider ->
            val expected = metadata.sqlKeywords.split(",",).map { it.trim() }.sorted()
            val actual = provider.propertyProvider.sqlKeywords().split(',').sorted()
            assertContentEquals(expected, actual)
        }
    }

    @Test
    fun testAdditionalMetadata() {
        withJdbcMetadata { _, metadata, provider ->
            assertEquals(metadata.supportsAlterTableWithAddColumn(), provider.propertyProvider.supportsAlterTableWithAddColumn)
            assertEquals(metadata.supportsMultipleResultSets(), provider.propertyProvider.supportsMultipleResultSets)
            assertEquals(metadata.supportsSelectForUpdate(), provider.propertyProvider.supportsSelectForUpdate)
            assertEquals(metadata.defaultTransactionIsolation, provider.propertyProvider.defaultTransactionIsolation.asInt())
        }
    }

    @Test
    fun testGetCatalog() {
        withJdbcMetadata(TestTableA, TestTableB) { _, metadata, provider ->
            val expected = metadata.connection.catalog
            val actual = executeMetadataQuery(provider.getCatalog()) { getString("TABLE_CAT") }?.firstOrNull()
            assertEquals(expected, actual)
        }
    }

    @Test
    fun testGetSchema() {
        withJdbcMetadata(TestTableA, TestTableB) { _, metadata, provider ->
            val expected = metadata.connection.schema
            val actual = executeMetadataQuery(provider.getSchema()) { getString("TABLE_SCHEM") }?.firstOrNull()
            assertEquals(expected, actual)
        }
    }

    @Test
    fun testGetCatalogs() {
        withJdbcMetadata(TestTableA, TestTableB) { _, metadata, provider ->
            val label = "TABLE_CAT"
            val expected = metadata.catalogs.iterate { getString(label) }
            val actual = executeMetadataQuery(provider.getCatalogs()) { getString(label) }
            assertContentEquals(expected, actual)
        }
    }

    @Test
    fun testGetSchemas() {
        withJdbcMetadata(TestTableA, TestTableB) { _, metadata, provider ->
            val label = "TABLE_SCHEM"
            val expected = metadata.schemas.iterate { getString(label) }
            val actual = executeMetadataQuery(provider.getSchemas()) { getString(label) }
            assertContentEquals(expected, actual)
        }
    }

    @Test
    fun testGetTables() {
        withJdbcMetadata(TestTableA, TestTableB) { testDb, metadata, provider ->
            val (catalogName, schemaName) = metadata.getCatalogAndSchema(testDb)
            val expected = metadata
                .getTables(catalogName, schemaName, "%", arrayOf("TABLE"))
                .iterate { asTableMetadata() }
            val actual = executeMetadataQuery(
                provider.getTables(catalogName, schemaName, "%")
            ) { asTableMetadata() }
            assertContentEquals(expected, actual)
        }
    }

    @Test
    fun testGetSequences() {
        // Oracle driver has limited types in getTables() and ignores "SEQUENCE" so returns empty result
        withJdbcMetadata(TestTableA, TestTableB, exclude = TestDB.ALL_ORACLE_LIKE) { _, metadata, provider ->
            val expected = metadata
                .getTables(null, null, null, arrayOf("SEQUENCE"))
                .iterate { getString("TABLE_NAME") }
            val actual = executeMetadataQuery(provider.getSequences()) { getString("SEQUENCE_NAME") }
            assertContentEquals(expected, actual)
        }
    }

    @Test
    fun testGetColumns() {
        // for some reason task for MariaDBv3 fails with identical (but different id) type even though db version is identical in MariaDBv2
        withJdbcMetadata(TestTableA, TestTableB, exclude = TestDB.ALL_MARIADB) { testDb, metadata, provider ->
            val (catalogName, schemaName) = metadata.getCatalogAndSchema(testDb)
            for (table in listOf(TestTableA, TestTableB)) {
                val tableName = table.nameInDatabaseCaseUnquoted()
                val expected = metadata
                    .getColumns(catalogName, schemaName, tableName, "%")
                    .iterate { asColumnMetadata() }
                val actual = executeMetadataQuery(
                    provider.getColumns(catalogName, schemaName, tableName)
                ) { asColumnMetadata() }
                assertContentEquals(expected, actual)
            }
        }
    }

    @Test
    fun testGetPrimaryKeys() {
        withJdbcMetadata(TestTableA, TestTableB) { testDb, metadata, provider ->
            val (catalogName, schemaName) = metadata.getCatalogAndSchema(testDb)
            val labels = listOf("COLUMN_NAME", "PK_NAME")
            for (table in listOf(TestTableA, TestTableB)) {
                val tableName = table.nameInDatabaseCaseUnquoted()
                val expected = metadata
                    .getPrimaryKeys(catalogName, schemaName, tableName)
                    .iterate { getString(labels[0]) to getString(labels[1]) }
                val actual = executeMetadataQuery(
                    provider.getPrimaryKeys(catalogName, schemaName, tableName)
                ) { getString(labels[0]) to getString(labels[1]) }
                assertContentEquals(expected, actual)
            }
        }
    }

    @Test
    fun testGetIndexInfo() {
        withJdbcMetadata(TestTableA, TestTableB) { testDb, metadata, provider ->
            val (catalogName, schemaName) = metadata.getCatalogAndSchema(testDb)
            for (table in listOf(TestTableA, TestTableB)) {
                val tableName = if (currentDialectTest is OracleDialect) table.nameInDatabaseCase() else table.nameInDatabaseCaseUnquoted()
                val expected = metadata
                    .getIndexInfo(catalogName, schemaName, tableName, false, false)
                    .iterate { asIndexMetadata() }
                    .filterNot { it.indexName == null }
                val actual = executeMetadataQuery(
                    provider.getIndexInfo(catalogName, schemaName, tableName)
                ) { asIndexMetadata() }
                assertContentEquals(expected, actual)
            }
        }
    }

    @Test
    fun testGetImportedKeys() {
        withJdbcMetadata(TestTableA, TestTableB) { testDb, metadata, provider ->
            val (catalogName, schemaName) = metadata.getCatalogAndSchema(testDb)
            for (table in listOf(TestTableA, TestTableB)) {
                val tableName = table.nameInDatabaseCaseUnquoted()
                val expected = metadata
                    .getImportedKeys(catalogName, schemaName, tableName)
                    .iterate { asForeignKeyMetadata() }
                val actual = executeMetadataQuery(
                    provider.getImportedKeys(catalogName, schemaName, tableName)
                ) { asForeignKeyMetadata() }
                assertContentEquals(expected, actual)
            }
        }
    }

    private fun DatabaseMetaData.getCatalogAndSchema(testDb: TestDB): Pair<String, String> = when (testDb) {
        in TestDB.ALL_MYSQL_MARIADB -> {
            val schema = connection.catalog.orEmpty()
            schema to schema
        }
        TestDB.ORACLE -> connection.metaData.userName to connection.schema.orEmpty()
        else -> connection.catalog.orEmpty() to connection.schema.orEmpty()
    }

    private fun <T> ResultSet.iterate(body: ResultSet.() -> T): List<T> {
        val result = mutableListOf<T>()
        while (next()) {
            result.add(body())
        }
        close()
        return result
    }

    private fun <T> Transaction.executeMetadataQuery(
        query: String,
        transform: ResultSet.() -> T
    ): MutableList<T>? = exec(query) {
        val result = mutableListOf<T>()
        while (it.next()) {
            result.add(transform(it))
        }
        result
    }

    private data class TableMetadata(
        val catalog: String?,
        val schema: String?,
        val name: String,
    )

    private fun ResultSet.asTableMetadata(): TableMetadata {
        val catalogName = getString("TABLE_CAT")
        val schemaName = getString("TABLE_SCHEM")
        val tableName = getString("TABLE_NAME")

        return TableMetadata(catalogName, schemaName, tableName)
    }

    private fun ResultSet.asColumnMetadata(): ColumnMetadata {
        val defaultDbValue = getString("COLUMN_DEF")
        val autoIncrement = getString("IS_AUTOINCREMENT") == "YES"
        val type = getInt("DATA_TYPE")
        val name = getString("COLUMN_NAME")
        val nullable = getBoolean("NULLABLE")
        val size = getInt("COLUMN_SIZE")
        val scale = getInt("DECIMAL_DIGITS")

        return ColumnMetadata(name, type, nullable, size, scale, autoIncrement, defaultDbValue)
    }

    private data class IndexMetadata(
        val isNotUnique: Boolean,
        val indexName: String?,
        val columnName: String?,
        val filterCondition: String?,
    )

    private fun ResultSet.asIndexMetadata(): IndexMetadata {
        val isNotUnique = getBoolean("NON_UNIQUE")
        val indexName = getString("INDEX_NAME")
        val columnName = getString("COLUMN_NAME")
        val filterCondition = getString("FILTER_CONDITION")

        return IndexMetadata(isNotUnique, indexName, columnName, filterCondition)
    }

    private data class ForeignKeyMetadata(
        val fromTableName: String,
        val fromColumnName: String,
        val foreignKeyName: String,
        val targetTableName: String,
        val targetColumnName: String,
        val updateRule: Int,
        val deleteRule: Int,
    )

    private fun ResultSet.asForeignKeyMetadata(): ForeignKeyMetadata {
        val fromTableName = getString("FKTABLE_NAME")
        val fromColumnName = getString("FKCOLUMN_NAME")
        val foreignKeyName = getString("FK_NAME")
        val targetTableName = getString("PKTABLE_NAME")
        val targetColumnName = getString("PKCOLUMN_NAME")
        val updateRule = getInt("UPDATE_RULE")
        val deleteRule = getInt("DELETE_RULE")

        return ForeignKeyMetadata(fromTableName, fromColumnName, foreignKeyName, targetTableName, targetColumnName, updateRule, deleteRule)
    }
}

private fun DatabaseTestsBase.withJdbcMetadata(
    vararg table: Table,
    exclude: Collection<TestDB> = emptyList(),
    body: Transaction.(testDb: TestDB, metadata: DatabaseMetaData, provider: MetadataProvider) -> Unit
) {
    withDb(excludeSettings = exclude + TestDB.ALL_H2_V1 + TestDB.SQLITE) { testDb ->
        try {
            val jdbcMetadata = (connection.connection as java.sql.Connection).metaData
            val provider = when (testDb) {
                TestDB.SQLSERVER -> SQLServerMetadata()
                TestDB.ORACLE -> OracleMetadata()
                TestDB.POSTGRESQLNG -> PostgreSQLNGMetadata()
                TestDB.POSTGRESQL -> PostgreSQLMetadata()
                in TestDB.ALL_MARIADB -> MariaDBMetadata()
                in TestDB.ALL_MYSQL -> MySQLMetadata()
                else -> H2Metadata()
            }

            table.takeIf { it.isNotEmpty() }?.let { SchemaUtils.create(tables = it) }

            body(testDb, jdbcMetadata, provider)
        } finally {
            table.takeIf { it.isNotEmpty() }?.let { SchemaUtils.drop(tables = it) }
        }
    }
}
