package org.jetbrains.exposed.r2dbc.sql.tests.postgresql

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.r2dbc.sql.Query
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.RepeatableTestRule
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import org.jetbrains.exposed.sql.vendors.ForUpdateOption.PostgreSQL
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class PostgresqlTests : R2dbcDatabaseTestsBase() {
    @get:Rule
    val repeatRule = RepeatableTestRule()

    private val table = object : IntIdTable() {
        val name = varchar("name", 50)
    }

    @Test
    fun testForUpdateOptionsSyntax() {
        val id = 1

        suspend fun Query.city() = map { it[table.name] }.single()

        suspend fun select(option: ForUpdateOption): String {
            return table.selectAll().where { table.id eq id }.forUpdate(option).city()
        }

        withDb(TestDB.ALL_POSTGRES) {
            withTables(table) {
                val name = "name"
                table.insert {
                    it[table.id] = id
                    it[table.name] = name
                }
                commit()

                val defaultForUpdateRes = table.selectAll().where { table.id eq id }.city()
                val forUpdateRes = select(ForUpdateOption.ForUpdate)
                val forUpdateOfTableRes = select(PostgreSQL.ForUpdate(ofTables = arrayOf(table)))
                val forShareRes = select(PostgreSQL.ForShare)
                val forShareNoWaitOfTableRes = select(
                    PostgreSQL.ForShare(
                        org.jetbrains.exposed.sql.vendors.ForUpdateOption.PostgreSQL.MODE.NO_WAIT,
                        table
                    )
                )
                val forKeyShareRes = select(PostgreSQL.ForKeyShare)
                val forKeyShareSkipLockedRes =
                    select(PostgreSQL.ForKeyShare(org.jetbrains.exposed.sql.vendors.ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED))
                val forNoKeyUpdateRes = select(PostgreSQL.ForNoKeyUpdate)
                val notForUpdateRes = table.selectAll().where { table.id eq id }.notForUpdate().city()

                assertEquals(name, defaultForUpdateRes)
                assertEquals(name, forUpdateRes)
                assertEquals(name, forUpdateOfTableRes)
                assertEquals(name, forShareRes)
                assertEquals(name, forShareNoWaitOfTableRes)
                assertEquals(name, forKeyShareRes)
                assertEquals(name, forKeyShareSkipLockedRes)
                assertEquals(name, forNoKeyUpdateRes)
                assertEquals(name, notForUpdateRes)
            }
        }
    }

//    @Test
//    fun testPrimaryKeyCreatedInPostgresql() {
//        val tableName = "tester"
//        val tester1 = object : Table(tableName) {
//            val age = integer("age")
//        }
//
//        val tester2 = object : Table(tableName) {
//            val age = integer("age")
//
//            override val primaryKey = PrimaryKey(age)
//        }
//
//        val tester3 = object : IntIdTable(tableName) {
//            val age = integer("age")
//        }
//
//        fun <T : Any> JdbcTransaction.assertPrimaryKey(transform: (ResultSet) -> T): T? {
//            return exec(
//                """
//                SELECT ct.relname as TABLE_NAME, ci.relname AS PK_NAME
//                FROM pg_catalog.pg_class ct
//                JOIN pg_index i ON (ct.oid = i.indrelid AND indisprimary)
//                JOIN pg_catalog.pg_class ci ON (ci.oid = i.indexrelid)
//                WHERE ct.relname IN ('$tableName')
//                """.trimIndent()
//            ) { rs ->
//                transform(rs)
//            }
//        }
//        withDb(listOf(TestDB.POSTGRESQLNG, TestDB.POSTGRESQL)) {
//            val defaultPKName = "tester_pkey"
//            SchemaUtils.createMissingTablesAndColumns(tester1)
//            assertPrimaryKey {
//                assertFalse(it.next())
//            }
//
//            SchemaUtils.createMissingTablesAndColumns(tester2)
//            assertPrimaryKey {
//                assertTrue(it.next())
//                assertEquals(defaultPKName, it.getString("PK_NAME"))
//            }
//
//            assertFailAndRollback("Multiple primary keys are not allowed") {
//                SchemaUtils.createMissingTablesAndColumns(tester3)
//            }
//
//            SchemaUtils.drop(tester1)
//        }
//    }
}
