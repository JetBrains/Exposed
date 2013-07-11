package demo

import com.sun.tools.javac.util.Name.Table
import java.util.Properties
import java.sql.DriverManager
import java.sql.Connection
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet

class Database(val url: String, val driver: String) {
    {
        Class.forName(driver)
    }

    fun withSession(statement: Session.() -> Unit) {
        val connection = DriverManager.getConnection(url)
        connection.setAutoCommit(false)
        Session(connection).statement()
        connection.commit()
        connection.close()
    }
}

open class Session (val connection: Connection) {
}

open class Table(name: String = "") {
    val tableName = if (name.length() > 0) name else this.javaClass.getSimpleName()

    val tableColumns: List<Column<*>> = ArrayList<Column<*>>()
    val primaryKeys: List<Column<*>> = ArrayList<Column<*>>()
    val foreignKeys: List<ForeignKey> = ArrayList<ForeignKey>()

    fun primaryKey(name: String): Column<Int> {
        return column<Int>(name, ColumnType.PRIMARY_KEY)
    }

    fun columnInt(name: String): Column<Int> {
        return column<Int>(name, ColumnType.INT)
    }

    fun columnNullableInt(name: String): Column<Int?> {
        return column<Int?>(name, ColumnType.INT, true)
    }

    fun columnString(name: String): Column<String> {
        return column<String>(name, ColumnType.STRING)
    }

    private fun <T> column(name: String, columnType: ColumnType, nullable: Boolean = false): Column<T> {
        val column = Column<T>(this, name, columnType, nullable)
        if (columnType == ColumnType.PRIMARY_KEY) {
            (primaryKeys as ArrayList<Column<*>>).add(column)
        }
        (tableColumns as ArrayList<Column<*>>).add(column)
        return column
    }

    fun foreignKey(column: Column<*>, table: Table): ForeignKey {
        val foreignKey = ForeignKey(this, column, table)
        (foreignKeys as ArrayList<ForeignKey>).add(foreignKey)
        return foreignKey
    }
}

class ForeignKey(val table:Table, val column:Column<*>, val referencedTable:Table) {
}

open class Op : Expression {
    fun and(op: Op): Op {
        return AndOp(this, op)
    }

    fun or(op: Op): Op {
        return OrOp(this, op)
    }
}

trait Expression {

}

class IsNullOp(val column: Column<*>): Op() {
    fun toString():String {
        return "${column} IS NULL"
    }
}

class LiteralOp(val value: Any): Op() {
    fun toString():String {
        return if (value is String) "'" + value + "'" else value.toString()
    }
}

class EqualsOp(val expr1: Expression, val expr2: Expression): Op() {
    fun toString():String {
        val sb = StringBuilder()
        if (expr1 is OrOp) {
            sb.append("(").append(expr1).append(")")
        } else {
            sb.append(expr1)
        }
        sb.append(" = ")
        if (expr2 is OrOp) {
            sb.append("(").append(expr2).append(")")
        } else {
            sb.append(expr2)
        }
        return sb.toString()
    }
}

class AndOp(val expr1: Expression, val expr2: Expression): Op() {
    fun toString():String {
        val sb = StringBuilder()
        if (expr1 is OrOp) {
            sb.append("(").append(expr1).append(")")
        } else {
            sb.append(expr1)
        }
        sb.append(" and ")
        if (expr2 is OrOp) {
            sb.append("(").append(expr2).append(")")
        } else {
            sb.append(expr2)
        }
        return sb.toString()
    }
}

class OrOp(val expr1: Expression, val expr2: Expression): Op() {
    fun toString():String {
        return expr1.toString() + " or " + expr2.toString()
    }
}

enum class ColumnType {
    PRIMARY_KEY
    INT
    STRING
}

class Column<T>(val table: Table, val name: String, val columnType: ColumnType, val nullable: Boolean) : Expression {
    fun equals(other: Expression): Op {
        return EqualsOp(this, other)
    }

    fun equals(other: Any): Op {
        return EqualsOp(this, LiteralOp(other))
    }

    fun isNull(): Op {
        return IsNullOp(this)
    }

    fun toString(): String {
        return table.tableName + "." + name;
    }

