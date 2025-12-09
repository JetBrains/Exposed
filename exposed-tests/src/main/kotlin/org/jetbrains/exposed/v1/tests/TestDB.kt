package org.jetbrains.exposed.v1.tests

import org.h2.engine.Mode
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
    H2_V2({ "jdbc:h2:mem:regular;DB_CLOSE_DELAY=-1;" }, "org.h2.Driver", dbConfig = {
        defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
    }),
    H2_V2_MYSQL(
        { "jdbc:h2:mem:mysql;MODE=MySQL;DB_CLOSE_DELAY=-1" },
        "org.h2.Driver",
        beforeConnection = {
            Mode::class.declaredMemberProperties.firstOrNull { it.name == "convertInsertNullToZero" }?.let { field ->
                val mode = Mode.getInstance("MySQL")
                @Suppress("UNCHECKED_CAST")
                (field as KMutableProperty1<Mode, Boolean>).set(mode, false)
            }
        }
    ),
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
                "&allowPublicKeyRetrieval=true" +
                "&yearIsDateType=false"
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
        "jdbc:oracle:thin:@127.0.0.1:3003/FREEPDB1"
    }, beforeConnection = {
        Locale.setDefault(Locale.ENGLISH)
        val tmp = Database.connect(
            ORACLE.connection(),
            user = "sys as sysdba",
            password = "Oracle18",
            driver = ORACLE.driver
        )
        transaction(tmp, Connection.TRANSACTION_READ_COMMITTED) {
            maxAttempts = 1

            @Suppress("SwallowedException", "TooGenericExceptionCaught")
            try {
                exec("CREATE TABLESPACE users DATAFILE '/opt/oracle/oradata/FREE/FREEPDB1/users01.dbf' SIZE 100M AUTOEXTEND ON NEXT 50M MAXSIZE UNLIMITED")
            } catch (e: Exception) { // ignore
                exposedLogger.warn("Tablespace users already exists", e)
            }

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
            "jdbc:sqlserver://127.0.0.1:3005;encrypt=false;"
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
        val ALL_H2_V2 = setOf(H2_V2, H2_V2_MYSQL, H2_V2_PSQL, H2_V2_MARIADB, H2_V2_ORACLE, H2_V2_SQLSERVER)
        val ALL_MYSQL = setOf(MYSQL_V5, MYSQL_V8)
        val ALL_MYSQL_MARIADB = ALL_MYSQL + MARIADB
        val ALL_MYSQL_LIKE = ALL_MYSQL_MARIADB + setOf(H2_V2_MYSQL, H2_V2_MARIADB)
        val ALL_POSTGRES = setOf(POSTGRESQL, POSTGRESQLNG)
        val ALL_POSTGRES_LIKE = ALL_POSTGRES + setOf(H2_V2_PSQL)
        val ALL_ORACLE_LIKE = setOf(ORACLE, H2_V2_ORACLE)
        val ALL_SQLSERVER_LIKE = setOf(SQLSERVER, H2_V2_SQLSERVER)
        val ALL = TestDB.entries.toSet()

        fun enabledDialects(): Set<TestDB> {
            if (TEST_DIALECTS.isEmpty()) {
                return entries.toSet()
            }

            return entries.filterTo(enumSetOf()) { it.name in TEST_DIALECTS }
        }
    }
}
