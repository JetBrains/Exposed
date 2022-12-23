package org.jetbrains.exposed.sql.tests

import org.h2.engine.Mode
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.transactions.inTopLevelTransaction
import org.jetbrains.exposed.sql.transactions.nullableTransactionScope
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.junit.Assume
import org.junit.AssumptionViolatedException
import org.testcontainers.containers.MySQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import java.util.*
import kotlin.concurrent.thread
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.declaredMemberProperties


enum class TestDB(
    val connection: () -> String,
    val driver: String,
    val user: String = "root",
    val pass: String = "",
    val beforeConnection: () -> Unit = {},
    val afterTestFinished: () -> Unit = {},
    val dbConfig: DatabaseConfig.Builder.() -> Unit = {}
) {

    H2({ "jdbc:h2:mem:regular;DB_CLOSE_DELAY=-1;" }, "org.h2.Driver", dbConfig = {
        defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
    }),
    H2_MYSQL(
        { "jdbc:h2:mem:mysql;MODE=MySQL;DB_CLOSE_DELAY=-1" }, "org.h2.Driver",
        beforeConnection = {
            Mode::class.declaredMemberProperties.firstOrNull { it.name == "convertInsertNullToZero" }?.let { field ->
                val mode = Mode.getInstance("MySQL")
                (field as KMutableProperty1<Mode, Boolean>).set(mode, false)
            }
        }
    ),
    H2_MARIADB({ "jdbc:h2:mem:mariadb;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" }, "org.h2.Driver"),
    H2_PSQL({ "jdbc:h2:mem:psql;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1" }, "org.h2.Driver"),
    H2_ORACLE({ "jdbc:h2:mem:oracle;MODE=Oracle;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1" }, "org.h2.Driver"),
    H2_SQLSERVER({ "jdbc:h2:mem:sqlserver;MODE=MSSQLServer;DB_CLOSE_DELAY=-1" }, "org.h2.Driver"),
    SQLITE({ "jdbc:sqlite:file:test?mode=memory&cache=shared" }, "org.sqlite.JDBC"),
    MYSQL(
        connection = {
            if (runTestContainersMySQL()) {
                "${mySQLProcess.jdbcUrl}?createDatabaseIfNotExist=true&characterEncoding=UTF-8&useSSL=false&zeroDateTimeBehavior=convertToNull"
            } else {
                val host = System.getProperty("exposed.test.mysql.host") ?: System.getProperty("exposed.test.mysql8.host")
                val port = System.getProperty("exposed.test.mysql.port") ?: System.getProperty("exposed.test.mysql8.port")
                host.let { dockerHost ->
                    "jdbc:mysql://$dockerHost:$port/testdb?useSSL=false&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull"
                }
            }
        },
        user = "root",
        pass = if (runTestContainersMySQL()) "test" else "",
        driver = "com.mysql.jdbc.Driver",
        beforeConnection = { if (runTestContainersMySQL()) mySQLProcess },
        afterTestFinished = { if (runTestContainersMySQL()) mySQLProcess.close() }
    ),
    POSTGRESQL(
        { "jdbc:postgresql://localhost:5432/exposed_template1?user=exposed_template1&password=exposed_template1&lc_messages=en_US.UTF-8" }, "org.postgresql.Driver",
        user = "exposed_template1", pass = "exposed_template1"
    ),
    POSTGRESQLNG(
        { "jdbc:pgsql://localhost:5432/exposed_template1?user=exposed_template1&password=exposed_template1" }, "com.impossibl.postgres.jdbc.PGDriver",
        user = "exposed_template1", pass = "exposed_template1"
    ),
    ORACLE(
        driver = "oracle.jdbc.OracleDriver", user = "ExposedTest", pass = "12345",
        connection = {
            "jdbc:oracle:thin:@//${System.getProperty("exposed.test.oracle.host", "localhost")}" +
                ":${System.getProperty("exposed.test.oracle.port", "1521")}/XEPDB1"
        },
        beforeConnection = {
            Locale.setDefault(Locale.ENGLISH)
            val tmp = Database.connect(ORACLE.connection(), user = "sys as sysdba", password = "Oracle18", driver = ORACLE.driver)
            transaction(Connection.TRANSACTION_READ_COMMITTED, 1, db  = tmp) {
                try {
                    exec("DROP USER ExposedTest CASCADE")
                } catch (e: Exception) { // ignore
                    exposedLogger.warn("Exception on deleting ExposedTest user")
                }
                exec("CREATE USER ExposedTest ACCOUNT UNLOCK IDENTIFIED BY 12345")
                exec("grant all privileges to ExposedTest")
            }
            Unit
        }
    ),

    SQLSERVER(
        {
            "jdbc:sqlserver://${System.getProperty("exposed.test.sqlserver.host", "192.168.99.100")}" +
                ":${System.getProperty("exposed.test.sqlserver.port", "32781")}"
        },
        "com.microsoft.sqlserver.jdbc.SQLServerDriver", "SA", "yourStrong(!)Password"
    ),

    MARIADB(
        {
            "jdbc:mariadb://${System.getProperty("exposed.test.mariadb.host", "192.168.99.100")}" +
                ":${System.getProperty("exposed.test.mariadb.port", "3306")}/testdb"
        },
        "org.mariadb.jdbc.Driver"
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
        fun enabledInTests(): Set<TestDB> {
            val concreteDialects = System.getProperty("exposed.test.dialects", "")
                .split(",")
                .mapTo(HashSet()) { it.trim().uppercase() }
            return values().filterTo(enumSetOf()) { it.name in concreteDialects }
        }
    }
}

