package kotlin.sql

import java.sql.Connection
import java.util.HashSet
import java.util.ArrayList

open class Query<T>(val connection: Connection, val columns: Array<Column<*>>) {
    var op: Op? = null;
    var joinedTables = HashSet<Table>();
    var selectedColumns = HashSet<Column<*>>();
    var leftJoins = HashSet<ForeignKey>();
    var inverseJoins = HashSet<ForeignKey>();
    var groupedByColumns = ArrayList<Column<*>>();

    fun join (vararg foreignKeys: ForeignKey):Query<T> {
        for (foreignKey in foreignKeys) {
            leftJoins.add(foreignKey)
            joinedTables.add(foreignKey.referencedTable)
        }
        return this
    }

    fun join (vararg tables: Table):Query<T> {
        for (table in tables) {
            for (foreignKey in table.foreignKeys) {
                for (column in columns) {
                    if (foreignKey.table == column.table) {
                        inverseJoins.add(foreignKey)
                    }
                }
            }
            joinedTables.add(table)
        }
        return this
    }

    fun where(op: Op): Query<T> {
        this.op = op
        return this
    }

    fun or(op: Op): Query<T> {
        this.op = OrOp(this.op!!, op)
        return this
    }

    fun and(op: Op): Query<T> {
        this.op = AndOp(this.op!!, op)
        return this
    }

    fun groupBy(vararg columns: Column<*>): Query<T> {
        for (column in columns) {
            groupedByColumns.add(column)
        }
        return this
    }

    fun forEach(statement: (row: T) -> Unit) {
        val tables: MutableSet<Table> = HashSet<Table>()
        val sql = StringBuilder("SELECT ")
        if (columns.size > 0) {
            var c = 0;
            for (column in columns) {
                selectedColumns.add(column)
                if (!joinedTables.contains(column.table)) {
                    tables.add(column.table)
                }
                sql.append(column)
                c++
                if (c < columns.size) {
                    sql.append(", ")
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
        if (leftJoins.size > 0) {
            for (foreignKey in leftJoins) {
                sql.append(" LEFT JOIN ").append(foreignKey.referencedTable.tableName).append(" ON ").
                append(foreignKey.referencedTable.primaryKeys[0]).append(" = ").append(foreignKey.column);
            }
        }
        if (inverseJoins.size > 0) {
            for (foreignKey in inverseJoins) {
                sql.append(" LEFT JOIN ").append(foreignKey.table.tableName).append(" ON ").
                append(foreignKey.referencedTable.primaryKeys[0]).append(" = ").append(foreignKey.column);
            }
        }
        if (op != null) {
            sql.append(" WHERE " + op.toString())
        }
        if (groupedByColumns.size > 0) {
            sql.append(" GROUP BY ")
        }
        c= 0;
        for (column in groupedByColumns) {
            sql.append(column)
            c++
            if (c < groupedByColumns.size) {
                sql.append(", ")
            }
        }
        println("SQL: " + sql.toString())
        val rs = connection.createStatement()?.executeQuery(sql.toString())!!
        while (rs.next()) {
            if (columns.size == 1) {
                statement(rs.getObject(1) as T)
            } else if (columns.size == 2) {
                statement(Pair(rs.getObject(1), rs.getObject(2)) as T)
            } else if (columns.size == 3) {
                statement(Triple(rs.getObject(1), rs.getObject(2), rs.getObject(3)) as T)
            }
        }
    }
}
