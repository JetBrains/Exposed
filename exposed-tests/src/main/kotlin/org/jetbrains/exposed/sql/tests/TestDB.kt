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
    H2({ "jdbc:h2:mem:regular;DB_CLOSE_DELAY=-1;" }, "org.h2.Driver", dbConfig = {
        defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
    }),
    H2_MYSQL({ "jdbc:h2:mem:mysql;MODE=MySQL;DB_CLOSE_DELAY=-1" }, "org.h2.Driver", beforeConnection = {
        Mode::class.declaredMemberProperties.firstOrNull { it.name == "convertInsertNullToZero" }?.let { field ->
            val mode = Mode.getInstance("MySQL")
            @Suppress("UNCHECKED_CAST")
            (field as KMutableProperty1<Mode, Boolean>).set(mode, false)
        }
    }),
    H2_MARIADB(
        { "jdbc:h2:mem:mariadb;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" },
        "org.h2.Driver",
        pass = "root"
    ),
    H2_PSQL(
        { "jdbc:h2:mem:psql;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1" },
        "org.h2.Driver"
    ),
    H2_ORACLE(
        { "jdbc:h2:mem:oracle;MODE=Oracle;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1" },
        "org.h2.Driver"
    ),
    H2_SQLSERVER({ "jdbc:h2:mem:sqlserver;MODE=MSSQLServer;DB_CLOSE_DELAY=-1" }, "org.h2.Driver"),
    SQLITE({ "jdbc:sqlite:file:test?mode=memory&cache=shared" }, "org.sqlite.JDBC"),
    MYSQL(
        connection = {
            "jdbc:mysql://127.0.0.1:3001/testdb?useSSL=false&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull"
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
            repetitionAttempts = 1

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
        val allH2TestDB = listOf(H2, H2_MYSQL, H2_PSQL, H2_MARIADB, H2_ORACLE, H2_SQLSERVER)
        val mySqlRelatedDB = listOf(MYSQL, MARIADB, H2_MYSQL, H2_MARIADB)
        val postgreSQLRelatedDB = listOf(POSTGRESQL, POSTGRESQLNG)

        fun enabledDialects(): Set<TestDB> {
            if (TEST_DIALECTS.isEmpty()) {
                return values().toSet()
            }

            return values().filterTo(enumSetOf()) { it.name in TEST_DIALECTS }
        }
    }
}
