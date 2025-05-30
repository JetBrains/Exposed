package org.example.examples

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.addLogger
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

class R2DBCExamples {
    val h2dbFromFile = R2dbcDatabase.connect("r2dbc:h2:./myh2file")
    val h2db = R2dbcDatabase.connect("r2dbc:h2:mem:///test")
    val mariadb = R2dbcDatabase.connect(
        "r2dbc:mariadb://localhost:3306/test",
        databaseConfig = {}
    )
    val mysqldb = R2dbcDatabase.connect(
        "r2dbc:mysql://localhost:3306/test",
        databaseConfig = {
        }
    )

    val oracledb = R2dbcDatabase.connect(
        "r2dbc:oracle://localhost:3306/test",
        databaseConfig = {
        }
    )

    val postgresqldb = R2dbcDatabase.connect(
        "r2dbc:postgresql://localhost:3306/test",
        databaseConfig = {
        }
    )

    val sqlserverdb = R2dbcDatabase.connect(
        "r2dbc:sqlserver://localhost:3306/test",
        databaseConfig = {
        }
    )

    suspend fun openTransaction(db: Database) {
        suspendTransaction {
            addLogger(StdOutSqlLogger)
        }
    }
}
