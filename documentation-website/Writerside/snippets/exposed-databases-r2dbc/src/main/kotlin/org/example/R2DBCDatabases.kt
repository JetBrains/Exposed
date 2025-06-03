package org.example

import io.r2dbc.spi.ConnectionFactoryOptions
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

class R2DBCDatabases {
    fun getH2DB(): R2dbcDatabase {
        val h2db = R2dbcDatabase.connect(
            "r2dbc:h2:mem:///test",
            databaseConfig = {
                connectionFactoryOptions {
                    option(ConnectionFactoryOptions.DRIVER, "org.h2.Driver")
                }
            }
        )
        return h2db
    }

    fun getH2DBFromFile(): R2dbcDatabase {
        val h2dbFromFile = R2dbcDatabase.connect("r2dbc:h2:./myh2file")
        return h2dbFromFile
    }

    fun getMySQLDB(): R2dbcDatabase {
        val mysqldb = R2dbcDatabase.connect(
            "r2dbc:mysql://localhost:3306/test",
            databaseConfig = {
                connectionFactoryOptions {
                    option(ConnectionFactoryOptions.USER, "user")
                    option(ConnectionFactoryOptions.PASSWORD, "password")
                }
            }
        )
        return mysqldb
    }

    fun getOracleDB(): R2dbcDatabase {
        val oracledb = R2dbcDatabase.connect(
            "r2dbc:oracle://localhost:3306/test",
            databaseConfig = {
                connectionFactoryOptions {
                    option(ConnectionFactoryOptions.USER, "user")
                    option(ConnectionFactoryOptions.PASSWORD, "password")
                }
            }
        )
        return oracledb
    }

    fun getPostgreSQLDB(): R2dbcDatabase {
        val postgresqldb = R2dbcDatabase.connect(
            url = "r2dbc:postgresql://db:5432/test",
            databaseConfig = {
                connectionFactoryOptions {
                    option(ConnectionFactoryOptions.USER, "user")
                    option(ConnectionFactoryOptions.PASSWORD, "password")
                }
            }
        )
        return postgresqldb
    }

    fun getMariaDB(): R2dbcDatabase {
        val mariadb = R2dbcDatabase.connect(
            "r2dbc:mariadb://localhost:3306/test",
            databaseConfig = {
                connectionFactoryOptions {
                    option(ConnectionFactoryOptions.USER, "root")
                    option(ConnectionFactoryOptions.PASSWORD, "your_pwd")
                }
            }
        )
        return mariadb
    }

    fun getSQLServerDB(): R2dbcDatabase {
        val sqlserverdb = R2dbcDatabase.connect(
            "r2dbc:sqlserver://localhost:32768;databaseName=test",
        )
        return sqlserverdb
    }
}
