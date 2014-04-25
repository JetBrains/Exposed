package kotlin.sql

import java.util.HashMap
import java.util.ArrayList

inline fun FieldSet.select(where: SqlExpressionBuilder.()->Op<Boolean>) : Query {
    return select(SqlExpressionBuilder.where())
}

fun FieldSet.select(where: Op<Boolean>) : Query {
    return Query(Session.get(), this, where)
}

fun FieldSet.selectAll() : Query {
    return Query(Session.get(), this, null)
}

inline fun Table.deleteWhere(op: SqlExpressionBuilder.()->Op<Boolean>) {
    DeleteQuery.where(Session.get(), this@deleteWhere, SqlExpressionBuilder.op())
}

fun Table.deleteAll() {
    DeleteQuery.all(Session.get(), this@deleteAll)
}

fun <T:Table> T.insert(body: T.(InsertQuery)->Unit): InsertQuery {
    val answer = InsertQuery(this)
    body(answer)
    answer.execute(Session.get())
    return answer
}

fun <T:Table> T.insertIgnore(body: T.(InsertQuery)->Unit): InsertQuery {
    val answer = InsertQuery(this, isIgnore = true)
    body(answer)
    answer.execute(Session.get())
    return answer
}

fun <T:Table> T.insert (selectQuery: Query): Unit {
    val answer = InsertSelectQuery (this, selectQuery)
    answer.execute(Session.get())
}

fun <T:Table> T.insertIgnore (selectQuery: Query): Unit {
    val answer = InsertSelectQuery (this, selectQuery, true)
    answer.execute(Session.get())
}

fun <T:Table> T.update(where: SqlExpressionBuilder.()->Op<Boolean>, limit: Int? = null, body: T.(UpdateQuery)->Unit): Int {
    val query = UpdateQuery(this, limit, SqlExpressionBuilder.where())
    body(query)
    return query.execute(Session.get())
}

fun Table.exists (): Boolean {
    val tableName = this.tableName
    val resultSet = Session.get().connection.createStatement()?.executeQuery("show tables")
    if (resultSet != null) {
        while (resultSet.next()) {
            val existingTableName = resultSet.getString(1)
            if (existingTableName?.equalsIgnoreCase(tableName) ?: false) {
                return true
            }
        }
    }

    return false
}

fun Table.matchesDefinition(): Boolean {
    val rs = Session.get().connection.createStatement()?.executeQuery("show columns from $tableName")
    if (rs == null)
        return false

    var nColumns = columns.size()
    while (rs.next()) {
        val fieldName = rs.getString(1)
        val column = columns.firstOrNull {it.name == fieldName}
        if (column == null)
            return false

        --nColumns
    }

    return nColumns == 0
}

/**
 * returns list of column names for every table
 */
fun tableColumns(): HashMap<String, List<String>> {
    if (Session.get().vendor != DatabaseVendor.MySql) {
        throw UnsupportedOperationException("Unsupported driver: " + Session.get().vendor)
    }

    val tables = HashMap<String, List<String>>()

    val rs = Session.get().connection.createStatement()?.executeQuery(
            "SELECT DISTINCT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = '${getDatabase()}'")
    if (rs == null)
        return tables

    while (rs.next()) {
        val tableName = rs.getString(1)!!
        val columnName = rs.getString(2)!!
        tables[tableName] = (tables[tableName]?.plus(listOf(columnName)) ?: listOf(columnName))
    }

    return tables
}

fun getDatabase(): String {
    return when (Session.get().vendor) {
        DatabaseVendor.MySql -> {
            val rs = Session.get().connection.createStatement()?.executeQuery("SELECT DATABASE()")
            if (rs == null || !rs.next()) {
                ""
            } else {
                rs.getString(1)!!
            }
        }
        else -> throw UnsupportedOperationException("Unsupported driver: " + Session.get().vendor)
    }
}
