package org.jetbrains.exposed.v1.migration.jdbc

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.core.statements.buildStatement
import org.jetbrains.exposed.v1.core.vendors.RedshiftDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.day
import org.jetbrains.exposed.v1.javatime.hour
import org.jetbrains.exposed.v1.javatime.minute
import org.jetbrains.exposed.v1.javatime.month
import org.jetbrains.exposed.v1.javatime.second
import org.jetbrains.exposed.v1.javatime.time
import org.jetbrains.exposed.v1.javatime.year
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import org.jetbrains.exposed.v1.datetime.datetime as kotlinDateTime
import org.jetbrains.exposed.v1.javatime.datetime as javaDateTime

private const val REDSHIFT_URL_ENV = "EXPOSED_REDSHIFT_JDBC_URL"
private const val REDSHIFT_USER_ENV = "EXPOSED_REDSHIFT_USER"
private const val REDSHIFT_PASSWORD_ENV = "EXPOSED_REDSHIFT_PASSWORD"
private const val REDSHIFT_DATABASE_DDL_ENV = "EXPOSED_REDSHIFT_TEST_DATABASE_DDL"

@OptIn(ExperimentalDatabaseMigrationApi::class, ExperimentalUuidApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Suppress("LargeClass", "LongMethod", "MagicNumber")
class RedshiftLiveIntegrationTests {
    private var database: Database? = null
    private val runId = System.currentTimeMillis().toString(36)

    @BeforeAll
    fun connectAndVerifyTemporaryTableAccess() {
        val url = System.getenv(REDSHIFT_URL_ENV)
        val user = System.getenv(REDSHIFT_USER_ENV)
        val password = System.getenv(REDSHIFT_PASSWORD_ENV)
        Assumptions.assumeTrue(
            !url.isNullOrBlank() && !user.isNullOrBlank() && !password.isNullOrBlank(),
            "Live Redshift credentials are not configured"
        )

        val connected = Database.connect(
            url = url!!,
            driver = "com.amazon.redshift.Driver",
            user = user!!,
            password = password!!
        )
        database = connected
        try {
            transaction(connected) {
                maxAttempts = 1
                assertIs<RedshiftDialect>(currentDialect)
                exec("CREATE TEMP TABLE exposed_connection_probe_$runId (value INTEGER)")
                exec("DROP TABLE exposed_connection_probe_$runId")
            }
        } catch (cause: Throwable) {
            TransactionManager.closeAndUnregister(connected)
            database = null
            throw cause
        }
    }

    @AfterAll
    fun disconnect() {
        database?.let { connected ->
            TransactionManager.closeAndUnregister(connected)
            database = null
        }
    }

    @Test
    fun testIdentityGeneratedKeysAndUpsertGrammar() {
        val records = object : Table(tableName("identity_upsert")) {
            val id = long("id").autoIncrement()
            val code = varchar("code", 32)
            val value = integer("value")

            override val primaryKey = PrimaryKey(id)
        }

        withTables(records) {
            val inserted = records.insert {
                it[code] = "existing"
                it[value] = 1
            }
            assertFailsWith<UnsupportedByDialectException> { inserted[records.id] }
            assertEquals(1L, records.selectAll().count())
            assertEquals(1L, records.select(records.id).single()[records.id])

            records.upsert(records.code) {
                it[code] = "existing"
                it[value] = 2
            }
            records.upsert(records.code) {
                it[code] = "new"
                it[value] = 3
            }

            assertEquals(
                mapOf("existing" to 2, "new" to 3),
                records.select(records.code, records.value).associate { it[records.code] to it[records.value] }
            )
            assertEquals(2L, records.selectAll().count())
        }
    }

    @Test
    fun testMergeAndMergeSelectGrammar() {
        val source = object : Table(tableName("merge_source")) {
            val key = integer("key")
            val value = integer("value")
        }
        val tableTarget = object : Table(tableName("merge_table_target")) {
            val key = integer("key")
            val value = integer("value")
        }
        val selectTarget = object : Table(tableName("merge_select_target")) {
            val key = integer("key")
            val value = integer("value")
        }

        withTables(source, tableTarget, selectTarget) {
            source.batchInsert(listOf(1 to 10, 2 to 20)) { (key, value) ->
                this[source.key] = key
                this[source.value] = value
            }
            tableTarget.insert {
                it[key] = 1
                it[value] = 1
            }
            selectTarget.insert {
                it[key] = 1
                it[value] = 1
            }

            tableTarget.mergeFrom(source, on = { tableTarget.key eq source.key }) {
                whenMatchedUpdate {
                    it[tableTarget.value] = source.value
                }
                whenNotMatchedInsert {
                    it[tableTarget.key] = source.key
                    it[tableTarget.value] = source.value
                }
            }

            val sourceQuery = source.selectAll().alias("source_query")
            selectTarget.mergeFrom(sourceQuery, on = { selectTarget.key eq sourceQuery[source.key] }) {
                whenMatchedUpdate {
                    it[selectTarget.value] = sourceQuery[source.value]
                }
                whenNotMatchedInsert {
                    it[selectTarget.key] = sourceQuery[source.key]
                    it[selectTarget.value] = sourceQuery[source.value]
                }
            }

            val expected = listOf(1 to 10, 2 to 20)
            assertEquals(
                expected,
                tableTarget.selectAll().orderBy(tableTarget.key).map { it[tableTarget.key] to it[tableTarget.value] }
            )
            assertEquals(
                expected,
                selectTarget.selectAll().orderBy(selectTarget.key).map { it[selectTarget.key] to it[selectTarget.value] }
            )
        }
    }

    @Test
    fun testExplainFunctionsAndBitwiseOperations() {
        val functions = object : Table(tableName("functions")) {
            val id = integer("id")
            val name = varchar("name", 32)
            val flags = integer("flags")
            val createdAt = javaDateTime("created_at")
        }

        withTables(functions) {
            functions.batchInsert(
                listOf(
                    listOf(1, "Alpha", 5, LocalDateTime.of(2024, 2, 3, 4, 5, 6)),
                    listOf(2, "Beta", 6, LocalDateTime.of(2024, 2, 3, 4, 5, 7))
                )
            ) { values ->
                this[functions.id] = values[0] as Int
                this[functions.name] = values[1] as String
                this[functions.flags] = values[2] as Int
                this[functions.createdAt] = values[3] as LocalDateTime
            }

            assertTrue(explain { functions.selectAll() }.toList().isNotEmpty())
            assertTrue(explain(options = "VERBOSE") { functions.selectAll() }.toList().isNotEmpty())

            val concatenated = concat("-", listOf(functions.name, stringLiteral("tail")))
            val located = functions.name.locate("ph")
            val bitwiseOr = functions.flags bitwiseOr 3
            val bitwiseXor = functions.flags bitwiseXor 3
            val date = functions.createdAt.date()
            val time = functions.createdAt.time()
            val year = functions.createdAt.year()
            val month = functions.createdAt.month()
            val day = functions.createdAt.day()
            val hour = functions.createdAt.hour()
            val minute = functions.createdAt.minute()
            val second = functions.createdAt.second()
            val row = functions
                .select(concatenated, located, bitwiseOr, bitwiseXor, date, time, year, month, day, hour, minute, second)
                .where { functions.id eq 1 }
                .single()

            assertEquals("Alpha-tail", row[concatenated])
            assertEquals(3, row[located])
            assertEquals(7, row[bitwiseOr])
            assertEquals(6, row[bitwiseXor])
            assertEquals(LocalDate.of(2024, 2, 3), row[date])
            assertEquals(LocalTime.of(4, 5, 6), row[time])
            assertEquals(2024, row[year])
            assertEquals(2, row[month])
            assertEquals(3, row[day])
            assertEquals(4, row[hour])
            assertEquals(5, row[minute])
            assertEquals(6, row[second])

            assertEquals(1L, functions.selectAll().where { functions.name regexp "^Al.*" }.count())
            val grouped = functions.name.groupConcat(
                separator = ",",
                orderBy = functions.name to SortOrder.ASC
            )
            assertEquals("Alpha,Beta", functions.select(grouped).single()[grouped])

            val random = Random()
            val randomValue = functions.select(random).limit(1).single()[random].toDouble()
            assertTrue(randomValue >= 0.0 && randomValue < 1.0)
        }
    }

    @Test
    fun testDriverMetadataColumnsIndicesPrimaryKeyAndChecks() {
        val unqualifiedName = tableName("metadata")
        class MetadataTable(name: String) : Table(name) {
            val id = integer("id")
            val code = varchar("code", 48).uniqueIndex("uq_${runId}_metadata_code")
            val payload = long("payload").index("ix_${runId}_metadata_payload")

            override val primaryKey = PrimaryKey(id, name = "pk_${runId}_metadata")
        }
        val metadataTable = MetadataTable(unqualifiedName)

        withTables(metadataTable) {
            val introspectionTable = MetadataTable("${temporarySchema(unqualifiedName)}.$unqualifiedName")
            val jdbcConnection = assertIs<JdbcConnectionImpl>(connection)
            assertEquals("Redshift JDBC Driver", jdbcConnection.connection.metaData.driverName)
            assertEquals(RedshiftDialect.dialectName, connection.metadata { databaseDialectName })

            currentDialectMetadata.resetSchemaCaches()
            val columns = currentDialectMetadata.tableColumns(introspectionTable)[introspectionTable].orEmpty()
            assertEquals(setOf("id", "code", "payload"), columns.map { it.name.lowercase() }.toSet())
            assertEquals(Types.INTEGER, columns.single { it.name.equals("id", true) }.jdbcType)
            assertEquals(Types.VARCHAR, columns.single { it.name.equals("code", true) }.jdbcType)
            assertEquals(Types.BIGINT, columns.single { it.name.equals("payload", true) }.jdbcType)

            val primaryKey = currentDialectMetadata.existingPrimaryKeys(introspectionTable)[introspectionTable]
            assertNotNull(primaryKey)
            assertEquals(listOf("id"), primaryKey.columnNames.map { it.lowercase() })

            val indices = currentDialectMetadata.existingIndices(introspectionTable)[introspectionTable].orEmpty()
            assertEquals(1, indices.size)
            assertTrue(indices.single().unique)
            assertEquals(listOf("code"), indices.single().columns.map { it.name.lowercase() })

            val checks = currentDialectMetadata.existingCheckConstraints(introspectionTable)[introspectionTable].orEmpty()
            assertTrue(checks.isEmpty())
        }
    }

    @Test
    fun testMixedCaseIdentifierRoundTrip() {
        val unqualifiedName = "MixedCase_$runId"
        class MixedCaseTable(name: String) : Table(name) {
            val mixedColumn = varchar("MixedColumn", 32)
        }
        val mixed = MixedCaseTable(unqualifiedName)

        withTables(mixed) {
            val introspectionTable = MixedCaseTable("${temporarySchema(unqualifiedName.lowercase())}.$unqualifiedName")
            mixed.insert { it[mixedColumn] = "value" }
            assertEquals("value", mixed.select(mixed.mixedColumn).single()[mixed.mixedColumn])

            currentDialectMetadata.resetSchemaCaches()
            val columns = currentDialectMetadata.tableColumns(introspectionTable)[introspectionTable].orEmpty()
            assertEquals(listOf("mixedcolumn"), columns.map { it.name.lowercase() })
        }
    }

    @Test
    fun testMigrationNoOpForUnchangedTable() {
        // This requires persistent-schema privileges because Redshift JDBC omits temporary tables from TABLE metadata.
        // On the shared cluster, schema DDL reached server authorization and was denied by database grants.
        val unqualifiedName = tableName("migration")
        class MigrationTable(name: String) : Table(name) {
            val id = integer("id")
            val code = varchar("code", 32)

            override val primaryKey = PrimaryKey(id)
        }
        val migrationTable = MigrationTable(unqualifiedName)

        withTables(migrationTable) {
            val introspectionTable = MigrationTable("${temporarySchema(unqualifiedName)}.$unqualifiedName")
            currentDialectMetadata.resetSchemaCaches()
            Assumptions.assumeTrue(
                introspectionTable.exists(),
                "Redshift JDBC does not expose temporary tables as TABLE metadata; persistent CREATE permission is required"
            )

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(introspectionTable, withLogs = false)
            assertTrue(statements.isEmpty(), "Unexpected migration statements: $statements")
            assertTrue(statements.none { it.isBlank() })
        }
    }

    @Test
    fun testCrudDataTypesAndUuidRoundTrips() {
        val values = object : Table(tableName("types")) {
            val id = integer("id")
            val textValue = text("text_value")
            val binaryValue = binary("binary_value", 1024)
            val blobValue = blob("blob_value")
            val byteValue = byte("byte_value")
            val floatValue = float("float_value")
            val javaAt = javaDateTime("java_at")
            val kotlinAt = kotlinDateTime("kotlin_at")
            val booleanValue = bool("boolean_value")
            val decimalValue = decimal("decimal_value", 12, 2)
            val longValue = long("long_value")
            val uuidValue = uuid("uuid_value")
            val javaUuidValue = javaUUID("java_uuid_value")

            override val primaryKey = PrimaryKey(id)
        }
        val binary = byteArrayOf(0, 1, 2, 127, -1)
        val blob = byteArrayOf(5, 4, 3, 2, 1)
        val javaDateTime = LocalDateTime.of(2025, 6, 7, 8, 9, 10)
        val kotlinDateTime = KotlinLocalDateTime(2025, 6, 7, 8, 9, 10)
        val uuid = Uuid.parse("00112233-4455-6677-8899-aabbccddeeff")
        val javaUuid = UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100")

        withTables(values) {
            values.insert {
                it[id] = 1
                it[textValue] = "initial text"
                it[binaryValue] = binary
                it[blobValue] = ExposedBlob(blob)
                it[byteValue] = -12
                it[floatValue] = 1.25f
                it[javaAt] = javaDateTime
                it[kotlinAt] = kotlinDateTime
                it[booleanValue] = true
                it[decimalValue] = BigDecimal("12345.67")
                it[longValue] = 9_876_543_210L
                it[uuidValue] = uuid
                it[javaUuidValue] = javaUuid
            }

            val row = values.selectAll().single()
            assertEquals("initial text", row[values.textValue])
            assertContentEquals(binary, row[values.binaryValue])
            assertContentEquals(blob, row[values.blobValue].bytes)
            assertEquals((-12).toByte(), row[values.byteValue])
            assertEquals(1.25f, row[values.floatValue])
            assertEquals(javaDateTime, row[values.javaAt])
            assertEquals(kotlinDateTime, row[values.kotlinAt])
            assertTrue(row[values.booleanValue])
            assertEquals(BigDecimal("12345.67"), row[values.decimalValue])
            assertEquals(9_876_543_210L, row[values.longValue])
            assertEquals(uuid, row[values.uuidValue])
            assertEquals(javaUuid, row[values.javaUuidValue])

            val storedUuids = exec(
                "SELECT uuid_value, java_uuid_value FROM ${values.tableName}"
            ) { result ->
                check(result.next())
                result.getString(1) to result.getString(2)
            }
            assertEquals(uuid.toString(), storedUuids?.first)
            assertEquals(javaUuid.toString(), storedUuids?.second)

            values.update({ values.id eq 1 }) { it[textValue] = "updated text" }
            assertEquals("updated text", values.select(values.textValue).single()[values.textValue])
            values.deleteWhere { values.id eq 1 }
            assertEquals(0L, values.selectAll().count())
        }
    }

    @Test
    fun testInformationalConstraintsRangesAndIndexPolicy() {
        val parentName = tableName("constraint_parent")
        class ParentTable(name: String) : Table(name) {
            val id = integer("id")
            val code = varchar("code", 32).uniqueIndex("uq_${runId}_constraint_code")
            val note = varchar("note", 32).index("ix_${runId}_constraint_note")

            override val primaryKey = PrimaryKey(id, name = "pk_${runId}_constraint_parent")
        }
        val parent = ParentTable(parentName)
        val child = object : Table(tableName("constraint_child")) {
            val id = integer("id")
            val parentId = integer("parent_id").references(parent.id)
        }
        val ranges = object : Table(tableName("ranges")) {
            val signedByte = byte("signed_byte")
            val unsignedByte = ubyte("unsigned_byte")
            val unsignedShort = ushort("unsigned_short")
        }

        withTables(parent, child, ranges) {
            parent.insert {
                it[id] = 1
                it[code] = "duplicate"
                it[note] = "one"
            }
            parent.insert {
                it[id] = 1
                it[code] = "duplicate"
                it[note] = "two"
            }
            child.insert {
                it[id] = 1
                it[parentId] = 999
            }
            assertEquals(2L, parent.selectAll().count())
            assertEquals(1L, child.selectAll().count())

            exec(
                "INSERT INTO ${ranges.tableName} (signed_byte, unsigned_byte, unsigned_short) " +
                    "VALUES (1000, -1, 70000)"
            )
            assertEquals(1L, ranges.selectAll().count())

            currentDialectMetadata.resetSchemaCaches()
            val introspectionTable = ParentTable("${temporarySchema(parentName)}.$parentName")
            val indices = currentDialectMetadata.existingIndices(introspectionTable)[introspectionTable].orEmpty()
            assertEquals(1, indices.size)
            assertEquals(listOf("code"), indices.single().columns.map { it.name.lowercase() })
            assertTrue(indices.single().unique)
            assertTrue(parent.indices.single { !it.unique }.createStatement().isEmpty())
            assertTrue(parent.indices.single { !it.unique }.dropStatement().isEmpty())
        }
    }

    @Test
    fun testCreateDropAndSetSchemaInsideTransaction() {
        val autoCommitTable = object : Table(tableName("autocommit")) {
            val id = integer("id")
        }

        withRedshift {
            SchemaUtils.setSchema(Schema("public"))
            val currentSchema = exec("SELECT current_schema()") { result ->
                check(result.next())
                result.getString(1)
            }
            assertEquals("public", currentSchema)

            createTemporaryTables(autoCommitTable)
            assertEquals(0L, autoCommitTable.selectAll().count())
            SchemaUtils.drop(autoCommitTable)
            assertFailsWith<ExposedSQLException> {
                autoCommitTable.selectAll().count()
            }
        }
    }

    @Test
    fun testCreateListAndDropDatabase() {
        // This is opt-in because it requires cluster-level database privileges. On the shared cluster, CREATE DATABASE
        // reached server authorization and was denied by IAM/database grants.
        Assumptions.assumeTrue(
            System.getenv(REDSHIFT_DATABASE_DDL_ENV).equals("true", ignoreCase = true),
            "Cluster-level database DDL is not enabled"
        )
        val databaseName = "exposed_live_db_$runId"

        withRedshift {
            connection.autoCommit = true
            try {
                if (databaseName in SchemaUtils.listDatabases()) {
                    SchemaUtils.dropDatabase(databaseName)
                }
                SchemaUtils.createDatabase(databaseName)
                assertContains(SchemaUtils.listDatabases(), databaseName)
            } finally {
                if (databaseName in SchemaUtils.listDatabases()) {
                    SchemaUtils.dropDatabase(databaseName)
                }
                connection.autoCommit = false
            }
            assertFalse(databaseName in SchemaUtils.listDatabases())
        }
    }

    @Test
    fun testUnsupportedFeaturesFailBeforeExecution() {
        val unsupported = object : Table(tableName("unsupported")) {
            val id = integer("id")
            val name = varchar("name", 32)
            val array = array<Int>("array_value")
        }

        withRedshift {
            assertFailsWith<UnsupportedByDialectException> {
                unsupported.update(limit = 1) { it[name] = "updated" }
            }
            assertFailsWith<UnsupportedByDialectException> {
                unsupported.deleteWhere(limit = 1) { id eq 1 }
            }
            assertFailsWith<UnsupportedByDialectException> {
                unsupported.insertReturning {
                    it[id] = 1
                    it[name] = "inserted"
                }.toList()
            }
            assertFailsWith<UnsupportedByDialectException> {
                unsupported.name.modifyStatements(ColumnDiff.AllChanged)
            }
            assertFailsWith<UnsupportedByDialectException> {
                unsupported.array.columnType.sqlType()
            }
            assertFailsWith<UnsupportedByDialectException> {
                Sequence("sequence_$runId").createStatement()
            }
            assertFailsWith<UnsupportedByDialectException> {
                currentDialect.dataTypeProvider.jsonType()
            }
            assertFailsWith<UnsupportedByDialectException> {
                currentDialect.dataTypeProvider.jsonBType()
            }
            assertFailsWith<UnsupportedByDialectException> {
                currentDialect.dataTypeProvider.vectorType()
            }
            assertFailsWith<UnsupportedByDialectException> {
                unsupported.name.regexp(stringLiteral("value"), caseSensitive = false).toString()
            }

            val returning = buildStatement {
                unsupported.insertReturning {
                    it[id] = 1
                    it[name] = "inserted"
                }
            }
            assertFailsWith<UnsupportedByDialectException> {
                returning.prepareSQL(this, prepared = true)
            }
        }
    }

    @Test
    fun testTextAndBinaryWriteLimits() {
        val limits = object : Table(tableName("limits")) {
            val id = integer("id")
            val textValue = text("text_value").nullable()
            val binaryValue = binary("binary_value").nullable()
        }

        assertFailsWith<ExposedSQLException> {
            withRedshift {
                createTemporaryTables(limits)
                limits.insert {
                    it[id] = 1
                    it[textValue] = "x".repeat(65_536)
                }
            }
        }
        withRedshift {
            createTemporaryTables(limits)
            val payload = ByteArray(1_048_577) { 1 }
            limits.insert {
                it[id] = 2
                it[binaryValue] = payload
            }
            val storedSize = exec(
                "SELECT OCTET_LENGTH(binary_value) FROM ${limits.tableName} WHERE id = 2"
            ) { result ->
                check(result.next())
                result.getInt(1)
            }
            assertEquals(payload.size, storedSize)

            val updatedPayload = ByteArray(1_048_578) { 2 }
            limits.update({ limits.id eq 2 }) {
                it[binaryValue] = updatedPayload
            }
            val updatedSize = exec(
                "SELECT OCTET_LENGTH(binary_value) FROM ${limits.tableName} WHERE id = 2"
            ) { result ->
                check(result.next())
                result.getInt(1)
            }
            assertEquals(updatedPayload.size, updatedSize)
        }
        assertFailsWith<ExposedSQLException> {
            withRedshift {
                createTemporaryTables(limits)
                limits.insert {
                    it[id] = 3
                    it[binaryValue] = ByteArray(16_777_217) { 1 }
                }
            }
        }
    }

    @Test
    fun testNullOrderingAndSubqueryUnion() {
        val queries = object : Table(tableName("queries")) {
            val id = integer("id")
            val value = varchar("value", 16).nullable()
        }

        withTables(queries) {
            queries.batchInsert(listOf(1 to "b", 2 to null, 3 to "a")) { (id, value) ->
                this[queries.id] = id
                this[queries.value] = value
            }

            assertEquals(
                listOf(null, "a", "b"),
                queries.select(queries.value).orderBy(queries.value to SortOrder.ASC_NULLS_FIRST).map { it[queries.value] }
            )
            assertEquals(
                listOf("b", "a", null),
                queries.select(queries.value).orderBy(queries.value to SortOrder.DESC_NULLS_LAST).map { it[queries.value] }
            )

            val sortedAndLimited = queries
                .selectAll()
                .where { queries.value.isNotNull() }
                .orderBy(queries.value to SortOrder.DESC)
                .limit(1)
            val union = sortedAndLimited.unionAll(sortedAndLimited)
            assertEquals(listOf("b", "b"), union.map { it[queries.value] })
        }
    }

    private fun tableName(suffix: String): String = "exposed_${runId}_$suffix"

    private fun <T> withRedshift(statement: JdbcTransaction.() -> T): T {
        val connected = checkNotNull(database)
        return transaction(connected) {
            maxAttempts = 1
            statement()
        }
    }

    private fun withTables(vararg tables: Table, statement: JdbcTransaction.() -> Unit) {
        withRedshift {
            try {
                createTemporaryTables(*tables)
                currentDialectMetadata.resetSchemaCaches()
                statement()
            } finally {
                SchemaUtils.drop(*tables.reversedArray())
                currentDialectMetadata.resetSchemaCaches()
            }
        }
    }

    private fun JdbcTransaction.createTemporaryTables(vararg tables: Table) {
        SchemaUtils.createStatements(*tables).forEach { statement ->
            val temporaryStatement = if (statement.startsWith("CREATE TABLE", ignoreCase = true)) {
                statement.replaceFirst("CREATE TABLE", "CREATE TEMP TABLE", ignoreCase = true)
            } else {
                statement
            }
            exec(temporaryStatement)
        }
    }

    private fun JdbcTransaction.temporarySchema(tableName: String): String {
        return checkNotNull(
            exec(
                "SELECT n.nspname FROM pg_catalog.pg_class c " +
                    "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace " +
                    "WHERE c.relname = '${tableName.replace("'", "''")}'"
            ) { result ->
                check(result.next())
                result.getString(1)
            }
        )
    }
}
