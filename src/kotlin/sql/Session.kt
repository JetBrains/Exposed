package kotlin.sql

import java.sql.Connection
import org.h2.engine.Session
import java.sql.Driver
import java.util.regex.Pattern

open class Session (val connection: Connection, val driver: Driver) {
    val identityQuoteString = connection.getMetaData()!!.getIdentifierQuoteString()!!
    val extraNameCharacters = connection.getMetaData()!!.getExtraNameCharacters()!!
    val identifierPattern = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$")

    fun <A> select(a: Field<A>): Query<A> {
        return Query(this, array(a))
    }

    fun count(column: Column<*>): Count {
        return Count(column)
    }

    fun <A, B> select(a: Field<A>, b: Field<B>): Query<Pair<A, B>> {
        return Query(this, array(a, b))
    }

    fun <A, B, C> select(a: Field<A>, b: Field<B>, c: Field<C>): Query<Triple<A, B, C>> {
        return Query<Triple<A, B, C>>(this, array(a, b, c))
    }

    fun update(vararg pairs: Pair<Column<*>, *>): UpdateQuery {
        return UpdateQuery(this, pairs)
    }

    fun delete(table: Table): DeleteQuery {
        return DeleteQuery(this, table)
    }

    fun insert(vararg columns: Pair<Column<*>, *>) {
        if (columns.size > 0) {
            val table = columns[0].component1().table
            var sql = StringBuilder("INSERT INTO ${identity(table)}")
            var c = 0
            sql.append(" (")
            for (column in columns) {
                sql.append(identity(column.component1()))
                c++
                if (c < columns.size) {
                    sql.append(", ")
                }
            }
            sql.append(") ")
            c = 0
            sql.append("VALUES (")
            for (column in columns) {
                when (column.component1().columnType) {
                    ColumnType.STRING -> sql.append("'" + column.component2() + "'")
                    else -> sql.append(column.component2())
                }
                c++
                if (c < columns.size) {
                    sql.append(", ")
                }
            }
            sql.append(") ")
            println("SQL: " + sql.toString())
            connection.createStatement()?.executeUpdate(sql.toString())
        }
    }

    fun create(vararg tables: Table) {
        if (tables.size > 0) {
            for (table in tables) {
                var ddl = StringBuilder("CREATE TABLE ${identity(table)}")
                if (table.tableColumns.size > 0) {
                    ddl.append(" (")
                    var c = 0;
                    for (column in table.tableColumns) {
                        ddl.append(identity(column)).append(" ")
                        when (column.columnType) {
                            ColumnType.PRIMARY_KEY -> ddl.append("INT PRIMARY KEY")
                            ColumnType.INT -> ddl.append("INT")
                            ColumnType.STRING -> ddl.append("VARCHAR(50)")
                            else -> throw IllegalStateException()
                        }
                        ddl.append(" ")
                        if (column.nullable) {
                            ddl.append("NULL")
                        } else {
                            ddl.append("NOT NULL")
                        }
                        c++
                        if (c < table.tableColumns.size) {
                            ddl.append(", ")
                        }
                    }
                    ddl.append(")")
                }
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

    class object {
        val threadLocale = ThreadLocal<Session>()

        fun get(): Session {
            return threadLocale.get()!!
        }
    }
}
