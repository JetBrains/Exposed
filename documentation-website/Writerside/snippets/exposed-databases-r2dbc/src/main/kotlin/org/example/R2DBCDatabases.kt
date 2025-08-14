package org.example

import io.r2dbc.spi.IsolationLevel
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

class R2DBCDatabases {
    fun getH2DB(): R2dbcDatabase {
        val h2db = R2dbcDatabase.connect("r2dbc:h2:mem:///test")
        return h2db
    }

    fun getH2DBWithConfig(): R2dbcDatabase {
        val database = R2dbcDatabase.connect(
            "r2dbc:h2:mem:///test;DB_CLOSE_DELAY=-1;",
            databaseConfig = {
                defaultMaxAttempts = 1
                defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
            }
        )
        return database
    }

    fun getH2DBFromFile(): R2dbcDatabase {
        val h2dbFromFile = R2dbcDatabase.connect("r2dbc:h2:file///./myh2file")
        return h2dbFromFile
    }

    fun getMySQLDB(): R2dbcDatabase {
        val mysqldb = R2dbcDatabase.connect(
            "r2dbc:mysql://localhost:3306/test",
            driver = "mysql",
            user = "user",
            password = "password"
        )
        return mysqldb
    }

    fun getOracleDB(): R2dbcDatabase {
        val oracledb = R2dbcDatabase.connect(
            "r2dbc:oracle://localhost:3306/test",
            driver = "oracle",
            user = "user",
            password = "password"
        )
        return oracledb
    }

    fun getPostgreSQLDB(): R2dbcDatabase {
        val postgresqldb = R2dbcDatabase.connect(
            url = "r2dbc:postgresql://db:5432/test",
            driver = "postgresql",
            user = "user",
            password = "password"
        )
        return postgresqldb
    }

    fun getMariaDB(): R2dbcDatabase {
        val mariadb = R2dbcDatabase.connect(
            "r2dbc:mariadb://localhost:3306/test",
            driver = "mariadb",
            user = "root",
            password = "your_pwd"
        )
        return mariadb
    }

    fun getSQLServerDB(): R2dbcDatabase {
        val sqlserverdb = R2dbcDatabase.connect(
            "r2dbc:mssql://localhost:32768;databaseName=test",
            driver = "sqlserver",
            user = "user",
            password = "password"
        )
        return sqlserverdb
    }
}
