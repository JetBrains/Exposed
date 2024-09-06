package org.jetbrains.exposed.sql.r2dbc

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.r2dbc.asInt
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.*
import org.junit.Test
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import kotlin.test.assertContentEquals

class MetadataTests : DatabaseTestsBase() {
    @Test
    fun testIdentifierMetadata() {
        withJdbcMetadata { metadata, provider ->
            assertEquals(metadata.identifierQuoteString, provider.identifierQuoteString())
            assertEquals(metadata.storesUpperCaseIdentifiers(), provider.storesUpperCaseIdentifiers())
            assertEquals(metadata.storesUpperCaseQuotedIdentifiers(), provider.storesUpperCaseQuotedIdentifiers())
            assertEquals(metadata.storesLowerCaseIdentifiers(), provider.storesLowerCaseIdentifiers())
            assertEquals(metadata.storesLowerCaseQuotedIdentifiers(), provider.storesLowerCaseQuotedIdentifiers())
            assertEquals(metadata.supportsMixedCaseIdentifiers(), provider.supportsMixedCaseIdentifiers())
            assertEquals(metadata.supportsMixedCaseQuotedIdentifiers(), provider.supportsMixedCaseQuotedIdentifiers())
            assertEquals(metadata.extraNameCharacters, provider.extraNameCharacters())
            assertEquals(metadata.maxColumnNameLength, provider.maxColumnNameLength())
        }
    }

    @Test
    fun testKeywords() {
        withJdbcMetadata { metadata, provider ->
            val expected = metadata.sqlKeywords.split(",",).map { it.trim() }.sorted()
            val actual = provider.sqlKeywords().split(',').sorted()
            assertContentEquals(expected, actual)
        }
    }

    @Test
    fun testAdditionalMetadata() {
        withJdbcMetadata { metadata, provider ->
            assertEquals(metadata.supportsAlterTableWithAddColumn(), provider.supportsAlterTableWithAddColumn())
            assertEquals(metadata.supportsMultipleResultSets(), provider.supportsMultipleResultSets())
            assertEquals(metadata.supportsSelectForUpdate(), provider.supportsSelectForUpdate())
            assertEquals(metadata.defaultTransactionIsolation, provider.getDefaultTransactionIsolation().asInt())
        }
    }

    @Test
    fun testGetCatalogs() {
        withJdbcMetadata(TestTableA, TestTableB, exclude = listOf(TestDB.POSTGRESQLNG)) { metadata, provider ->
            val label = "TABLE_CAT"
            val expected = metadata.catalogs.use {
                val result = mutableListOf<String>()
                while (it.next()) {
                    result.add(it.getString(label))
                }
                it.close()
                result
            }
            val actual = executeMetadataQuery(provider.getCatalogs()) {
                val result = mutableListOf<String>()
                while (it.next()) {
                    result.add(it.getString(label))
                }
                result
            }
            assertContentEquals(expected, actual)
        }
    }

    @Test
    fun testGetSchemas() {
        withJdbcMetadata(TestTableA, TestTableB, exclude = TestDB.ALL_MYSQL_MARIADB) { metadata, provider ->
            val label = "TABLE_SCHEM"
            val expected = metadata.schemas.use {
                val result = mutableListOf<String>()
                while (it.next()) {
                    result.add(it.getString(label))
                }
                it.close()
                result
            }
            val actual = executeMetadataQuery(provider.getSchemas()) {
                val result = mutableListOf<String>()
                while (it.next()) {
                    result.add(it.getString(label))
                }
                result
            }
            assertContentEquals(expected, actual)
        }
    }

    @Test
    fun testGetTables() {
        withJdbcMetadata(TestTableA, TestTableB, exclude = listOf(TestDB.SQLSERVER, TestDB.POSTGRESQLNG)) { metadata, provider ->
            val (catalogName, schemaName) = when (currentDialectTest) {
                is MysqlDialect -> metadata.connection.catalog.inProperCase() to "%"
                else -> metadata.connection.catalog.inProperCase() to metadata.connection.schema.inProperCase()
            }
            val labels = listOf("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME")
            val expected = metadata.getTables(catalogName, schemaName, "%", arrayOf("TABLE")).use {
                val result = mutableListOf<Triple<String, String, String>>()
                while (it.next()) {
                    result.add(Triple(it.getString(labels[0]), it.getString(labels[1]), it.getString(labels[2])))
                }
                it.close()
                result
            }
            val actual = executeMetadataQuery(provider.getTables(catalogName, schemaName, "%", "TABLE")) {
                val result = mutableListOf<Triple<String, String, String>>()
                while (it.next()) {
                    result.add(Triple(it.getString(labels[0]), it.getString(labels[1]), it.getString(labels[2])))
                }
                result
            }
            assertContentEquals(expected, actual)
        }
    }

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

    private fun <T> Transaction.executeMetadataQuery(
        query: String,
        transform: (ResultSet) -> T?
    ) = exec(query) { transform(it) }
}

private fun DatabaseTestsBase.withJdbcMetadata(
    vararg table: Table,
    exclude: Collection<TestDB> = emptyList(),
    body: Transaction.(metadata: DatabaseMetaData, provider: MetadataProvider) -> Unit
) {
    withDb(excludeSettings = exclude + TestDB.ALL_H2_V1 + TestDB.SQLITE + TestDB.ORACLE) { testDb ->
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

        body(jdbcMetadata, provider)

        table.takeIf { it.isNotEmpty() }?.let { SchemaUtils.drop(tables = it) }
    }
}
