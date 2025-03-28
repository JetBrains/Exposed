package org.jetbrains.exposed.sql.tests

import org.jetbrains.exposed.r2dbc.sql.R2dbcDatabase
import org.jetbrains.exposed.r2dbc.sql.R2dbcDatabaseConfig
import org.jetbrains.exposed.r2dbc.sql.transactions.suspendTransaction
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.exposedLogger
import java.sql.Connection
import java.util.*

enum class TestDB(
    val connection: () -> String,
    val driver: String,
    val user: String = "root",
    val pass: String = "Exposed_password_1!",
    val beforeConnection: suspend () -> Unit = {},
    val afterTestFinished: () -> Unit = {},
    val dbConfig: DatabaseConfig.Builder.() -> Unit = {}
) {
    H2_V2(
        { "r2dbc:h2:mem:///regular;DB_CLOSE_DELAY=-1;" },
        "org.h2.Driver",
        dbConfig = {
            defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
        }
    ),
    H2_V2_MYSQL(
        { "r2dbc:h2:mem:///mysql;MODE=MySQL;DB_CLOSE_DELAY=-1" },
        "org.h2.Driver"
    ),
    H2_V2_MARIADB(
        { "r2dbc:h2:mem:///mariadb;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" },
        "org.h2.Driver",
        pass = "root"
    ),
    H2_V2_PSQL(
        { "r2dbc:h2:mem:///psql;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1" },
        "org.h2.Driver"
    ),
    H2_V2_ORACLE(
        { "r2dbc:h2:mem:///oracle;MODE=Oracle;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1" },
        "org.h2.Driver"
    ),
    H2_V2_SQLSERVER(
        { "r2dbc:h2:mem:///sqlserver;MODE=MSSQLServer;DB_CLOSE_DELAY=-1" },
        "org.h2.Driver"
    ),
    MYSQL_V5(
        {
            "r2dbc:mysql://${MYSQL_V5.user}:${MYSQL_V5.pass}@127.0.0.1:3001/testdb" +
                "?useSSL=false&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull"
        },
        "com.mysql.jdbc.Driver"
    ),
    MYSQL_V8(
        {
            "r2dbc:mysql://${MYSQL_V8.user}:${MYSQL_V8.pass}@127.0.0.1:3002/testdb" +
                "?useSSL=false&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull&allowPublicKeyRetrieval=true"
        },
        "com.mysql.cj.jdbc.Driver"
    ),
    MARIADB(
        { "r2dbc:mariadb://${MARIADB.user}:${MARIADB.pass}@127.0.0.1:3000/testdb" },
        "org.mariadb.jdbc.Driver"
    ),
    POSTGRESQL(
        { "r2dbc:postgresql://${POSTGRESQL.user}:${POSTGRESQL.pass}@127.0.0.1:3004/postgres?lc_messages=en_US.UTF-8" },
        "org.postgresql.Driver"
    ),
    ORACLE(
        { "r2dbc:oracle://${ORACLE.user}:${ORACLE.pass}@127.0.0.1:3003/FREEPDB1" },
        "oracle.jdbc.OracleDriver",
        user = "ExposedTest",
        pass = "12345",
        beforeConnection = {
            Locale.setDefault(Locale.ENGLISH)
            val tmp = R2dbcDatabase.connect("r2dbc:oracle://sys%20as%20sysdba:Oracle18@127.0.0.1:3003/FREEPDB1")
            suspendTransaction(db = tmp, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
                maxAttempts = 1

                try {
                    exec("DROP USER ExposedTest CASCADE")
                } catch (_: Exception) {
                    exposedLogger.warn("Exception on deleting ExposedTest user")
                }
                exec("CREATE USER ExposedTest ACCOUNT UNLOCK IDENTIFIED BY 12345")
                exec("GRANT ALL PRIVILEGES TO ExposedTest")
            }
            Unit
        }
    ),
    SQLSERVER(
        { "r2dbc:mssql://${SQLSERVER.user}:${SQLSERVER.pass}@127.0.0.1:3005" },
        "com.microsoft.sqlserver.jdbc.SQLServerDriver",
        user = "SA",
    );

    var db: R2dbcDatabase? = null

    fun connect(configure: R2dbcDatabaseConfig.Builder.() -> Unit = {}): R2dbcDatabase {
        val config = R2dbcDatabaseConfig {
            dbConfig()
            configure()

            setUrl(connection())
        }
        return R2dbcDatabase.connect(databaseConfig = config)
    }

    companion object {
        val ALL_H2_V2 = setOf(H2_V2, H2_V2_MYSQL, H2_V2_PSQL, H2_V2_MARIADB, H2_V2_ORACLE, H2_V2_SQLSERVER)
        val ALL_MYSQL = setOf(MYSQL_V5, MYSQL_V8)
        val ALL_MARIADB = setOf(MARIADB)
        val ALL_MYSQL_MARIADB = ALL_MYSQL + ALL_MARIADB
        val ALL_MYSQL_LIKE = ALL_MYSQL_MARIADB + setOf(H2_V2_MYSQL, H2_V2_MARIADB)
        val ALL_POSTGRES = setOf(POSTGRESQL)
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
