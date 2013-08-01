package kotlin.sql

import java.sql.Connection
import org.h2.engine.Session
import java.sql.Driver
import java.util.regex.Pattern
import java.sql.Statement
import java.sql.ResultSet

open class Session (val connection: Connection, val driver: Driver) {
    val identityQuoteString = connection.getMetaData()!!.getIdentifierQuoteString()!!
    val extraNameCharacters = connection.getMetaData()!!.getExtraNameCharacters()!!
    val identifierPattern = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$")

    fun count(column: Column<*>): Count {
        return Count(column)
    }

    fun FieldSet.select(where: Op) : Query {
        return Query(this@Session, this, where)
    }

    fun FieldSet.selectAll() : Query {
        return Query(this@Session, this, null)
    }

    fun delete(table: Table): DeleteQuery {
        return DeleteQuery(this, table)
    }

    fun <T:Table> T.insert(body: T.(InsertQuery)->Unit): InsertQuery {
        val answer = InsertQuery(this)
        body(answer)
        answer.execute(this@Session)
        return answer
    }

    fun <T:Table> T.update(where: Op, body: T.(UpdateQuery)->Unit): UpdateQuery {
        val answer = UpdateQuery(this, where)
        body(answer)
        answer.execute(this@Session)
        return answer
    }

    fun create(vararg tables: Table) {
        if (tables.size > 0) {
            for (table in tables) {
                val ddl = table.ddl
                println("SQL: " + ddl.toString())
                connection.createStatement()?.executeUpdate(ddl.toString())
                if (table.foreignKeys.size > 0) {
                    for (foreignKey in table.foreignKeys) {
                        val fKDdl = foreignKey(foreignKey);
                        println("SQL: " + fKDdl)
                        connection.createStatement()?.executeUpdate(fKDdl)
                    }
                }
            }
        }
    }

    fun drop(vararg tables: Table) {
        if (tables.size > 0) {
            for (table in tables) {
                val ddl = StringBuilder("DROP TABLE ${identity(table)}")
                println("SQL: " + ddl.toString())
                connection.createStatement()?.executeUpdate(ddl.toString())
            }
        }
    }

    fun identity(table: Table): String {
        return if (identifierPattern.matcher(table.tableName).matches())
            table.tableName else "$identityQuoteString${table.tableName}$identityQuoteString"
    }

    fun fullIdentity(column: Column<*>): String {
        return (if (identifierPattern.matcher(column.table.tableName).matches())
            column.table.tableName else "$identityQuoteString${column.table.tableName}$identityQuoteString") + "." +
        (if (identifierPattern.matcher(column.name).matches())
            column.name else "$identityQuoteString${column.name}$identityQuoteString")
    }

    fun identity(column: Column<*>): String {
        return if (identifierPattern.matcher(column.name).matches())
            column.name else "$identityQuoteString${column.name}$identityQuoteString"
    }

    fun identity(foreignKey: ForeignKey): String {
        return "$identityQuoteString${foreignKey.name}$identityQuoteString"
    }

    fun foreignKey(foreignKey: ForeignKey): String {
        return when (driver.getClass().getName()) {
            "com.mysql.jdbc.Driver", "oracle.jdbc.driver.OracleDriver",
            "com.microsoft.sqlserver.jdbc.SQLServerDriver", "org.postgresql.Driver",
            "org.h2.Driver" -> {
                "ALTER TABLE ${identity(foreignKey.table)} ADD CONSTRAINT ${identity(foreignKey)} FOREIGN KEY (${identity(foreignKey.column)}) REFERENCES ${identity(foreignKey.referencedTable)}(${identity(foreignKey.column.table.primaryKeys[0])})"
            }
            else -> throw UnsupportedOperationException("Unsupported driver: " + driver.getClass().getName())
        }
    }

    fun autoIncrement(column: Column<*>): String {
        return when (driver.getClass().getName()) {
            "com.mysql.jdbc.Driver", /*"oracle.jdbc.driver.OracleDriver",*/
            "com.microsoft.sqlserver.jdbc.SQLServerDriver", /*"org.postgresql.Driver",*/
            "org.h2.Driver" -> {
                "AUTO_INCREMENT"
            }
            else -> throw UnsupportedOperationException("Unsupported driver: " + driver.getClass().getName())
        }
    }

    class object {
        val threadLocal = ThreadLocal<Session>()

        fun get(): Session {
            return threadLocal.get()!!
        }
    }
}
