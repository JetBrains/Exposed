package kotlin.sql

fun FieldSet.select(where: SqlExpressionBuilder.()->Op<Boolean>) : Query {
    return select(SqlExpressionBuilder.where())
}

fun FieldSet.select(where: Op<Boolean>) : Query {
    return Query(Session.get(), this, where)
}

fun FieldSet.selectAll() : Query {
    return Query(Session.get(), this, null)
}

fun Table.deleteWhere(op: SqlExpressionBuilder.()->Op<Boolean>) {
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
        val column = columns.find {it.name == fieldName}
        if (column == null)
            return false

        --nColumns
    }

    return nColumns == 0
}
