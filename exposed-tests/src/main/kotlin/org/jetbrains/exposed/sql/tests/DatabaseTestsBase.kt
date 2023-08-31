package org.jetbrains.exposed.sql.tests

import org.jetbrains.exposed.sql.Key
import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.transactions.inTopLevelTransaction
import org.jetbrains.exposed.sql.transactions.nullableTransactionScope
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.junit.Assume
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.math.BigDecimal
import java.sql.SQLException
import java.util.*
import kotlin.concurrent.thread

val TEST_DIALECTS: HashSet<String> = System.getProperty(
    "exposed.test.dialects", ""
).split(",").mapTo(HashSet()) { it.trim().uppercase() }

private val registeredOnShutdown = HashSet<TestDB>()

internal var currentTestDB by nullableTransactionScope<TestDB>()

@RunWith(Parameterized::class)
abstract class DatabaseTestsBase {
    init {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private object CurrentTestDBInterceptor : StatementInterceptor {
        override fun keepUserDataInTransactionStoreOnCommit(userData: Map<Key<*>, Any?>): Map<Key<*>, Any?> {
            return userData.filterValues { it is TestDB }
        }
    }

    companion object {
        @Parameters(name = "name: {2}, container: {0}, dialect: {1}")
        @JvmStatic
        fun data(): Collection<Array<Any>> {
            val name = System.getProperty("exposed.test.name")
            val container = System.getProperty("exposed.test.container")
            return TestDB.enabledDialects().map { arrayOf(container, it, name) }
        }
    }


    @Parameterized.Parameter(0)
    lateinit var container: String

    @Parameterized.Parameter(1)
    lateinit var dialect: Any

    @Parameterized.Parameter(2)
    lateinit var testName: String

    fun withDb(dbSettings: TestDB, statement: Transaction.(TestDB) -> Unit) {
        Assume.assumeTrue(dbSettings in TestDB.enabledDialects())

        if (dbSettings !in registeredOnShutdown) {
            dbSettings.beforeConnection()
            Runtime.getRuntime().addShutdownHook(thread(false) {
                dbSettings.afterTestFinished()
                registeredOnShutdown.remove(dbSettings)
            })
            registeredOnShutdown += dbSettings
            dbSettings.db = dbSettings.connect()
        }

        val database = dbSettings.db!!
        try {
            transaction(database.transactionManager.defaultIsolationLevel, db = database) {
                repetitionAttempts = 1
                registerInterceptor(CurrentTestDBInterceptor)
                currentTestDB = dbSettings
                statement(dbSettings)
            }
        } catch (cause: SQLException) {
            throw cause
        } catch (cause: Throwable) {
            throw IllegalStateException("Failed on ${dbSettings.name}", cause)
        }
    }

    fun withDb(db: List<TestDB>? = null, excludeSettings: List<TestDB> = emptyList(), statement: Transaction.(TestDB) -> Unit) {
        val enabledInTests = TestDB.enabledDialects()
        val toTest = db?.intersect(enabledInTests) ?: (enabledInTests - excludeSettings)
        Assume.assumeTrue(toTest.isNotEmpty())
        toTest.forEach { dbSettings ->
            @Suppress("TooGenericExceptionCaught") try {
                withDb(dbSettings, statement)
            } catch (cause: Throwable) {
                throw AssertionError("Failed on ${dbSettings.name}", cause)
            }
        }
    }

    fun withTables(excludeSettings: List<TestDB>, vararg tables: Table, statement: Transaction.(TestDB) -> Unit) {
        val testDB: TestDB? = dialect as? TestDB

        if (testDB == null) {
            Assume.assumeFalse(false)
            return
        }

        Assume.assumeFalse(testDB in excludeSettings)

        withDb(testDB) {
            SchemaUtils.create(*tables)
            try {
                statement(testDB)
                commit() // Need commit to persist data before drop tables
            } finally {
                try {
                    SchemaUtils.drop(*tables)
                    commit()
                } catch (_: Exception) {
                    val database = testDB.db!!
                    inTopLevelTransaction(database.transactionManager.defaultIsolationLevel, db = database) {
                        repetitionAttempts = 1
                        SchemaUtils.drop(*tables)
                    }
                }
            }
        }
    }

    fun withSchemas(excludeSettings: List<TestDB>, vararg schemas: Schema, statement: Transaction.() -> Unit) {
        val toTest = TestDB.enabledDialects() - excludeSettings
        Assume.assumeTrue(toTest.isNotEmpty())
        toTest.forEach { testDB ->
            withDb(testDB) {
                if (currentDialectTest.supportsCreateSchema) {
                    SchemaUtils.createSchema(*schemas)
                    try {
                        statement()
                        commit() // Need commit to persist data before drop schemas
                    } finally {
                        val cascade = it != TestDB.SQLSERVER
                        SchemaUtils.dropSchema(*schemas, cascade = cascade)
                        commit()
                    }
                }
            }
        }
    }

    fun withTables(vararg tables: Table, statement: Transaction.(TestDB) -> Unit) {
        withTables(excludeSettings = emptyList(), tables = tables, statement = statement)
    }

    fun withSchemas(vararg schemas: Schema, statement: Transaction.() -> Unit) {
        withSchemas(excludeSettings = emptyList(), schemas = schemas, statement = statement)
    }

    fun addIfNotExistsIfSupported() = if (currentDialectTest.supportsIfNotExists) {
        "IF NOT EXISTS "
    } else {
        ""
    }

    fun Transaction.excludingH2Version1(dbSettings: TestDB, statement: Transaction.(TestDB) -> Unit) {
        if (dbSettings !in TestDB.allH2TestDB || (db.dialect as H2Dialect).isSecondVersion) {
            statement(dbSettings)
        }
    }

    fun Transaction.isOldMySql(version: String = "8.0") = currentDialectTest is MysqlDialect && !db.isVersionCovers(BigDecimal(version))

    protected fun prepareSchemaForTest(schemaName: String): Schema = Schema(
        schemaName, defaultTablespace = "USERS", temporaryTablespace = "TEMP ", quota = "20M", on = "USERS"
    )
}
