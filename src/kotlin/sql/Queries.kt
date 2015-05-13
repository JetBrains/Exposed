package kotlin.sql

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.Delegates

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

inline fun Table.deleteIgnoreWhere(op: SqlExpressionBuilder.()->Op<Boolean>) {
    DeleteQuery.where(Session.get(), this@deleteIgnoreWhere, SqlExpressionBuilder.op(), true)
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

fun allTablesNames(): List<String> {
    val result = ArrayList<String>()
    val resultSet = Session.get().connection.getMetaData().getTables(null, null, null, arrayOf("TABLE"))

    while (resultSet.next()) {
        result.add(resultSet.getString("TABLE_NAME"))
    }
    return result
}

fun Table.exists (): Boolean {
    val tableName = this.tableName
    val resultSet = Session.get().connection.createStatement().executeQuery("show tables")

    while (resultSet.next()) {
        val existingTableName = resultSet.getString(1)
        if (existingTableName?.equalsIgnoreCase(tableName) ?: false) {
            return true
        }
    }

    return false
}

fun Table.matchesDefinition(): Boolean {
    val rs = Session.get().connection.createStatement().executeQuery("show columns from $tableName")

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

    val tables = HashMap<String, List<Pair<String, Boolean>>>()

    val rs = Session.get().connection.getMetaData().getColumns(getDatabase(), null, null, null)

    while (rs.next()) {
        val tableName = rs.getString("TABLE_NAME")!!
        val columnName = rs.getString("COLUMN_NAME")!!
        val nullable = rs.getBoolean("NULLABLE")
        tables[tableName] = (tables[tableName]?.plus(listOf(columnName to nullable)) ?: listOf(columnName to nullable))
    }
    return tables
}


/**
 * returns map of constraint for a table name/column name pair
 */

val columnConstraintsCache = ConcurrentHashMap<Table, List<ForeignKeyConstraint>>()

fun columnConstraints(vararg tables: Table): Map<Pair<String, String>, List<ForeignKeyConstraint>> {

    val constraints = HashMap<Pair<String, String>, MutableList<ForeignKeyConstraint>>()

    for (table in tables) {
        columnConstraintsCache.getOrPut(table, {
            val rs = Session.get().connection.getMetaData().getExportedKeys(getDatabase(), null, table.tableName)
            val tableConstraint = arrayListOf<ForeignKeyConstraint> ()
            while (rs.next()) {
                val refereeTableName = rs.getString("FKTABLE_NAME")!!
                val refereeColumnName = rs.getString("FKCOLUMN_NAME")!!
                val constraintName = rs.getString("FK_NAME")!!
                val refTableName = rs.getString("PKTABLE_NAME")!!
                val refColumnName = rs.getString("PKCOLUMN_NAME")!!
                val constraintDeleteRule = ReferenceOption.resolveRefOptionFromJdbc(rs.getInt("DELETE_RULE"))
                tableConstraint.add(ForeignKeyConstraint(constraintName, refereeTableName, refereeColumnName, refTableName, refColumnName, constraintDeleteRule))
            }
            tableConstraint
        }).forEach { it ->
            constraints.getOrPut(it.refereeTable to it.refereeColumn, {arrayListOf()}).add(it)
        }

    }

    return constraints
}

val existingIndicesCache = ConcurrentHashMap<String, List<Index>>()

fun existingIndices(vararg tables: Table): Map<String, List<Index>> {
    for(table in tables) {
        existingIndicesCache.getOrPut(table.tableName, {
            val rs = Session.get().connection.getMetaData().getIndexInfo(getDatabase(), null, table.tableName, false, false)

            val tmpIndices = hashMapOf<Pair<String, Boolean>, MutableList<String>>()

            while (rs.next()) {
                val indexName = rs.getString("INDEX_NAME")!!
                val column = rs.getString("COLUMN_NAME")!!
                val isUnique = !rs.getBoolean("NON_UNIQUE")
                tmpIndices.getOrPut(indexName to isUnique, { arrayListOf() }).add(column)
            }
            tmpIndices.filterNot { it.getKey().first == "PRIMARY" }.map { Index(it.getKey().first, table.tableName, it.getValue(), it.getKey().second)}
        }
       )
    }
    return HashMap(existingIndicesCache)
}

/**
 * Log entity <-> database mapping problems and returns DDL Statements to fix them
 */
fun checkMappingConsistence(vararg tables: Table): List<String> {
    checkExcessiveIndices(*tables)
    return checkMissingIndices(*tables).map{ it.createStatement() }
}

fun checkExcessiveIndices(vararg tables: Table) {

    val excessiveConstraints = columnConstraints(*tables).filter { it.getValue().size() > 1 }

    if (!excessiveConstraints.isEmpty()) {
        exposedLogger.warn("List of excessive foreign key constraints:")
        excessiveConstraints.forEach {
            val (pair, fk) = it
            val constraint = fk.first()
            exposedLogger.warn("\t\t\t'${pair.first}'.'${pair.second}' -> '${constraint.referencedTable}'.'${constraint.referencedColumn}':\t${fk.map{it.fkName}.join(", ")}")
        }
    }

    val excessiveIndices = existingIndices(*tables).flatMap { it.getValue() }.groupBy { Triple(it.tableName, it.unique, it.columns.join()) }.filter {it.getValue().size() > 1}
    if (!excessiveIndices.isEmpty()) {
        exposedLogger.warn("List of excessive indices:")
        excessiveIndices.forEach {
            val (triple, indices) = it
            exposedLogger.warn("\t\t\t'${triple.first}'.'${triple.third}' -> ${indices.map{it.indexName}.join(", ")}")
        }
    }
}

/** Returns list of indices missed in database **/
private fun checkMissingIndices(vararg tables: Table): List<Index> {
    fun Collection<Index>.log(mainMessage: String) {
        if (isNotEmpty()) {
            exposedLogger.warn(mainMessage)
            forEach {
                exposedLogger.warn("\t\t$it")
            }
        }
    }

    val fKeyCostraints = columnConstraints(*tables).keySet()

    fun List<Index>.filterFKeys() = filterNot { it.tableName to it.columns.singleOrNull()?.orEmpty() in fKeyCostraints}

    val allExistingIndices = existingIndices(*tables)
    val missingIndices = HashSet<Index>()
    val notMappedIndices = HashMap<String, MutableSet<Index>>()
    val nameDiffers = HashSet<Index>()
    for (table in tables) {
        val existingTableIndices = allExistingIndices[table.tableName].orEmpty().filterFKeys()
        val mappedIndices = table.indices.map { Index.forColumns(*it.first, unique = it.second)}.filterFKeys()

        existingTableIndices.forEach { index ->
            mappedIndices.firstOrNull { it.onlyNameDiffer(index) }?.let {
                exposedLogger.info("Index on table '${table.tableName}' differs only in name: in db ${index.indexName} -> in mapping ${it.indexName}")
                nameDiffers.add(index)
                nameDiffers.add(it)
            }
        }

        notMappedIndices.getOrPut(table.javaClass.getSimpleName(), {hashSetOf()}).addAll(existingTableIndices.subtract(mappedIndices))

        missingIndices.addAll(mappedIndices.subtract(existingTableIndices))
    }

    val toCreate = missingIndices.subtract(nameDiffers)
    toCreate.log("Indices missed from database (will be created):")
    notMappedIndices.forEach { it.getValue().subtract(nameDiffers).log("Indices exist in database and not mapped in code on class '${it.getKey()}':") }
    return toCreate.toList()
}

fun getDatabase(): String = with(Session.get().connection) { getSchema() ?: getCatalog() }