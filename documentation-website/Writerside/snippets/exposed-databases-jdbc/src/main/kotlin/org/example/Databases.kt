package org.example

import org.jetbrains.exposed.v1.jdbc.Database

object DbSettings {
    val db by lazy {
        Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
    }
}

class Databases {
    fun getH2DB(): Database {
        val h2db = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
        return h2db
    }

    fun getH2DBFromFile(): Database {
        val h2dbFromFile = Database.connect("jdbc:h2:./myh2file", driver = "org.h2.Driver")
        return h2dbFromFile
    }

    fun getMySQLDB(): Database {
        val mysqldb = Database.connect(
            "jdbc:mysql://localhost:3306/test",
            driver = "com.mysql.cj.jdbc.Driver",
            user = "user",
            password = "password"
        )
        return mysqldb
    }

    fun getOracleDB(): Database {
        val oracledb = Database.connect(
            "jdbc:oracle:thin:@//localhost:1521/test",
            driver = "oracle.jdbc.OracleDriver",
            user = "user",
            password = "password"
        )
        return oracledb
    }

    fun getPostgreSQLDB(): Database {
        val postgresqldb = Database.connect(
            "jdbc:postgresql://localhost:12346/test",
            driver = "org.postgresql.Driver",
            user = "user",
            password = "password"
        )
        return postgresqldb
    }

    fun getMariaDB(): Database {
        val mariadb = Database.connect(
            "jdbc:mariadb://localhost:3306/test",
            driver = "org.mariadb.jdbc.Driver",
            user = "root",
            password = "your_pwd"
        )
        return mariadb
    }

    fun getSQLServerDB(): Database {
        val sqlserverdb = Database.connect(
            "jdbc:sqlserver://localhost:32768;databaseName=test",
            "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            user = "user",
            password = "password"
        )
        return sqlserverdb
    }

    fun getPostgreSQLNGDB(): Database {
        val postgresqldbNG = Database.connect(
            "jdbc:pgsql://localhost:12346/test",
            driver = "com.impossibl.postgres.jdbc.PGDriver",
            user = "user",
            password = "password"
        )
        return postgresqldbNG
    }
}
