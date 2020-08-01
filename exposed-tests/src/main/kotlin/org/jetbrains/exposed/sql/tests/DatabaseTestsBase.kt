package org.jetbrains.exposed.sql.tests

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import org.h2.engine.Mode
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.testcontainers.containers.MySQLContainer
import java.sql.Connection
import java.util.*
import kotlin.concurrent.thread

enum class TestDB(val connection: () -> String, val driver: String, val user: String = "root", val pass: String = "",
                  val beforeConnection: () -> Unit = {}, val afterTestFinished: () -> Unit = {}, var db: Database? = null) {
    H2({"jdbc:h2:mem:regular;DB_CLOSE_DELAY=-1;"}, "org.h2.Driver"),
    H2_MYSQL({"jdbc:h2:mem:mysql;MODE=MySQL;DB_CLOSE_DELAY=-1"}, "org.h2.Driver", beforeConnection = {
        Mode.getInstance("MySQL").convertInsertNullToZero = false
    }),
    SQLITE({"jdbc:sqlite:file:test?mode=memory&cache=shared"}, "org.sqlite.JDBC"),
    MYSQL(
        connection = {
            if (runTestContainersMySQL()) {
                "${mySQLProcess.jdbcUrl}?createDatabaseIfNotExist=true&characterEncoding=UTF-8&useSSL=false"
            } else {
                val host = System.getProperty("exposed.test.mysql.host") ?: System.getProperty("exposed.test.mysql8.host")
                val port = System.getProperty("exposed.test.mysql.port") ?: System.getProperty("exposed.test.mysql8.port")
                host.let { dockerHost ->
                    "jdbc:mysql://$dockerHost:$port/testdb?useSSL=false&characterEncoding=UTF-8"
                }
            }
        },
        user = "root",
        pass = if (runTestContainersMySQL()) "test" else "",
        driver = "com.mysql.jdbc.Driver",
        beforeConnection = { if (runTestContainersMySQL()) mySQLProcess },
        afterTestFinished = { if (runTestContainersMySQL()) mySQLProcess.close() }
    ),
    POSTGRESQL({"jdbc:postgresql://localhost:12346/template1?user=postgres&password=&lc_messages=en_US.UTF-8"}, "org.postgresql.Driver",
            beforeConnection = { postgresSQLProcess }, afterTestFinished = { postgresSQLProcess.close() }),
    POSTGRESQLNG({"jdbc:pgsql://localhost:12346/template1?user=postgres&password="}, "com.impossibl.postgres.jdbc.PGDriver",
            user = "postgres", beforeConnection = { postgresSQLProcess }, afterTestFinished = { postgresSQLProcess.close() }),
    ORACLE(driver = "oracle.jdbc.OracleDriver", user = "C##ExposedTest", pass = "12345",
            connection = {"jdbc:oracle:thin:@//${System.getProperty("exposed.test.oracle.host", "localhost")}" +
                    ":${System.getProperty("exposed.test.oracle.port", "1521")}/xe"},
            beforeConnection = {
                Locale.setDefault(Locale.ENGLISH)
                val tmp = Database.connect(ORACLE.connection(), user = "sys as sysdba", password = "Oracle18", driver = ORACLE.driver)
                transaction(Connection.TRANSACTION_READ_COMMITTED, 1, tmp) {
                    try {
                        exec("DROP USER C##ExposedTest CASCADE")
                    } catch (e: Exception) { // ignore
                        exposedLogger.warn("Exception on deleting C##ExposedTest user", e)
                    }
                    exec("CREATE USER C##ExposedTest IDENTIFIED BY 12345 DEFAULT TABLESPACE system QUOTA UNLIMITED ON system")
                    exec("grant all privileges to C##ExposedTest")
                    exec("grant dba to C##ExposedTest")
                }
                Unit
            }),

    SQLSERVER({"jdbc:sqlserver://${System.getProperty("exposed.test.sqlserver.host", "192.168.99.100")}" +
            ":${System.getProperty("exposed.test.sqlserver.port", "32781")}"},
            "com.microsoft.sqlserver.jdbc.SQLServerDriver", "SA", "yourStrong(!)Password"),

    MARIADB({"jdbc:mariadb://${System.getProperty("exposed.test.mariadb.host", "192.168.99.100")}" +
            ":${System.getProperty("exposed.test.mariadb.port", "3306")}/testdb"},
            "org.mariadb.jdbc.Driver");

    fun connect() = Database.connect(connection(), user = user, password = pass, driver = driver)

    companion object {
        fun enabledInTests(): List<TestDB> {
            val embeddedTests = (TestDB.values().toList() - ORACLE - SQLSERVER - MARIADB).joinToString()
            val concreteDialects = System.getProperty("exposed.test.dialects", embeddedTests).let {
                if (it == "") emptyList()
                else it.split(',').map { it.trim().toUpperCase() }
            }
            return values().filter { concreteDialects.isEmpty() || it.name in concreteDialects }
        }
    }
}

