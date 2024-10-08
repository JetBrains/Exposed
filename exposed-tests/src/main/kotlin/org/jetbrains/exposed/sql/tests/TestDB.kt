package org.jetbrains.exposed.sql.tests

import org.h2.engine.Mode
import org.jetbrains.exposed.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.statements.api.DatabaseApi
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.util.*
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.declaredMemberProperties

@Suppress("LongParameterList")
enum class TestDB(
    val connection: () -> String,
    val r2dbcConnection: () -> String,
    val driver: String,
    val user: String = "root",
    val pass: String = "Exposed_password_1!",
    val beforeConnection: () -> Unit = {},
    val afterTestFinished: () -> Unit = {},
    val dbConfig: DatabaseConfig.Builder.() -> Unit = {}
) {
    H2_V1(
        { "jdbc:h2:mem:regular;DB_CLOSE_DELAY=-1;" },
        { error("An R2DBC connection string is not configured for this TestDB") },
        "org.h2.Driver",
        dbConfig = {
            defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
        }
    ),
    H2_V1_MYSQL(
        { "jdbc:h2:mem:mysql;MODE=MySQL;DB_CLOSE_DELAY=-1;" },
        { error("An R2DBC connection string is not configured for this TestDB") },
        "org.h2.Driver",
        beforeConnection = {
            Mode::class.declaredMemberProperties.firstOrNull { it.name == "convertInsertNullToZero" }?.let { field ->
                val mode = Mode.getInstance("MySQL")
                @Suppress("UNCHECKED_CAST")
                (field as KMutableProperty1<Mode, Boolean>).set(mode, false)
            }
        }
    ),
    H2_V2(
        { "jdbc:h2:mem:regular;DB_CLOSE_DELAY=-1;" },
        { H2_V2.connection().replace("jdbc:h2:mem:", "r2dbc:h2:mem:///") },
        "org.h2.Driver",
        dbConfig = {
            defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
        }
    ),
    H2_V2_MYSQL(
        { "jdbc:h2:mem:mysql;MODE=MySQL;DB_CLOSE_DELAY=-1;" },
        { H2_V2_MYSQL.connection().replace("jdbc:h2:mem:", "r2dbc:h2:mem:///") },
        "org.h2.Driver",
        beforeConnection = H2_V1_MYSQL.beforeConnection
    ),
    H2_V2_MARIADB(
        { "jdbc:h2:mem:mariadb;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;" },
        { H2_V2_MARIADB.connection().replace("jdbc:h2:mem:", "r2dbc:h2:mem:///") },
        "org.h2.Driver",
        pass = "root"
    ),
    H2_V2_PSQL(
        { "jdbc:h2:mem:psql;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;" },
        { H2_V2_PSQL.connection().replace("jdbc:h2:mem:", "r2dbc:h2:mem:///") },
        "org.h2.Driver"
    ),
    H2_V2_ORACLE(
        { "jdbc:h2:mem:oracle;MODE=Oracle;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;" },
        { H2_V2_ORACLE.connection().replace("jdbc:h2:mem:", "r2dbc:h2:mem:///") },
        "org.h2.Driver"
    ),
    H2_V2_SQLSERVER(
        { "jdbc:h2:mem:sqlserver;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;" },
        { H2_V2_SQLSERVER.connection().replace("jdbc:h2:mem:", "r2dbc:h2:mem:///") },
        "org.h2.Driver"
    ),
    SQLITE(
        { "jdbc:sqlite:file:test?mode=memory&cache=shared" },
        { error("An R2DBC connection string is not configured for ${SQLITE.name}") },
        "org.sqlite.JDBC"
    ),
    MYSQL_V5(
        { "jdbc:mysql://127.0.0.1:3001/testdb?useSSL=false&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull" },
        {
            MYSQL_V5.connection()
                .replace("jdbc:mysql://", "r2dbc:mysql://${MYSQL_V5.user}:${MYSQL_V5.pass}@")
        },
        "com.mysql.jdbc.Driver"
    ),
    MYSQL_V8(
        {
            "jdbc:mysql://127.0.0.1:3002/testdb" +
                "?useSSL=false&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull&allowPublicKeyRetrieval=true"
        },
        {
            MYSQL_V8.connection()
                .replace("jdbc:mysql://", "r2dbc:mysql://${MYSQL_V8.user}:${MYSQL_V8.pass}@")
        },
        "com.mysql.cj.jdbc.Driver"
    ),
    POSTGRESQL(
        { "jdbc:postgresql://127.0.0.1:3004/postgres?lc_messages=en_US.UTF-8" },
        {
            POSTGRESQL.connection()
                .replace("jdbc:postgresql://", "r2dbc:postgresql://${POSTGRESQL.user}:${POSTGRESQL.pass}@")
        },
        "org.postgresql.Driver"
    ),
    POSTGRESQLNG(
        { POSTGRESQL.connection().replace(":postgresql:", ":pgsql:") },
        // only succeeds if driver path is left as :postgresql:
        { error("An R2DBC connection string is not configured for this TestDB") },
        "com.impossibl.postgres.jdbc.PGDriver",
    ),
    ORACLE(
        { "jdbc:oracle:thin:@127.0.0.1:3003/XEPDB1" },
        // Oracle R2DBC requires jdk11+ & db version 18+ & JDBC 21.11.0.0 (ojdbc11.jar)
        { error("An R2DBC connection string is not configured for this TestDB") },
        "oracle.jdbc.OracleDriver",
        user = "ExposedTest",
        pass = "12345",
        beforeConnection = {
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
        }
    ),
    SQLSERVER(
        { "jdbc:sqlserver://127.0.0.1:3005" },
        // SQL Server R2DBC requires jdk11+
        { error("An R2DBC connection string is not configured for this TestDB") },
        "com.microsoft.sqlserver.jdbc.SQLServerDriver",
        user = "SA",
    ),
    MARIADB(
        { "jdbc:mariadb://127.0.0.1:3000/testdb" },
        {
            MARIADB.connection()
                .replace("jdbc:mariadb://", "r2dbc:mariadb://${MARIADB.user}:${MARIADB.pass}@")
        },
        "org.mariadb.jdbc.Driver"
    );

    var db: DatabaseApi? = null

    fun connect(configure: DatabaseConfig.Builder.() -> Unit = {}): Database {
        val config = DatabaseConfig {
            dbConfig()
            configure()
        }
        return Database.connect(connection(), databaseConfig = config, user = user, password = pass, driver = driver)
    }

    fun connectR2dbc(configure: DatabaseConfig.Builder.() -> Unit = {}): R2dbcDatabase {
        val config = DatabaseConfig {
            dbConfig()
            configure()
        }
        return R2dbcDatabase.connect(r2dbcConnection(), databaseConfig = config)
    }

    companion object {
        val ALL_H2_V1 = setOf(H2_V1, H2_V1_MYSQL)
        val ALL_H2_V2 = setOf(H2_V2, H2_V2_MYSQL, H2_V2_PSQL, H2_V2_MARIADB, H2_V2_ORACLE, H2_V2_SQLSERVER)
        val ALL_H2 = ALL_H2_V1 + ALL_H2_V2
        val ALL_MYSQL = setOf(MYSQL_V5, MYSQL_V8)
        val ALL_MARIADB = setOf(MARIADB)
        val ALL_MYSQL_MARIADB = ALL_MYSQL + ALL_MARIADB
        val ALL_MYSQL_LIKE = ALL_MYSQL_MARIADB + setOf(H2_V2_MYSQL, H2_V2_MARIADB, H2_V1_MYSQL)
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
