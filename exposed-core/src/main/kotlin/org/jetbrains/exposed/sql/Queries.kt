package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.statements.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.inProperCase
import java.util.*

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testSelect01
 */
inline fun FieldSet.select(where: SqlExpressionBuilder.()->Op<Boolean>) : Query = select(SqlExpressionBuilder.where())

fun FieldSet.select(where: Op<Boolean>) : Query = Query(this, where)

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testSelectDistinct
 */
fun FieldSet.selectAll() : Query = Query(this, null)

/**
 * That function will make multiple queries with limit equals to [batchSize]
 * and return result as a collection of [ResultRow] sub-collections.
 * [FieldSet] will be sorted by the first auto-increment column and then returned in batches.
 *
 * @param batchSize Size of a sub-collections to return
 * @param where Where condition to be applied
 */
fun FieldSet.selectBatched(
        batchSize: Int = 1000,
        where: SqlExpressionBuilder.() -> Op<Boolean>
): Iterable<Iterable<ResultRow>> {
    return selectBatched(batchSize, SqlExpressionBuilder.where())
}

/**
 * That function will make multiple queries with limit equals to [batchSize]
 * and return result as a collection of [ResultRow] sub-collections.
 * [FieldSet] will be sorted by the first auto-increment column and then returned in batches.
 *
 * @param batchSize Size of a sub-collections to return
 */
fun FieldSet.selectAllBatched(
        batchSize: Int = 1000
): Iterable<Iterable<ResultRow>> {
    return selectBatched(batchSize, Op.TRUE)
}

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testDelete01
 */
fun Table.deleteWhere(limit: Int? = null, offset: Int? = null, op: SqlExpressionBuilder.()->Op<Boolean>) =
    DeleteStatement.where(TransactionManager.current(), this@deleteWhere, SqlExpressionBuilder.op(), false, limit, offset)

fun Table.deleteIgnoreWhere(limit: Int? = null, offset: Int? = null, op: SqlExpressionBuilder.()->Op<Boolean>) =
    DeleteStatement.where(TransactionManager.current(), this@deleteIgnoreWhere, SqlExpressionBuilder.op(), true, limit, offset)

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testDelete01
 */
fun Table.deleteAll() =
    DeleteStatement.all(TransactionManager.current(), this@deleteAll)

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testInsert01
 */
fun <T:Table> T.insert(body: T.(InsertStatement<Number>)->Unit): InsertStatement<Number> = InsertStatement<Number>(this).apply {
    body(this)
    execute(TransactionManager.current())
}

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testGeneratedKey03
 */
fun <Key:Comparable<Key>, T: IdTable<Key>> T.insertAndGetId(body: T.(InsertStatement<EntityID<Key>>)->Unit) =
    InsertStatement<EntityID<Key>>(this, false).run {
        body(this)
        execute(TransactionManager.current())
        get(id)
    }

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testBatchInsert01
 */
fun <T:Table, E:Any> T.batchInsert(data: Iterable<E>, ignore: Boolean = false, body: BatchInsertStatement.(E)->Unit): List<ResultRow> {
    if (data.count() == 0) return emptyList()
    fun newBatchStatement() : BatchInsertStatement {
        return if (currentDialect is SQLServerDialect && this.autoIncColumn != null) {
            SQLServerBatchInsertStatement(this, ignore)
        } else {
            BatchInsertStatement(this, ignore)
        }
    }
    var statement = newBatchStatement()

    val result = ArrayList<ResultRow>()
    fun BatchInsertStatement.handleBatchException(removeLastData: Boolean = false, body: BatchInsertStatement.() -> Unit) {
        try {
            body()
            if(removeLastData)
                validateLastBatch()
        } catch (e: BatchDataInconsistentException) {
            if (this.data.size == 1) {
                throw e
            }
            val notTheFirstBatch = this.data.size > 1
            if (notTheFirstBatch) {
                if (removeLastData) {
                    removeLastBatch()
                }
                execute(TransactionManager.current())
                result += resultedValues.orEmpty()
            }
            statement = newBatchStatement()
            if (removeLastData && notTheFirstBatch) {
                statement.addBatch()
                statement.body()
                statement.validateLastBatch()
            }
        }
    }

    for (element in data) {
        statement.handleBatchException { addBatch() }
        statement.handleBatchException(true) { body(element) }
    }
    if (statement.arguments().isNotEmpty()) {
        statement.execute(TransactionManager.current())
        result += statement.resultedValues.orEmpty()
    }
    return result
}