private val registeredOnShutdown = HashSet<TestDB>()

private val mySQLProcess by lazy {
    MySQLContainer("mysql:5")
        .withDatabaseName("testdb")
        .withEnv("MYSQL_ROOT_PASSWORD", "test")
        .withExposedPorts()
        .apply { start() }
}

private fun runTestContainersMySQL(): Boolean =
    (System.getProperty("exposed.test.mysql.host") ?: System.getProperty("exposed.test.mysql8.host")).isNullOrBlank()

internal var currentTestDB by nullableTransactionScope<TestDB>()

@Suppress("UnnecessaryAbstractClass")
abstract class DatabaseTestsBase {
    init {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private object CurrentTestDBInterceptor : StatementInterceptor {
        override fun keepUserDataInTransactionStoreOnCommit(userData: Map<Key<*>, Any?>): Map<Key<*>, Any?> {
            return userData.filterValues { it is TestDB }
        }
    }

    fun withDb(dbSettings: TestDB, statement: Transaction.(TestDB) -> Unit) {
        try {
            Assume.assumeTrue(dbSettings in TestDB.enabledInTests())
        } catch (e: AssumptionViolatedException) {
            exposedLogger.warn("$dbSettings is not enabled for being used in tests", e)
            throw e
        }

        if (dbSettings !in registeredOnShutdown) {
            dbSettings.beforeConnection()
            Runtime.getRuntime().addShutdownHook(
                thread(false) {
                    dbSettings.afterTestFinished()
                    registeredOnShutdown.remove(dbSettings)
                }
            )
            registeredOnShutdown += dbSettings
            dbSettings.db = dbSettings.connect()
        }

        val database = dbSettings.db!!
        try {
            transaction(database.transactionManager.defaultIsolationLevel, 1, db = database) {
                registerInterceptor(CurrentTestDBInterceptor)
                currentTestDB = dbSettings
                statement(dbSettings)
            }
        } catch (e: SQLException) {
            throw e
        } catch (e: Exception) {
            throw Exception("Failed on ${dbSettings.name}", e)
        }
    }

    fun withDb(db: List<TestDB>? = null, excludeSettings: List<TestDB> = emptyList(), statement: Transaction.(TestDB) -> Unit) {
        val enabledInTests = TestDB.enabledInTests()
        val toTest = db?.intersect(enabledInTests) ?: (enabledInTests - excludeSettings)
        Assume.assumeTrue(toTest.isNotEmpty())
        toTest.forEach { dbSettings ->
            @Suppress("TooGenericExceptionCaught")
            try {
                withDb(dbSettings, statement)
            } catch (e: Exception) {
                throw AssertionError("Failed on ${dbSettings.name}", e)
            }
        }
    }

    fun withTables(excludeSettings: List<TestDB>, vararg tables: Table, statement: Transaction.(TestDB) -> Unit) {
        val toTest = TestDB.enabledInTests() - excludeSettings
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
                        inTopLevelTransaction(database.transactionManager.defaultIsolationLevel, 1, db = database) {
                            SchemaUtils.drop(*tables)
                        }
                    }
                }
            }
        }
    }

    fun withSchemas(excludeSettings: List<TestDB>, vararg schemas: Schema, statement: Transaction.() -> Unit) {
        val toTest = TestDB.enabledInTests() - excludeSettings
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

    fun withTables(vararg tables: Table, statement: Transaction.(TestDB) -> Unit) =
        withTables(excludeSettings = emptyList(), tables = tables, statement = statement)

    fun withSchemas(vararg schemas: Schema, statement: Transaction.() -> Unit) =
        withSchemas(excludeSettings = emptyList(), schemas = schemas, statement = statement)

    fun addIfNotExistsIfSupported() = if (currentDialectTest.supportsIfNotExists) {
        "IF NOT EXISTS "
    } else {
        ""
    }

    protected fun prepareSchemaForTest(schemaName: String) : Schema {
        return Schema(schemaName, defaultTablespace = "USERS", temporaryTablespace = "TEMP ", quota = "20M", on = "USERS")
    }
}
