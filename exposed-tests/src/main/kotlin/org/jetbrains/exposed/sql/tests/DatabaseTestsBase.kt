package org.jetbrains.exposed.sql.tests

import org.h2.engine.Mode
import org.jetbrains.exposed.sql.*
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
import java.lang.IllegalStateException
import java.math.BigDecimal
import java.sql.Connection
import java.sql.SQLException
import java.util.*
import kotlin.concurrent.thread
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.declaredMemberProperties

val TEST_DIALECTS: HashSet<String> = System.getProperty(
    "exposed.test.dialects", ""
).split(",").mapTo(HashSet()) { it.trim().uppercase() }

enum class TestDB(
    val connection: () -> String,
    val driver: String,
    val user: String = "root",
    val pass: String = "Exposed_password_1!",
    val beforeConnection: () -> Unit = {},
    val afterTestFinished: () -> Unit = {},
    val dbConfig: DatabaseConfig.Builder.() -> Unit = {}
) {
    H2({ "jdbc:h2:mem:regular;DB_CLOSE_DELAY=-1;" }, "org.h2.Driver", dbConfig = {
        defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
    }),
    H2_MYSQL({ "jdbc:h2:mem:mysql;MODE=MySQL;DB_CLOSE_DELAY=-1" }, "org.h2.Driver", beforeConnection = {
        Mode::class.declaredMemberProperties.firstOrNull { it.name == "convertInsertNullToZero" }?.let { field ->
            val mode = Mode.getInstance("MySQL")
            @Suppress("UNCHECKED_CAST") (field as KMutableProperty1<Mode, Boolean>).set(mode, false)
        }
    }),
    H2_MARIADB(
        { "jdbc:h2:mem:mariadb;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" }, "org.h2.Driver", pass = "root"
    ),
    H2_PSQL(
        { "jdbc:h2:mem:psql;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1" }, "org.h2.Driver"
    ),
    H2_ORACLE(
        { "jdbc:h2:mem:oracle;MODE=Oracle;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1" }, "org.h2.Driver"
    ),
    H2_SQLSERVER({ "jdbc:h2:mem:sqlserver;MODE=MSSQLServer;DB_CLOSE_DELAY=-1" }, "org.h2.Driver"),
    SQLITE({ "jdbc:sqlite:file:test?mode=memory&cache=shared" }, "org.sqlite.JDBC"),
    MYSQL(
        connection = {
            "jdbc:mysql://127.0.0.1:3001/testdb?useSSL=false&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull"
        }, driver = "com.mysql.jdbc.Driver"
    ),
    POSTGRESQL({ "jdbc:postgresql://127.0.0.1:3004/&user=postgres&password=&lc_messages=en_US.UTF-8" },
               "org.postgresql.Driver",
               beforeConnection = { },
               afterTestFinished = { }),
    POSTGRESQLNG(
        { "jdbc:pgsql://127.0.0.1:3004/&user=postgres&password=&lc_messages=en_US.UTF-8" },
        "com.impossibl.postgres.jdbc.PGDriver",
        user = "postgres",
    ),
    ORACLE(driver = "oracle.jdbc.OracleDriver", user = "sys as sysdba", pass = "Oracle18", connection = {
        "jdbc:oracle:thin:@127.0.0.1:3003:XE"
    }, beforeConnection = {
        Locale.setDefault(Locale.ENGLISH)
    }),
    SQLSERVER(
        {
            "jdbc:sqlserver://127.0.0.1:3005"
        },
        "com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "SA",
    ),
    MARIADB(
        {
            "jdbc:mariadb://127.0.0.1:3000/testdb"
        }, "org.mariadb.jdbc.Driver"
    );

    var db: Database? = null

    fun connect(configure: DatabaseConfig.Builder.() -> Unit = {}): Database {
        val config = DatabaseConfig {
            dbConfig()
            configure()
        }
        return Database.connect(connection(), databaseConfig = config, user = user, password = pass, driver = driver)
    }

    companion object {
        val allH2TestDB = listOf(H2, H2_MYSQL, H2_PSQL, H2_MARIADB, H2_ORACLE, H2_SQLSERVER)
        val mySqlRelatedDB = listOf(MYSQL, MARIADB, H2_MYSQL, H2_MARIADB)
        fun enabledDialects(): Set<TestDB> {
            return values().filterTo(enumSetOf()) { it.name in TEST_DIALECTS }
        }
    }
}

private val registeredOnShutdown = HashSet<TestDB>()

// private val postgresSQLProcess by lazy {
//    PostgreSQLContainer("postgres:13.8-alpine")
//        .withUsername("postgres")
//        .withPassword("")
//        .withDatabaseName("template1")
//        .withStartupTimeout(Duration.ofSeconds(60))
//        .withEnv("POSTGRES_HOST_AUTH_METHOD", "trust")
//        .apply {
//            listOf(
//                "timezone=UTC",
//                "synchronous_commit=off",
//                "max_connections=300",
//                "fsync=off"
//            ).forEach {
//                setCommand("postgres", "-c", it)
//            }
//            start()
//        }
// }

// private val mySQLProcess by lazy {
//    MySQLContainer("mysql:5")
//        .withDatabaseName("testdb")
//        .withEnv("MYSQL_ROOT_PASSWORD", "test")
//        .withExposedPorts().apply {
//            start()
//        }
// }

// private fun runTestContainersMySQL(): Boolean =
//    (System.getProperty("exposed.test.mysql.host") ?: System.getProperty("exposed.test.mysql8.host")).isNullOrBlank()

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
        @Parameters(name = "container: {0}, dialect: {1}")
        @JvmStatic
        fun data(): Collection<Array<Any>> {
            val container = System.getProperty("exposed.test.container")
            return TestDB.enabledDialects().map { arrayOf(container, it) }
        }
    }


    @Parameterized.Parameter(0)
    lateinit var container: String

    @Parameterized.Parameter(1)
    lateinit var dialect: Any

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
        val toTest = TestDB.enabledDialects() - excludeSettings
        Assume.assumeTrue(toTest.isNotEmpty())
        toTest.forEach { testDB ->
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
