package kotlin.sql

import java.sql.Connection
import java.util.HashSet
import java.util.ArrayList

open class Query<T>(val session: Session, val fields: Array<Field<*>>) {
    var op: Op? = null;
    var selectedTables = ArrayList<Table>();
    var joinedTables = ArrayList<Table>();
    var selectedColumns = HashSet<Column<*>>();
    //var leftJoins = HashSet<ForeignKey>();
    //var inverseJoins = HashSet<ForeignKey>();
    var groupedByColumns = ArrayList<Column<*>>();

    /*fun join (vararg foreignKeys: ForeignKey): Query<T> {
        for (foreignKey in foreignKeys) {
            //leftJoins.add(foreignKey)
            joinedTables.add(foreignKey.referencedTable)
        }
        return this
    }*/

    fun from (vararg tables: Table) : Query<T> {
        for (table in tables) {
            selectedTables.add(table)
        }
        return this
    }

    fun join (vararg tables: Table): Query<T> {
        for (table in tables) {
            /*for (foreignKey in table.foreignKeys) {
                for (field in fields) {
                    if (field is Column<*> && foreignKey.table == field.table) {
                        inverseJoins.add(foreignKey)
                    } else if (field is Function<*>) {
                        for (column in field.columns)
                            if (foreignKey.table == column.table) {
                                inverseJoins.add(foreignKey)
                            }
                    }
                }
            }*/
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

    fun single(): T {
        var answer: T? = null
        var found = false;

        forEach {
            if (found) {
                throw RuntimeException("Duplicate entries")
            }
            found = true
            answer = it
        }

        return answer ?: throw RuntimeException("No entries found")
    }

    fun forEach(statement: (row: T) -> Unit) {
        val tables: MutableSet<Table> = HashSet<Table>()
        val sql = StringBuilder("SELECT ")
        if (fields.size > 0) {
            var c = 0;
            for (field in fields) {
                if (field is Column<*>) {
                    selectedColumns.add(field)
                    if (!joinedTables.contains(field.table)) {
                        tables.add(field.table)
                    }
                } else if (field is Function<*>) {
                    for (column in field.columns) {
                        selectedColumns.add(column)
                        if (!joinedTables.contains(column.table)) {
                            tables.add(column.table)
                        }
                    }
                }
                sql.append(field.toSQL())
                c++
                if (c < fields.size) {
                    sql.append(", ")
                }
            }
        }
        sql.append(" FROM ")
        var c = 0;
        if (selectedTables.isEmpty()) {
            for (table in tables) {
                sql.append(session.identity(table))
                c++
                if (c < tables.size) {
                    sql.append(", ")
                }
            }
        } else {
            for (table in selectedTables) {
                sql.append(session.identity(table))
                c++
                if (c < tables.size) {
                    sql.append(", ")
                }
            }
        }
        if (!joinedTables.isEmpty()) {
            for (table in joinedTables) {
                if (table.primaryKeys.size == 1) {
                    val primaryKey = table.primaryKeys.get(0)
                    for (column in selectedColumns) {
                        if (column.referee == primaryKey) {
                            sql.append(" LEFT JOIN ").append(session.identity(column.referee!!.table)).append(" ON ").
                            append(session.fullIdentity(column)).append(" = ").append(session.fullIdentity(primaryKey));
                        }
                    }
                }
                for (selectedTable in selectedTables) {
                    if (selectedTable.primaryKeys.size == 1) {
                        val primaryKey = selectedTable.primaryKeys.get(0)
                        for (column in table.tableColumns) {
                            if (column.referee == primaryKey) {
                                sql.append(" LEFT JOIN ").append(session.identity(table)).append(" ON ").
                                append(session.fullIdentity(column)).append(" = ").append(session.fullIdentity(primaryKey));
                            }
                        }
                    }
                }
            }
        }
        /*if (leftJoins.size > 0) {
            for (foreignKey in leftJoins) {
                sql.append(" LEFT JOIN ").append(session.identity(foreignKey.referencedTable)).append(" ON ").
                append(session.fullIdentity(foreignKey.referencedTable.primaryKeys[0])).append(" = ").append(session.fullIdentity(foreignKey.column));
            }
        }
        if (inverseJoins.size > 0) {
            for (foreignKey in inverseJoins) {
                sql.append(" LEFT JOIN ").append(session.identity(foreignKey.table)).append(" ON ").
                append(session.fullIdentity(foreignKey.referencedTable.primaryKeys[0])).append(" = ").append(session.fullIdentity(foreignKey.column));
            }
        }*/
        if (op != null) {
            sql.append(" WHERE ").append(op!!.toSQL())
        }
        if (groupedByColumns.size > 0) {
            sql.append(" GROUP BY ")
        }
        c = 0;
        for (column in groupedByColumns) {
            sql.append(session.fullIdentity(column))
            c++
            if (c < groupedByColumns.size) {
                sql.append(", ")
            }
        }
        println("SQL: " + sql.toString())
        val rs = session.connection.createStatement()?.executeQuery(sql.toString())!!
        while (rs.next()) {
            if (fields.size == 1) {
                statement(rs.getObject(1) as T)
            } else if (fields.size == 2) {
                statement(Pair(rs.getObject(1), rs.getObject(2)) as T)
            } else if (fields.size == 3) {
                statement(Triple(rs.getObject(1), rs.getObject(2), rs.getObject(3)) as T)
            }
        }
    }
}
