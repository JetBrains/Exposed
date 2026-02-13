package org.jetbrains.exposed.v1.r2dbc.tests

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Key
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.transactions.nullableTransactionScope
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.Parameter
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import kotlin.concurrent.thread

val TEST_DIALECTS: HashSet<String> = System.getProperty(
    "exposed.test.dialects",
    ""
).split(",").mapTo(HashSet()) { it.trim().uppercase() }

private val registeredOnShutdown = HashSet<TestDB>()

internal var currentTestDB by nullableTransactionScope<TestDB>()

@ParameterizedClass(name = "name: {2}, container: {0}, dialect: {1}", allowZeroInvocations = true)
@MethodSource("data")
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
        @JvmStatic
        fun data(): Collection<Array<Any>> {
            val name = System.getProperty("exposed.test.name")
            val container = System.getProperty("exposed.test.container")
            return TestDB.enabledDialects().map { arrayOf(container, it, name) }
        }
    }

    @Parameter(0)
    lateinit var container: String

    @Parameter(1)
    lateinit var dialect: TestDB

    @Parameter(2)
    lateinit var testName: String

    @OptIn(InternalApi::class)
    fun withConnection(
        dbSettings: TestDB = dialect,
        configure: (DatabaseConfig.Builder.() -> Unit)? = null,
        statement: suspend (R2dbcDatabase, TestDB) -> Unit
    ) = runTest {
        Assumptions.assumeTrue(dialect == dbSettings)

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

        statement(database, dbSettings)

        // revert any new configuration to not be carried over to the next test in suite
        if (configure != null) {
            dbSettings.db = registeredDb
        }
    }

    fun withDb(
        dbSettings: TestDB,
        configure: (DatabaseConfig.Builder.() -> Unit)? = null,
        statement: suspend R2dbcTransaction.(TestDB) -> Unit
    ) = withConnection(dbSettings, configure) { database, testDb ->
        suspendTransaction(database) {
            maxAttempts = 1
            registerInterceptor(CurrentTestDBInterceptor)
            currentTestDB = dbSettings
            statement(dbSettings)
        }
    }

    fun withDb(
        db: Collection<TestDB>? = null,
        excludeSettings: Collection<TestDB> = emptyList(),
        configure: (DatabaseConfig.Builder.() -> Unit)? = null,
        statement: suspend R2dbcTransaction.(TestDB) -> Unit
    ) {
        if (db != null && dialect !in db) {
            Assumptions.assumeFalse(true)
            return
        }

        if (dialect in excludeSettings) {
            Assumptions.assumeFalse(true)
            return
        }

        if (dialect !in TestDB.enabledDialects()) {
            Assumptions.assumeFalse(true)
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
        Assumptions.assumeFalse(dialect in excludeSettings)

        withDb(dialect, configure = configure) {
            try {
                SchemaUtils.drop(*tables)
            } catch (_: Throwable) {
            }

            SchemaUtils.create(*tables)
            try {
                statement(dialect)
                commit() // Need commit to persist data before drop tables
            } finally {
                try {
                    SchemaUtils.drop(*tables)
                    commit()
                } catch (_: Exception) {
                    val database = dialect.db!!
                    inTopLevelSuspendTransaction(database) {
                        maxAttempts = 1
                        SchemaUtils.drop(*tables)
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
            Assumptions.assumeFalse(true)
            return
        }

        if (dialect in excludeSettings) {
            Assumptions.assumeFalse(true)
            return
        }

        withDb(dialect, configure) {
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
