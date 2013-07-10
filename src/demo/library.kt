package demo

import com.sun.tools.javac.util.Name.Table
import java.util.Properties
import java.sql.DriverManager
import java.sql.Connection
import java.util.ArrayList
import java.util.HashMap

class Database(val url: String, val driver: String) {
    {
        Class.forName(driver)
    }

    fun withSession(statement: (session: Session) -> Unit) {
        statement(Session(DriverManager.getConnection(url)))
    }
}

class Session (val connection: Connection){
}

open class Table(name: String = "") {
    val tableName = if (name.length() > 0) name else this.javaClass.getSimpleName()

    val tableColumns: List<Column<*>> = ArrayList<Column<*>>() //TODO

    fun <T> column(name: String, columnType: ColumnType): Column<T> {
        val column = Column<T>(this, name, columnType)
        (tableColumns as ArrayList<Column<*>>).add(column)
        return column
    }
}

fun <T> T.equals(c: Column<T>): Boolean {
    return true
}

class Op {
    fun and(op: Op): Op {
        return Op()
    }

    fun or(op: Op): Op {
        return Op()
    }
}

enum class ColumnType {
    INT
    STRING
}

class Column<T>(val table: Table, val name: String, val columnType: ColumnType) {
    fun equals(other: Any?): Op {
        return Op()
    }
}

fun Session.insert(vararg columns: Pair<Column<*>, Any>) {
    if (columns.size > 0) {
        val table = columns[0].component1().table
        var sql = StringBuilder("INSERT INTO ${table.tableName}")
        var c = 0
        sql.append(" (")
        for (column in columns) {
            sql.append(column.component1().name)
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
                ColumnType.INT -> sql.append(column.component2())
                ColumnType.STRING -> sql.append("'" + column.component2() + "'")
                else -> throw IllegalStateException()
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

fun Session.select(vararg columns: Column<*>): Query {
    return Query(connection, columns)
}

open class Query(val connection: Connection, val columns: Array<Column<*>>) {
    var op: Op? = null;

    fun where(op: Op): Query {
        this.op = op
        return this
    }

    fun groupBy(vararg b: Column<*>): Query {
        return this
    }

    fun forEach(statement: (row: Row) -> Unit) {
        val sql = StringBuilder("SELECT ")
        if (columns.size > 0) {
            var c = 0;
            for (column in columns) {
                sql.append(column.name)
                c++
                if (c < columns.size) {
                    sql.append(", ")
                }
            }
        }
        sql.append(" FROM " + columns[0].table.tableName)
        if (op != null) {

        }
        println("SQL: " + sql.toString())
        val rs = connection.createStatement()?.executeQuery(sql.toString())!!
        val values = HashMap<Column<*>, Any?>()
        while (rs.next()) {
            var c = 0;
            for (column in columns) {
                c++;
                values[column] = rs.getObject(c)
            }
            statement(Row(values))
        }
    }
}

class Row(val values: Map<Column<*>, *>) {
    fun <T> get(column: Column<T>): T {
        return values.get(column) as T
    }
}

fun Session.create(vararg tables: Table) {
    if (tables.size > 0) {
        for (table in tables) {
            var ddl = StringBuilder("CREATE TABLE ${table.tableName}")
            if (table.tableColumns.size > 0) {
                ddl.append(" (")
                var c = 0;
                for (column in table.tableColumns) {
                    ddl.append(column.name).append(" ")
                    when (column.columnType) {
                        ColumnType.INT -> ddl.append("INT")
                        ColumnType.STRING -> ddl.append("VARCHAR(50)")
                        else -> throw IllegalStateException()
                    }
                    c++
                    if (c < table.tableColumns.size) {
                        ddl.append(", ")
                    }
                }
            }
            ddl.append(")")
            println("SQL: " + ddl.toString())
            connection.createStatement()?.executeUpdate(ddl.toString())
        }
    }
}