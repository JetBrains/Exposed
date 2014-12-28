package kotlin.sql

import java.util.HashMap

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

fun <T:Table> T.replace(body: T.(InsertQuery)->Unit): InsertQuery {
    val answer = InsertQuery(this, isReplace = true)
    body(answer)
    answer.execute(Session.get())
    return answer
}

fun <T:Table> T.insert (selectQuery: Query): Unit {
    val answer = InsertSelectQuery (this, selectQuery)
    answer.execute(Session.get())
}

fun <T:Table> T.replace(selectQuery: Query): Unit {
    val answer = InsertSelectQuery (this, selectQuery, isReplace = true)
    answer.execute(Session.get())
}

fun <T:Table> T.insertIgnore (selectQuery: Query): Unit {
    val answer = InsertSelectQuery (this, selectQuery, isIgnore = true)
    answer.execute(Session.get())
}

fun <T:Table> T.update(where: SqlExpressionBuilder.()->Op<Boolean>, limit: Int? = null, body: T.(UpdateQuery)->Unit): Int {
    val query = UpdateQuery({session -> session.identity(this)}, limit, SqlExpressionBuilder.where())
    body(query)
    return query.execute(Session.get())
}

fun Join.update(where: (SqlExpressionBuilder.()->Op<Boolean>)? =  null, limit: Int? = null, body: (UpdateQuery)->Unit) : Int {
    val query = UpdateQuery({session -> this.describe(session)}, limit, where?.let { SqlExpressionBuilder.it() })
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
 * returns list of pairs (column name + nullable) for every table
 */
fun tableColumns(): HashMap<String, List<Pair<String, Boolean>>> {
    if (Session.get().vendor != DatabaseVendor.MySql) {
        throw UnsupportedOperationException("Unsupported driver: " + Session.get().vendor)
    }

    val tables = HashMap<String, List<Pair<String, Boolean>>>()

    val rs = Session.get().connection.createStatement()?.executeQuery(
            "SELECT DISTINCT TABLE_NAME, COLUMN_NAME, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = '${getDatabase()}'")
    if (rs == null)
        return tables

    while (rs.next()) {
        val tableName = rs.getString(1)!!
        val columnName = rs.getString(2)!!
        val nullable = rs.getBoolean(3)
        tables[tableName] = (tables[tableName]?.plus(listOf(columnName to nullable)) ?: listOf(columnName to nullable))
    }
    return tables
}

class Constraint (var name: String, var referencedTable: String, var deleteRule: String)

/**
 * returns map of constraint for a table name/column name pair
 */
fun columnConstraints(): HashMap<Pair<String, String>, Constraint> {
    if (Session.get().vendor != DatabaseVendor.MySql) {
        throw UnsupportedOperationException("Unsupported driver: " + Session.get().vendor)
    }

    val constraints = HashMap<Pair<String, String>, Constraint>()

    val rs = Session.get().connection.createStatement()?.executeQuery(
            "SELECT\n" +
                    "  rc.TABLE_NAME,\n" +
                    "  ku.COLUMN_NAME,\n" +
                    "  rc.CONSTRAINT_NAME,\n" +
                    "  rc.REFERENCED_TABLE_NAME,\n" +
                    "  rc.DELETE_RULE\n" +
                    "FROM information_schema.REFERENTIAL_CONSTRAINTS rc\n" +
                    "  INNER JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE ku\n" +
                    "    ON ku.TABLE_SCHEMA = rc.CONSTRAINT_SCHEMA AND rc.CONSTRAINT_NAME = ku.CONSTRAINT_NAME\n" +
                    "WHERE ku.TABLE_SCHEMA = '${getDatabase()}'")
    if (rs == null)
        return constraints

    while (rs.next()) {
        val tableName = rs.getString("TABLE_NAME")!!
        val columnName = rs.getString("COLUMN_NAME")!!
        val constraintName = rs.getString("CONSTRAINT_NAME")!!
        val refTableName = rs.getString("REFERENCED_TABLE_NAME")!!
        val constraintDeleteRule = rs.getString("DELETE_RULE")!!
        constraints[Pair(tableName, columnName)] = Constraint(constraintName, refTableName, constraintDeleteRule)
    }

    return constraints
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