fun <T:Table> T.insertIgnore(body: T.(UpdateBuilder<*>)->Unit): InsertStatement<Long> = InsertStatement<Long>(this, isIgnore = true).apply {
    body(this)
    execute(TransactionManager.current())
}

fun <Key:Comparable<Key>, T: IdTable<Key>> T.insertIgnoreAndGetId(body: T.(UpdateBuilder<*>)->Unit) = InsertStatement<EntityID<Key>>(this, isIgnore = true).run {
    body(this)
    execute(TransactionManager.current())
    getOrNull(id)
}

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testReplace01
 */
fun <T:Table> T.replace(body: T.(UpdateBuilder<*>)->Unit): ReplaceStatement<Long> = ReplaceStatement<Long>(this).apply {
    body(this)
    execute(TransactionManager.current())
}

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testInsertSelect01
 */
fun <T:Table> T.insert(selectQuery: Query, columns: List<Column<*>> = this.columns.filterNot { it.columnType.isAutoInc }) =
    InsertSelectStatement(columns, selectQuery).execute(TransactionManager.current())


fun <T:Table> T.insertIgnore(selectQuery: Query, columns: List<Column<*>> = this.columns.filterNot { it.columnType.isAutoInc }) =
    InsertSelectStatement(columns, selectQuery, true).execute(TransactionManager.current())


/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testUpdate01
 */
fun <T:Table> T.update(where: (SqlExpressionBuilder.()->Op<Boolean>)? = null, limit: Int? = null, body: T.(UpdateStatement)->Unit): Int {
    val query = UpdateStatement(this, limit, where?.let { SqlExpressionBuilder.it() })
    body(query)
    return query.execute(TransactionManager.current())!!
}

fun Join.update(where: (SqlExpressionBuilder.()->Op<Boolean>)? = null, limit: Int? = null, body: (UpdateStatement)->Unit) : Int {
    val query = UpdateStatement(this, limit, where?.let { SqlExpressionBuilder.it() })
    body(query)
    return query.execute(TransactionManager.current())!!
}

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DDLTests.tableExists02
 */
fun Table.exists(): Boolean = currentDialect.tableExists(this)

/**
 * Log Exposed table mappings <-> real database mapping problems and returns DDL Statements to fix them
 */
fun checkMappingConsistence(vararg tables: Table): List<String> {
    checkExcessiveIndices(*tables)
    return checkMissingIndices(*tables).flatMap { it.createStatement() }
}

