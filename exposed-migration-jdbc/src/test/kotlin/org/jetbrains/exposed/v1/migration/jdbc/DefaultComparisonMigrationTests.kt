package org.jetbrains.exposed.v1.migration.jdbc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.columnTransformer
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression tests for false-positive `SET DEFAULT` statements reported by
 * [EXPOSED-1140](https://youtrack.jetbrains.com/issue/EXPOSED-1140) and
 * [EXPOSED-1141](https://youtrack.jetbrains.com/issue/EXPOSED-1141): a column whose database default already
 * matches the defined one must not require an ALTER statement, even when PostgreSQL renders the default in
 * its metadata differently than Exposed stored it (type casts, jsonb normalization).
 */
class DefaultComparisonMigrationTests : DatabaseTestsBase() {
    // EXPOSED-1141
    @Test
    fun transformedVarcharColumnWithMatchingDefaultRequiresNoSetDefault() {
        val tester = object : Table("tester_1141") {
            val code = varchar("code", 64)
                .transform(columnTransformer<String, String>(unwrap = { it.lowercase() }, wrap = { it.uppercase() }))
                .default("ADMIN")
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.POSTGRESQL, tester) {
            SchemaUtils.create(tester)
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tester, withLogs = false)

            assertTrue(statements.none { it.contains("SET DEFAULT", ignoreCase = true) })
        }
    }

    // EXPOSED-1140
    @Test
    fun jsonbColumnWithScalarValuesInDefaultRequiresNoSetDefault() {
        val tester = object : Table("tester_1140") {
            val data = jsonb<Holder1140>("data", Json.Default)
                .default(Holder1140(flag = true, count = 1000, team = null, name = "A"))
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.POSTGRESQL, tester) {
            SchemaUtils.create(tester)
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tester, withLogs = false)

            assertTrue(statements.none { it.contains("SET DEFAULT", ignoreCase = true) })
        }
    }
}

@Serializable
data class Holder1140(val flag: Boolean, val count: Int, val team: String?, val name: String)
