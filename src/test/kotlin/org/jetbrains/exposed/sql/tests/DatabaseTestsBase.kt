package org.jetbrains.exposed.sql.tests

import com.mysql.management.MysqldResource
import com.mysql.management.driverlaunched.MysqldResourceNotFoundException
import com.mysql.management.driverlaunched.ServerLauncherSocketFactory
import com.mysql.management.util.Files
import com.opentable.db.postgres.embedded.EmbeddedPostgres
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.*
import org.joda.time.DateTimeZone
import java.util.*
import kotlin.concurrent.thread

enum class TestDB(val dialect: DatabaseDialect, val connection: String, val driver: String, val user: String = "root", val pass: String = "",
                  val beforeConnection: () -> Any = {Unit}, val afterTestFinished: () -> Unit = {}) {
    H2(H2Dialect, "jdbc:h2:mem:", "org.h2.Driver"),
    SQLITE(SQLiteDialect, "jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC"),
    MYSQL(MysqlDialect, "jdbc:mysql:mxj://localhost:12345/testdb1?createDatabaseIfNotExist=true&server.initialize-user=false&user=root&password=", "com.mysql.jdbc.Driver",
            beforeConnection = { System.setProperty(Files.USE_TEST_DIR, java.lang.Boolean.TRUE!!.toString()); Files().cleanTestDir(); Unit },
            afterTestFinished = {
                try {
                    val baseDir = Files().tmp(MysqldResource.MYSQL_C_MXJ)
                    ServerLauncherSocketFactory.shutdown(baseDir, null)
                } catch (e: MysqldResourceNotFoundException) {
                    exposedLogger.warn(e.message, e)
                } finally {
                    Files().cleanTestDir()
                }
            }),
    POSTGRESQL(PostgreSQLDialect, "jdbc:postgresql://localhost:12346/template1?user=postgres&password=&lc_messages=en_US.UTF-8", "org.postgresql.Driver",
            beforeConnection = { postgresSQLProcess }, afterTestFinished = { postgresSQLProcess.close() }),
    ORACLE(OracleDialect, driver = "oracle.jdbc.OracleDriver", user = "ExposedTest", pass = "12345",
            connection = ("jdbc:oracle:thin:@//${System.getProperty("exposed.test.oracle.host", "192.168.99.100")}" +
                        ":${System.getProperty("exposed.test.oracle.port", "1521")}/xe"),
            beforeConnection = {
                Locale.setDefault(Locale.ENGLISH)
                Database.connect(ORACLE.connection, user = "sys as sysdba", password = "oracle", driver = ORACLE.driver)
                transaction(java.sql.Connection.TRANSACTION_READ_COMMITTED, 1) {
                    try {
                        exec("DROP USER ExposedTest CASCADE")
                    } catch (e: Exception) { // ignore
                        exposedLogger.warn("Exception on deleting ExposedTest user", e)
                    }

                    exec("CREATE USER ExposedTest IDENTIFIED BY 12345 DEFAULT TABLESPACE system QUOTA UNLIMITED ON system")
                    exec("grant all privileges to ExposedTest IDENTIFIED BY 12345")
                    exec("grant dba to ExposedTest IDENTIFIED BY 12345")
                }
                Unit
            });

    companion object {
        fun enabledInTests(): List<TestDB> {
            val concreteDialects = System.getProperty("exposed.test.dialects", "h2,sqlite,mysql,postgresql").let {
                if (it == "") emptyList()
                else it.split(',').map { it.trim().toUpperCase() }
            }
            return values().filter { concreteDialects.isEmpty() || it.name in concreteDialects }
        }
    }
}

private val registeredOnShutdown = HashSet<TestDB>()

private val postgresSQLProcess by lazy {
//    val locale = if (PlatformUtil.isWindows()) "american_usa" else "en_US.UTF-8"
    EmbeddedPostgres.builder()
        .setPgBinaryResolver{ system, _ ->
            EmbeddedPostgres::class.java.getResourceAsStream("/postgresql-$system-x86_64.txz")
        }/*.setLocaleConfig("locale", locale)*/
        .setPort(12346).start()
}

abstract class DatabaseTestsBase {
    fun withDb(dbSettings: TestDB, statement: Transaction.() -> Unit) {
        if (dbSettings !in TestDB.enabledInTests()) return
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        DateTimeZone.setDefault(DateTimeZone.UTC)

        if (dbSettings !in registeredOnShutdown) {
            dbSettings.beforeConnection()
            Runtime.getRuntime().addShutdownHook(thread(false ){ dbSettings.afterTestFinished() })
            registeredOnShutdown += dbSettings
        }

        val database = Database.connect(dbSettings.connection, user = dbSettings.user, password = dbSettings.pass, driver = dbSettings.driver)

        transaction(database.metadata.defaultTransactionIsolation, 1) {
            statement()
        }
    }

    fun withDb(statement: Transaction.() -> Unit) {
        TestDB.enabledInTests().forEach {
            withDb(it, statement)
        }
    }

    fun withTables (excludeSettings: List<TestDB>, vararg tables: Table, statement: Transaction.() -> Unit) {
        (TestDB.enabledInTests().toList() - excludeSettings).forEach {
            withDb(it) {
                SchemaUtils.create(*tables)
                try {
                    statement()
                    commit() // Need commit to persist data before drop tables
                } finally {
                    SchemaUtils.drop (*tables)
                }
            }
        }
    }

    fun withTables (vararg tables: Table, statement: Transaction.() -> Unit) = withTables(excludeSettings = emptyList(), tables = *tables, statement = statement)

    fun <T>Transaction.assertEquals(exp: T, act: T) = kotlin.test.assertEquals(exp, act, "Failed on ${currentDialect.name}")
    fun <T>Transaction.assertEquals(exp: T, act: List<T>) = kotlin.test.assertEquals(exp, act.single(), "Failed on ${currentDialect.name}")
}