private val registeredOnShutdown = HashSet<TestDB>()

private val postgresSQLProcess by lazy {
    EmbeddedPostgres.builder()
            .setPgBinaryResolver{ system, _ ->
                EmbeddedPostgres::class.java.getResourceAsStream("/postgresql-$system-x86_64.txz")
            }
            .setPort(12346).start()
}

// MySQLContainer has to be extended, otherwise it leads to Kotlin compiler issues: https://github.com/testcontainers/testcontainers-java/issues/318
internal class SpecifiedMySQLContainer(val image: String) : MySQLContainer<SpecifiedMySQLContainer>(image)

private val mySQLProcess by lazy {
    SpecifiedMySQLContainer(image = "mysql:5")
            .withDatabaseName("testdb")
            .withEnv("MYSQL_ROOT_PASSWORD", "test")
            .withExposedPorts().apply {
               start()
            }
}

private fun runTestContainersMySQL(): Boolean =
    (System.getProperty("exposed.test.mysql.host") ?: System.getProperty("exposed.test.mysql8.host")).isNullOrBlank()

abstract class DatabaseTestsBase {
    init {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }
    fun withDb(dbSettings: TestDB, statement: Transaction.(TestDB) -> Unit) {
        if (dbSettings !in TestDB.enabledInTests()) {
            exposedLogger.warn("$dbSettings is not enabled for being used in tests", RuntimeException())
            return
        }

        if (dbSettings !in registeredOnShutdown) {
            dbSettings.beforeConnection()
            Runtime.getRuntime().addShutdownHook(thread(false){
                dbSettings.afterTestFinished()
                registeredOnShutdown.remove(dbSettings)
            })
            registeredOnShutdown += dbSettings
            dbSettings.db = dbSettings.connect()
        }

        val database = dbSettings.db!!

        transaction(database.transactionManager.defaultIsolationLevel, 1, db = database) {
            statement(dbSettings)
        }
    }

    fun withDb(db : List<TestDB>? = null, excludeSettings: List<TestDB> = emptyList(), statement: Transaction.(TestDB) -> Unit) {
        val enabledInTests = TestDB.enabledInTests()
        val toTest = db?.intersect(enabledInTests) ?: enabledInTests - excludeSettings
        toTest.forEach { dbSettings ->
            try {
                withDb(dbSettings, statement)
            } catch (e: Exception) {
                throw AssertionError("Failed on ${dbSettings.name}", e)
            }
        }
    }

    fun withTables (excludeSettings: List<TestDB>, vararg tables: Table, statement: Transaction.(TestDB) -> Unit) {
        (TestDB.enabledInTests() - excludeSettings).forEach { testDB ->
            withDb(testDB) {
                SchemaUtils.create(*tables)
                try {
                    statement(testDB)
                    commit() // Need commit to persist data before drop tables
                } finally {
                    SchemaUtils.drop(*tables)
                    commit()
                }
            }
        }
    }

    fun withSchemas (excludeSettings: List<TestDB>, vararg schemas: Schema, statement: Transaction.() -> Unit) {
        (TestDB.enabledInTests() - excludeSettings).forEach { testDB ->
            withDb(testDB) {
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

    fun withTables (vararg tables: Table, statement: Transaction.(TestDB) -> Unit) = withTables(excludeSettings = emptyList(), tables = *tables, statement = statement)

    fun withSchemas (vararg schemas: Schema, statement: Transaction.() -> Unit) = withSchemas(excludeSettings = emptyList(), schemas = *schemas, statement = statement)

    fun addIfNotExistsIfSupported() = if (currentDialectTest.supportsIfNotExists) {
        "IF NOT EXISTS "
    } else {
        ""
    }

}
