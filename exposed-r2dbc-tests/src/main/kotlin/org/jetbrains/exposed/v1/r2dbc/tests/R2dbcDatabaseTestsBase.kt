package org.jetbrains.exposed.v1.r2dbc.tests

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Key
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.transactions.nullableTransactionScope
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import org.junit.Assume
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.util.*
import kotlin.concurrent.thread

val TEST_DIALECTS: HashSet<String> = System.getProperty(
    "exposed.test.dialects",
    ""
).split(",").mapTo(HashSet()) { it.trim().uppercase() }

private val registeredOnShutdown = HashSet<TestDB>()

internal var currentTestDB by nullableTransactionScope<TestDB>()

@RunWith(Parameterized::class)
abstract class R2dbcDatabaseTestsBase {
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
    lateinit var dialect: TestDB

    @Parameterized.Parameter(2)
    lateinit var testName: String

    fun withDb(
        dbSettings: TestDB,
        configure: (DatabaseConfig.Builder.() -> Unit)? = null,
        statement: suspend R2dbcTransaction.(TestDB) -> Unit
    ) = runTest {
        Assume.assumeTrue(dialect == dbSettings)

        val unregistered = dbSettings !in registeredOnShutdown
        val newConfiguration = configure != null && !unregistered

        if (unregistered) {
            dbSettings.beforeConnection()
            Runtime.getRuntime().addShutdownHook(
                thread(false) {
                    dbSettings.afterTestFinished()
                    registeredOnShutdown.remove(dbSettings)
                }
            )
            registeredOnShutdown += dbSettings
            dbSettings.db = dbSettings.connect(configure ?: {})
        }

        val registeredDb = dbSettings.db!!
        if (newConfiguration) {
            dbSettings.db = dbSettings.connect(configure ?: {})
        }
        val database = dbSettings.db!!
        suspendTransaction(transactionIsolation = database.transactionManager.defaultIsolationLevel, db = database) {
            maxAttempts = 1
            registerInterceptor(CurrentTestDBInterceptor)
            currentTestDB = dbSettings
            statement(dbSettings)
        }

        // revert any new configuration to not be carried over to the next test in suite
        if (configure != null) {
            dbSettings.db = registeredDb
        }
    }

    fun withDb(
        db: Collection<TestDB>? = null,
        excludeSettings: Collection<TestDB> = emptyList(),
        configure: (DatabaseConfig.Builder.() -> Unit)? = null,
        statement: suspend R2dbcTransaction.(TestDB) -> Unit
    ) {
        if (db != null && dialect !in db) {
            Assume.assumeFalse(true)
            return
        }

        if (dialect in excludeSettings) {
            Assume.assumeFalse(true)
            return
        }

        if (dialect !in TestDB.enabledDialects()) {
            Assume.assumeFalse(true)
            return
        }

        withDb(dialect, configure, statement)
    }

    fun withTables(
        excludeSettings: Collection<TestDB>,
        vararg tables: Table,
        configure: (DatabaseConfig.Builder.() -> Unit)? = null,
        statement: suspend R2dbcTransaction.(TestDB) -> Unit
    ) {
        Assume.assumeFalse(dialect in excludeSettings)

        withDb(dialect, configure = configure) {
            try {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.drop(*tables)
            } catch (_: Throwable) {
            }

            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(*tables)
            try {
                statement(dialect)
                commit() // Need commit to persist data before drop tables
            } finally {
                try {
                    org.jetbrains.exposed.v1.r2dbc.SchemaUtils.drop(*tables)
                    commit()
                } catch (_: Exception) {
                    val database = dialect.db!!
                    suspendTransaction(transactionIsolation = database.transactionManager.defaultIsolationLevel, db = database) {
                        maxAttempts = 1
                        org.jetbrains.exposed.v1.r2dbc.SchemaUtils.drop(*tables)
                    }
                }
            }
        }
    }

    fun withSchemas(
        excludeSettings: List<TestDB>,
        vararg schemas: Schema,
        configure: (DatabaseConfig.Builder.() -> Unit)? = null,
        statement: suspend R2dbcTransaction.() -> Unit
    ) {
        if (dialect !in TestDB.enabledDialects()) {
            Assume.assumeFalse(true)
            return
        }

        if (dialect in excludeSettings) {
            Assume.assumeFalse(true)
            return
        }

        withDb(dialect, configure) {
            if (currentDialectTest.supportsCreateSchema) {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.createSchema(*schemas)
                try {
                    statement()
                    commit() // Need commit to persist data before drop schemas
                } finally {
                    val cascade = it != TestDB.SQLSERVER
                    org.jetbrains.exposed.v1.r2dbc.SchemaUtils.dropSchema(*schemas, cascade = cascade)
                    commit()
                }
            }
        }
    }

    fun withTables(
        vararg tables: Table,
        configure: (DatabaseConfig.Builder.() -> Unit)? = null,
        statement: suspend R2dbcTransaction.(TestDB) -> Unit
    ) {
        withTables(excludeSettings = emptyList(), tables = tables, configure = configure, statement = statement)
    }

    fun withSchemas(
        vararg schemas: Schema,
        configure: (DatabaseConfig.Builder.() -> Unit)? = null,
        statement: suspend R2dbcTransaction.() -> Unit
    ) {
        withSchemas(excludeSettings = emptyList(), schemas = schemas, configure = configure, statement = statement)
    }

    fun addIfNotExistsIfSupported() = if (currentDialectTest.supportsIfNotExists) {
        "IF NOT EXISTS "
    } else {
        ""
    }

    protected fun prepareSchemaForTest(schemaName: String): Schema = Schema(
        schemaName,
        defaultTablespace = "USERS",
        temporaryTablespace = "TEMP ",
        quota = "20M",
        on = "USERS"
    )
}