fun checkExcessiveIndices(vararg tables: Table) {

    val excessiveConstraints = currentDialect.columnConstraints(*tables).filter { it.value.size > 1 }

    if (!excessiveConstraints.isEmpty()) {
        exposedLogger.warn("List of excessive foreign key constraints:")
        excessiveConstraints.forEach { (pair, fk) ->
            val constraint = fk.first()
            exposedLogger.warn("\t\t\t'${pair.first}'.'${pair.second}' -> '${constraint.fromTable}'.'${constraint.fromColumn}':\t${fk.joinToString(", ") {it.fkName}}")
        }

        exposedLogger.info("SQL Queries to remove excessive keys:")
        excessiveConstraints.forEach {
            it.value.take(it.value.size - 1).forEach {
                exposedLogger.info("\t\t\t${it.dropStatement()};")
            }
        }
    }

    val excessiveIndices = currentDialect.existingIndices(*tables).flatMap { it.value }.groupBy { Triple(it.table, it.unique, it.columns.joinToString { it.name }) }.filter { it.value.size > 1}
    if (excessiveIndices.isNotEmpty()) {
        exposedLogger.warn("List of excessive indices:")
        excessiveIndices.forEach { (triple, indices)->
            exposedLogger.warn("\t\t\t'${triple.first.tableName}'.'${triple.third}' -> ${indices.joinToString(", ") {it.indexName}}")
        }
        exposedLogger.info("SQL Queries to remove excessive indices:")
        excessiveIndices.forEach {
            it.value.take(it.value.size - 1).forEach {
                exposedLogger.info("\t\t\t${it.dropStatement()};")
            }
        }
    }
}

private fun FieldSet.selectBatched(
        batchSize: Int = 1000,
        whereOp: Op<Boolean>
): Iterable<Iterable<ResultRow>> {
    require(batchSize > 0) { "Batch size should be greater than 0" }

    val autoIncColumn = try {
        source.columns.first { it.columnType.isAutoInc }
    } catch (_: NoSuchElementException) {
        throw UnsupportedOperationException("Batched select only works on tables with an autoincrementing column")
    }

    return object : Iterable<Iterable<ResultRow>> {
        override fun iterator(): Iterator<Iterable<ResultRow>> {
            return iterator {
                var lastOffset = 0L
                while (true) {
                    val query =
                            select { whereOp and (autoIncColumn greater lastOffset) }
                                    .limit(batchSize)
                                    .orderBy(autoIncColumn, SortOrder.ASC)

                    // query.iterator() executes the query
                    val results = query.iterator().asSequence().toList()

                    if (results.isEmpty()) break

                    yield(results)

                    if (results.size < batchSize) break

                    lastOffset = toLong(results.last()[autoIncColumn]!!)
                }
            }
        }

        private fun toLong(autoIncVal: Any): Long = when (autoIncVal) {
            is EntityID<*> -> autoIncVal.value as Long
            is Int -> autoIncVal.toLong()
            else -> autoIncVal as Long
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

    val tr = TransactionManager.current()
    val isMysql = currentDialect is MysqlDialect
    val fKeyConstraints = currentDialect.columnConstraints(*tables).keys
    val existingIndices = currentDialect.existingIndices(*tables)
    fun List<Index>.filterFKeys() = if (isMysql)
        filterNot { (it.table.tableName.inProperCase() to it.columns.singleOrNull()?.let { c -> tr.identity(c) }) in fKeyConstraints }
    else
        this

    val missingIndices = HashSet<Index>()
    val notMappedIndices = HashMap<String, MutableSet<Index>>()
    val nameDiffers = HashSet<Index>()

    for (table in tables) {
        val existingTableIndices = existingIndices[table].orEmpty().filterFKeys()
        val mappedIndices = table.indices.filterFKeys()

        existingTableIndices.forEach { index ->
            mappedIndices.firstOrNull { it.onlyNameDiffer(index) }?.let {
                exposedLogger.trace("Index on table '${table.tableName}' differs only in name: in db ${index.indexName} -> in mapping ${it.indexName}")
                nameDiffers.add(index)
                nameDiffers.add(it)
            }
        }

        notMappedIndices.getOrPut(table.nameInDatabaseCase()) {hashSetOf()}.addAll(existingTableIndices.subtract(mappedIndices))

        missingIndices.addAll(mappedIndices.subtract(existingTableIndices))
    }

    val toCreate = missingIndices.subtract(nameDiffers)
    toCreate.log("Indices missed from database (will be created):")
    notMappedIndices.forEach { (name, indexes) -> indexes.subtract(nameDiffers).log("Indices exist in database and not mapped in code on class '$name':") }
    return toCreate.toList()
}
