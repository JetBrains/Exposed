package kotlin.sql

import java.sql.Connection
import org.h2.engine.Session
import java.sql.Driver
import java.util.regex.Pattern
import java.sql.Statement
import java.sql.ResultSet
import java.util.HashMap

public class Key<T>()
open class UserDataHolder() {
    private val userdata = HashMap<Key<*>, Any?>()

    public fun <T:Any> getOrCreate(key: Key<T>, init: ()->T): T {
        if (userdata.containsKey(key)) {
            return userdata[key] as T
        }

        val new = init()
        userdata[key] = new
        return new
    }
}

open class Session (val connection: Connection, val driver: Driver): UserDataHolder() {
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
                // create table
                val ddl = table.ddl
                log(ddl)
                connection.createStatement()?.executeUpdate(ddl.toString())

                // create indices
                for (table_index in table.indices) {
                    val alterTable = index(table_index)
                    log(alterTable)
                    connection.createStatement()?.executeUpdate(alterTable.toString())
                }
            }

            for (table in tables) {
                // foreign keys
                for (column in table.columns) {
                    if (column.referee != null) {
                        val fKDdl = foreignKey(column);
                        log(fKDdl)
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
                log(ddl)
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

    fun foreignKey(reference: Column<*>): String {
        val referee = reference.referee ?: throw RuntimeException("$reference does not reference anything")

        return when (driver.getClass().getName()) {
            "com.mysql.jdbc.Driver", "oracle.jdbc.driver.OracleDriver",
            "com.microsoft.sqlserver.jdbc.SQLServerDriver", "org.postgresql.Driver",
            "org.h2.Driver" -> {
                "ALTER TABLE ${identity(reference.table)} ADD FOREIGN KEY (${identity(reference)}) REFERENCES ${identity(referee.table)}(${identity(referee)})"
            }
            else -> throw UnsupportedOperationException("Unsupported driver: " + driver.getClass().getName())
        }
    }

    fun index (columns: Array<Column<*>>): String {
        if (columns.size == 0) throw RuntimeException("No columns to create index from")

        val table = columns[0].table
        return when (driver.getClass().getName()) {
            "com.mysql.jdbc.Driver", "oracle.jdbc.driver.OracleDriver",
            "com.microsoft.sqlserver.jdbc.SQLServerDriver", "org.postgresql.Driver",
            "org.h2.Driver" -> {
                var alter = StringBuilder()
                alter.append("CREATE INDEX ON ${identity(table)} (")
                var isFirst = true
                for (c in columns) {
                    if (table != c.table) throw RuntimeException("Columns from different tables cannot make index")
                    if (!isFirst) {
                        alter.append(", ")
                    }
                    isFirst = false
                    alter.append(identity(c))
                }
                alter.append(")")
                alter.toString()
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

    fun <T> withLockedTables (vararg tables: Table, statement : () -> T) : T {
/* Locks are not supported in H2
        if (tables.size > 0) {
            val ddl = StringBuilder("LOCK TABLES ${(tables map { identity(it) }).makeString(", ", "", "")}")
            println("SQL: " + ddl.toString())
            connection.createStatement()?.executeUpdate(ddl.toString())
        }
*/

        try {
            return statement()
        }
        finally {
/* Locks are not supported in H2
            if (tables.size > 0) {
                val ddl = "UNLOCK TABLES"
                println("SQL: " + ddl.toString())
                connection.createStatement()?.executeUpdate(ddl.toString())
            }
*/
        }
    }

    class object {
        val threadLocal = ThreadLocal<Session>()

        fun hasSession(): Boolean = threadLocal.get() != null

        fun get(): Session {
            return threadLocal.get() ?: throw RuntimeException("No session in context. Use transaction?")
        }
    }
}
