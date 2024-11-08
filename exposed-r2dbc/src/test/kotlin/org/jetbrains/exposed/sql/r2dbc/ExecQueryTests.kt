package org.jetbrains.exposed.sql.r2dbc

// including a dependency on r2dbc drivers in this module means jdbc tests in MetadataTests will attempt to
// use those drivers. Is it not possible to have dependencies on both simultaneously?

// import kotlinx.coroutines.runBlocking
// import org.jetbrains.exposed.sql.DatabaseConfig
// import org.jetbrains.exposed.sql.IntegerColumnType
// import org.jetbrains.exposed.sql.TextColumnType
// import org.jetbrains.exposed.sql.Transaction
// import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
// import org.jetbrains.exposed.sql.tests.TestDB
// import org.jetbrains.exposed.sql.tests.shared.assertEquals
// import org.jetbrains.exposed.sql.transactions.execQuery
// import org.junit.Test
// import kotlin.test.assertNotNull
//
// class ExecQueryTests : DatabaseTestsBase() {
//    // Oracle & SQL Server requires a jdk bump in this module & in tests module
//    private val r2dbcSupportedDb = TestDB.ALL_H2_V2 + TestDB.POSTGRESQL + TestDB.ALL_MYSQL_MARIADB
//
//    @Test
//    fun testConnectionExecutesPlainSqlQuery() {
//        withR2dbcDb(r2dbcSupportedDb) {
//            runBlocking {
//                execQuery("SELECT 13, 'Hello World'") {
//                    (it.get(0) as Number).toInt() to it.get(1)
//                }?.collect {
//                    assertNotNull(it)
//                    assertEquals(13, it.first)
//                    assertEquals("Hello World", it.second)
//                }
//            }
//        }
//    }
//
//    @Test
//    fun testConnectionExecutesParameterizedStatement() {
//        withR2dbcDb(r2dbcSupportedDb) {
//            runBlocking {
//                execQuery("DROP TABLE IF EXISTS TESTER")
//                execQuery("CREATE TABLE IF NOT EXISTS TESTER (AMOUNT INT NOT NULL, WORDS TEXT NOT NULL)")
//
//                execQuery(
//                    "INSERT INTO TESTER (AMOUNT, WORDS) VALUES (?, ?)",
//                    args = listOf(IntegerColumnType() to 13, TextColumnType() to "Hello World")
//                ) {
//                    (it.get(0) as Number).toInt() to it.get(1)
//                }?.collect {
//                    assertNotNull(it)
//                    assertEquals(13, it)
//                    assertEquals("Hello World", it.second)
//                }
//            }
//        }
//    }
// }
//
// private fun DatabaseTestsBase.withR2dbcDb(
//    db: Collection<TestDB>? = null,
//    excludeSettings: Collection<TestDB> = emptyList(),
//    configure: (DatabaseConfig.Builder.() -> Unit)? = null,
//    statement: Transaction.(TestDB) -> Unit
// ) = withDb(db, excludeSettings, configure, true, statement)
