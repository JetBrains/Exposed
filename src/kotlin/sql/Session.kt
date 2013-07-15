package kotlin.sql

import java.sql.Connection

open class Session (val connection: Connection) {
    fun <A> select(a: Column<A>): Query<A> {
        return Query(connection, array(a))
    }

    fun <A, B> select(a: Column<A>, b: Column<B>): Query<Pair<A, B>> {
        return Query(connection, array(a, b))
    }

    fun <A, B, C> select(a: Column<A>, b: Column<B>, c: Column<C>): Query<Triple<A, B, C>> {
        return Query<Triple<A, B, C>>(connection, array(a, b, c))
    }

    fun update(vararg pairs: Pair<Column<*>, *>): UpdateQuery {
        return UpdateQuery(connection, pairs)
    }

    fun delete(table: Table): DeleteQuery {
        return DeleteQuery(connection, table)
    }

    fun insert(vararg columns: Pair<Column<*>, *>) {
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

    fun create(vararg tables: Table) {
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

    fun drop(vararg tables: Table) {
        if (tables.size > 0) {
            for (table in tables) {
                val ddl = StringBuilder("DROP TABLE ${table.tableName}")
                println("SQL: " + ddl.toString())
                connection.createStatement()?.executeUpdate(ddl.toString())
            }
        }
    }
}