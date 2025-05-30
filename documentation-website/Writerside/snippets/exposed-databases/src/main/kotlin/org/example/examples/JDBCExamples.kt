package org.example.examples

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.addLogger
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class JDBCExamples {
    val h2dbFromFile = Database.connect("jdbc:h2:./myh2file", driver = "org.h2.Driver")
    val h2db = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
    val mariadb = Database.connect(
        "jdbc:mariadb://localhost:3306/test",
       driver = "org.mariadb.jdbc.Driver",
       user = "root",
       password = "your_pwd"
    )
    val mysqldb = Database.connect(
        "jdbc:mysql://localhost:3306/test",
        driver = "com.mysql.cj.jdbc.Driver",
        user = "user",
        password = "password"
    )

    val oracledb = Database.connect(
        "jdbc:oracle:thin:@//localhost:1521/test",
        driver = "oracle.jdbc.OracleDriver",
        user = "user",
        password = "password"
    )

    val postgresqldb = Database.connect(
        "jdbc:postgresql://localhost:12346/test",
        driver = "org.postgresql.Driver",
        user = "user",
        password = "password"
    )

    val postgresqldbNG = Database.connect(
        "jdbc:pgsql://localhost:12346/test",
        driver = "com.impossibl.postgres.jdbc.PGDriver",
        user = "user",
        password = "password"
    )

    val sqlserverdb = Database.connect(
        "jdbc:sqlserver://localhost:32768;databaseName=test",
        "com.microsoft.sqlserver.jdbc.SQLServerDriver",
        user = "user",
        password = "password"
    )

    object DbSettings {
        val db by lazy {
            Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
        }
    }

    fun testDatabase(db: Database) {
        transaction (db) {
            addLogger(StdOutSqlLogger)
        }
    }
}