    fun invoke(value: T): Pair<Column<T>, T> {
        return Pair<Column<T>, T>(this, value)
    }
}

fun Session.insert(vararg columns: Pair<Column<*>, *>) {
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

fun <A> Session.select(a: Column<A>): Query<A> {
    return Query(connection, array(a))
}

fun <A, B> Session.select(a: Column<A>, b: Column<B>): Query<Pair<A, B>> {
    return Query(connection, array(a, b))
}

fun <A, B, C> Session.select(a: Column<A>, b: Column<B>, c: Column<C>): Query<Triple<A, B, C>> {
    return Query(connection, array(a, b, c))
}

/*
fun <T> Session.select(vararg columns: Column<*>): Query<T> {
    return Query(connection, columns)
}
*/

open class Query<T>(val connection: Connection, val columns: Array<Column<*>>) {
    var op: Op? = null;
    var joinedTables = HashSet<Table>();
    var selectedColumns = HashSet<Column<*>>();
    val finalColumns = ArrayList<Column<*>>()
    var joins = HashSet<ForeignKey>();

    fun join (vararg foreignKeys: ForeignKey):Query<T> {
        for (foreignKey in foreignKeys) {
            joins.add(foreignKey)
            joinedTables.add(foreignKey.referencedTable)
        }
        return this
    }

    fun where(op: Op): Query<T> {
        this.op = op
        return this
    }

    fun groupBy(vararg b: Column<*>): Query<T> {
        return this
    }

    fun forEach(statement: (row: T) -> Unit) {
        val tables: MutableSet<Table> = HashSet<Table>()
        val sql = StringBuilder("SELECT ")
        if (columns.size > 0) {
            var c = 0;
            for (column in columns) {
                selectedColumns.add(column)
                finalColumns.add(column)
                if (!joinedTables.contains(column.table)) {
                    tables.add(column.table)
                }
                sql.append(column.table.tableName).append(".").append(column.name)
                c++
                if (c < columns.size) {
                    sql.append(", ")
                }
            }
        }
        if (joins.size > 0) {
            for (foreignKey in joins) {
                if (!selectedColumns.contains(foreignKey.column)) {
                    finalColumns.add(foreignKey.column)
                    sql.append(", ").append(foreignKey.column.table.tableName).append(".").append(foreignKey.column.name)
                }
            }
        }
        sql.append(" FROM ")
        var c= 0;
        for (table in tables) {
            sql.append(table.tableName)
            c++
            if (c < tables.size) {
                sql.append(", ")
            }
        }
        if (joins.size > 0) {
            for (foreignKey in joins) {
                sql.append(" LEFT JOIN ").append(foreignKey.referencedTable.tableName).append(" ON ").
                        append(foreignKey.referencedTable.primaryKeys[0]).append(" = ").append(foreignKey.column);
            }
        }
        if (op != null) {
            sql.append(" WHERE " + op.toString())
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
            if (columns.size == 1) {
                statement(values[columns[0]] as T)
            } else if (columns.size == 2) {
                statement(Pair(values[columns[0]], values[columns[1]]) as T)
            } else if (columns.size == 3) {
                statement(Triple(values[columns[0]], values[columns[1]], values[columns[2]]) as T)
            }
        }
    }
}

/*
open class Row<T>(val values: Map<Column<*>, *>) {
    fun has(foreignKey: ForeignKey): Boolean {
        return values.get(foreignKey.column) != null
    }

    fun <T> get(column: Column<T>): T {
        return values.get(column) as T
    }

    fun has(column: Column<*>): Boolean {
        return values.get(column) != null
    }
}
*/

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
                ddl.append(");")
            }
            if (table.foreignKeys.size > 0) {
                for (foreignKey in table.foreignKeys) {
                    ddl.append(" ALTER TABLE ${table.tableName} ADD FOREIGN KEY (").append(foreignKey.column.name).
                            append(") REFERENCES ").append(foreignKey.column.table.tableName).append("(").
                            append(foreignKey.column.table.primaryKeys[0].name).append(");")
                }
            }
            println("SQL: " + ddl.toString())
            connection.createStatement()?.executeUpdate(ddl.toString())
        }
    }
}