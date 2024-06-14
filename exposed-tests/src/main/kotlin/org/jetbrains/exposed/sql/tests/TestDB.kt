package org.jetbrains.exposed.sql.tests

import org.h2.engine.Mode
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.util.*
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.declaredMemberProperties

enum class TestDB(
    val connection: () -> String,
    val driver: String,
    val user: String = "root",
    val pass: String = "Exposed_password_1!",
    val beforeConnection: () -> Unit = {},
    val afterTestFinished: () -> Unit = {},
    val dbConfig: DatabaseConfig.Builder.() -> Unit = {}
) {
    H2_V1({ "jdbc:h2:mem:regular;DB_CLOSE_DELAY=-1;" }, "org.h2.Driver", dbConfig = {
        defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
    }),
    H2_V1_MYSQL({ "jdbc:h2:mem:mysql;MODE=MySQL;DB_CLOSE_DELAY=-1" }, "org.h2.Driver", beforeConnection = {
        Mode::class.declaredMemberProperties.firstOrNull { it.name == "convertInsertNullToZero" }?.let { field ->
            val mode = Mode.getInstance("MySQL")
            @Suppress("UNCHECKED_CAST")
            (field as KMutableProperty1<Mode, Boolean>).set(mode, false)
        }
    }),
    H2_V2({ "jdbc:h2:mem:regular;DB_CLOSE_DELAY=-1;" }, "org.h2.Driver", dbConfig = {
        defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
    }),
    H2_V2_MYSQL({ "jdbc:h2:mem:mysql;MODE=MySQL;DB_CLOSE_DELAY=-1" }, "org.h2.Driver", beforeConnection = H2_V1_MYSQL.beforeConnection),
    H2_V2_MARIADB(
        { "jdbc:h2:mem:mariadb;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" },
        "org.h2.Driver",
        pass = "root"
    ),
    H2_V2_PSQL(
        { "jdbc:h2:mem:psql;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1" },
        "org.h2.Driver"
    ),
    H2_V2_ORACLE(
        { "jdbc:h2:mem:oracle;MODE=Oracle;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1" },
        "org.h2.Driver"
    ),
    H2_V2_SQLSERVER({ "jdbc:h2:mem:sqlserver;MODE=MSSQLServer;DB_CLOSE_DELAY=-1" }, "org.h2.Driver"),
    SQLITE({ "jdbc:sqlite:file:test?mode=memory&cache=shared" }, "org.sqlite.JDBC"),
    MYSQL_V5(
        connection = {
            "jdbc:mysql://127.0.0.1:3001/" +
                "testdb" +
                "?useSSL=false" +
                "&characterEncoding=UTF-8" +
                "&zeroDateTimeBehavior=convertToNull"
        },
        driver = "com.mysql.jdbc.Driver"
    ),
    MYSQL_V8(
        connection = {
            "jdbc:mysql://127.0.0.1:3002/" +
                "testdb" +
                "?useSSL=false" +
                "&characterEncoding=UTF-8" +
                "&zeroDateTimeBehavior=convertToNull" +
                "&allowPublicKeyRetrieval=true"
        },
        driver = "com.mysql.cj.jdbc.Driver"
    ),
    POSTGRESQL(
        { "jdbc:postgresql://127.0.0.1:3004/postgres?lc_messages=en_US.UTF-8" },
        "org.postgresql.Driver",
        beforeConnection = { },
        afterTestFinished = { }
    ),
    POSTGRESQLNG(
        { POSTGRESQL.connection().replace(":postgresql:", ":pgsql:") },
        "com.impossibl.postgres.jdbc.PGDriver",
    ),
    ORACLE(driver = "oracle.jdbc.OracleDriver", user = "ExposedTest", pass = "12345", connection = {
        "jdbc:oracle:thin:@127.0.0.1:3003/XEPDB1"
    }, beforeConnection = {
        Locale.setDefault(Locale.ENGLISH)
        val tmp = Database.connect(
            ORACLE.connection(),
            user = "sys as sysdba",
            password = "Oracle18",
            driver = ORACLE.driver
        )
        transaction(Connection.TRANSACTION_READ_COMMITTED, db = tmp) {
            maxAttempts = 1

            @Suppress("SwallowedException", "TooGenericExceptionCaught")
            try {
                exec("DROP USER ExposedTest CASCADE")
            } catch (e: Exception) { // ignore
                exposedLogger.warn("Exception on deleting ExposedTest user")
            }
            exec("CREATE USER ExposedTest ACCOUNT UNLOCK IDENTIFIED BY 12345")
            exec("grant all privileges to ExposedTest")
        }
        Unit
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
        val ALL_H2_V1 = listOf(H2_V1, H2_V1_MYSQL)
        val ALL_H2_V2 = listOf(H2_V2, H2_V2_MYSQL, H2_V2_PSQL, H2_V2_MARIADB, H2_V2_ORACLE, H2_V2_SQLSERVER)
        val ALL_H2 = ALL_H2_V1 + ALL_H2_V2
        val ALL_MYSQL = listOf(MYSQL_V5, MYSQL_V8)
        val ALL_MARIADB = listOf(MARIADB)
        val ALL_MYSQL_MARIADB = ALL_MYSQL + ALL_MARIADB
        val ALL_MYSQL_LIKE = ALL_MYSQL_MARIADB + listOf(H2_V2_MYSQL, H2_V2_MARIADB, H2_V1_MYSQL)
        val ALL_POSTGRES = listOf(POSTGRESQL, POSTGRESQLNG)
        val ALL_POSTGRES_LIKE = ALL_POSTGRES + listOf(H2_V2_PSQL)
        val ALL_ORACLE_LIKE = listOf(ORACLE, H2_V2_ORACLE)
        val ALL_SQLSERVER_LIKE = listOf(SQLSERVER, H2_V2_SQLSERVER)
        val ALL = TestDB.entries.toList()

        fun enabledDialects(): Set<TestDB> {
            if (TEST_DIALECTS.isEmpty()) {
                return entries.toSet()
            }

            return entries.filterTo(enumSetOf()) { it.name in TEST_DIALECTS }
        }
    }
}
