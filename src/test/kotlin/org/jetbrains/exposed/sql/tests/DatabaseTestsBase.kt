package org.jetbrains.exposed.sql.tests

import com.mysql.management.MysqldResource
import com.mysql.management.driverlaunched.MysqldResourceNotFoundException
import com.mysql.management.driverlaunched.ServerLauncherSocketFactory
import com.mysql.management.util.Files
import org.jetbrains.exposed.dao.EntityCache
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.*
import org.joda.time.DateTimeZone
import ru.yandex.qatools.embed.postgresql.PostgresStarter
import ru.yandex.qatools.embed.postgresql.config.AbstractPostgresConfig
import ru.yandex.qatools.embed.postgresql.config.PostgresConfig
import ru.yandex.qatools.embed.postgresql.distribution.Version
import java.util.*
import kotlin.concurrent.thread

enum class TestDB(val dialect: DatabaseDialect, val connection: String, val driver: String, val beforeConnection: () -> Any, val afterConnection: () -> Unit) {
    H2(H2Dialect, "jdbc:h2:mem:", "org.h2.Driver", {Unit}, {}),
    SQLITE(SQLiteDialect, "jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC", {Unit}, {}),
    MYSQL(MysqlDialect, "jdbc:mysql:mxj://localhost:12345/testdb1?createDatabaseIfNotExist=true&server.initialize-user=false&user=root&password=", "com.mysql.jdbc.Driver",
            beforeConnection = { System.setProperty(Files.USE_TEST_DIR, java.lang.Boolean.TRUE!!.toString()); Files().cleanTestDir(); Unit },
            afterConnection = {
                try {
                    val baseDir = Files().tmp(MysqldResource.MYSQL_C_MXJ)
                    ServerLauncherSocketFactory.shutdown(baseDir, null)
                } catch (e: MysqldResourceNotFoundException) {
                    exposedLogger.warn(e.message, e)
                } finally {
                    Files().cleanTestDir()
                }
            }),
    POSTGRESQL(PostgreSQLDialect, "jdbc:postgresql://localhost:12346/template1?user=root&password=root", "org.postgresql.Driver",
            beforeConnection = { postgresSQLProcess.start() }, afterConnection = { postgresSQLProcess.stop() });

    companion object {
        fun enabledInTests(): List<TestDB> {
            val concreteDialects = System.getProperty("exposed.test.dialects", "").let {
                if (it == "") emptyList()
                else it.split(',').map { it.trim().toUpperCase() }
            }
            return values().filter { concreteDialects.isEmpty() || it.name in concreteDialects }
        }
    }
}

private val registeredOnShutdown = HashSet<TestDB>()

private val postgresSQLProcess by lazy {
    val config = PostgresConfig(
            Version.Main.PRODUCTION, AbstractPostgresConfig.Net("localhost", 12346),
            AbstractPostgresConfig.Storage("template1"), AbstractPostgresConfig.Timeout(),
            AbstractPostgresConfig.Credentials("root", "root")
    );
    PostgresStarter.getDefaultInstance().prepare(config)
}

abstract class DatabaseTestsBase() {
    fun withDb(dbSettings: TestDB, statement: Transaction.() -> Unit) {
        if (dbSettings !in TestDB.enabledInTests()) return
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        DateTimeZone.setDefault(DateTimeZone.UTC)

        if (dbSettings !in registeredOnShutdown) {
            dbSettings.beforeConnection()
            Runtime.getRuntime().addShutdownHook(thread(false ){ dbSettings.afterConnection() })
            registeredOnShutdown += dbSettings
        }

        val database = Database.connect(dbSettings.connection, user = "root", driver = dbSettings.driver)

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
                    SchemaUtils.drop (*EntityCache.sortTablesByReferences(tables.toList()).reversed().toTypedArray())
                }
            }
        }
    }

    fun withTables (vararg tables: Table, statement: Transaction.() -> Unit) = withTables(excludeSettings = emptyList(), tables = *tables, statement = statement)

    fun <T>Transaction.assertEquals(a: T, b: T) = kotlin.test.assertEquals(a, b, "Failed on ${currentDialect.name}")
    fun <T>Transaction.assertEquals(a: T, b: List<T>) = kotlin.test.assertEquals(a, b.single(), "Failed on ${currentDialect.name}")
}
