package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.statements.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.util.*

inline fun FieldSet.select(where: SqlExpressionBuilder.()->Op<Boolean>) : Query = select(SqlExpressionBuilder.where())

fun FieldSet.select(where: Op<Boolean>) : Query = Query(TransactionManager.current(), this, where)

fun FieldSet.selectAll() : Query = Query(TransactionManager.current(), this, null)

fun Table.deleteWhere(op: SqlExpressionBuilder.()->Op<Boolean>) =
    DeleteStatement.where(TransactionManager.current(), this@deleteWhere, SqlExpressionBuilder.op())

fun Table.deleteIgnoreWhere(op: SqlExpressionBuilder.()->Op<Boolean>) =
    DeleteStatement.where(TransactionManager.current(), this@deleteIgnoreWhere, SqlExpressionBuilder.op(), true)

fun Table.deleteAll() =
    DeleteStatement.all(TransactionManager.current(), this@deleteAll)

fun <T:Table> T.insert(body: T.(InsertStatement<Number>)->Unit): InsertStatement <Number> = InsertStatement<Number>(this).apply {
    body(this)
    execute(TransactionManager.current())
}


fun <Key:Any, T: IdTable<Key>> T.insertAndGetId(body: T.(InsertStatement<EntityID<Key>>)->Unit) = InsertStatement<EntityID<Key>>(this).run {
    body(this)
    execute(TransactionManager.current())
    generatedKey
}

fun <T:Table, E:Any> T.batchInsert(data: Iterable<E>, ignore: Boolean = false, body: BatchInsertStatement.(E)->Unit): List<Map<Column<*>, Any>> {
    if (data.count() == 0) return emptyList()
    BatchInsertStatement(this, ignore).let {
        for (element in data) {
            it.addBatch()
            it.body(element)
        }
        return it.execute(TransactionManager.current())!!
    }
}

fun <T:Table> T.insertIgnore(body: T.(UpdateBuilder<*>)->Unit): InsertStatement<Long> = InsertStatement<Long>(this, isIgnore = true).apply {
    body(this)
    execute(TransactionManager.current())
}

fun <Key:Any, T: IdTable<Key>> T.insertIgnoreAndGetId(body: T.(UpdateBuilder<*>)->Unit) = InsertStatement<EntityID<Key>>(this, isIgnore = true).run {
    body(this)
    execute(TransactionManager.current())
    generatedKey
}

fun <T:Table> T.replace(body: T.(UpdateBuilder<*>)->Unit): ReplaceStatement<Long> = ReplaceStatement<Long>(this).apply {
    body(this)
    execute(TransactionManager.current())
}

fun <T:Table> T.insert(selectQuery: Query) =
    InsertSelectStatement(this, selectQuery).execute(TransactionManager.current())


fun <T:Table> T.insertIgnore(selectQuery: Query) =
    InsertSelectStatement(this, selectQuery, true).execute(TransactionManager.current())


fun <T:Table> T.update(where: SqlExpressionBuilder.()->Op<Boolean>, limit: Int? = null, body: T.(UpdateStatement)->Unit): Int {
    val query = UpdateStatement(this, limit, SqlExpressionBuilder.where())
    body(query)
    return query.execute(TransactionManager.current())!!
}

fun Join.update(where: (SqlExpressionBuilder.()->Op<Boolean>)? =  null, limit: Int? = null, body: (UpdateStatement)->Unit) : Int {
    val query = UpdateStatement(this, limit, where?.let { SqlExpressionBuilder.it() })
    body(query)
    return query.execute(TransactionManager.current())!!
}

fun Table.exists (): Boolean = currentDialect.tableExists(this)

/**
 * Log entity <-> database mapping problems and returns DDL Statements to fix them
 */
fun checkMappingConsistence(vararg tables: Table): List<String> {
    checkExcessiveIndices(*tables)
    return checkMissingIndices(*tables).flatMap { it.createStatement() }
}

fun checkExcessiveIndices(vararg tables: Table) {

    val excessiveConstraints = currentDialect.columnConstraints(*tables).filter { it.value.size > 1 }

    if (!excessiveConstraints.isEmpty()) {
        exposedLogger.warn("List of excessive foreign key constraints:")
        excessiveConstraints.forEach {
            val (pair, fk) = it
            val constraint = fk.first()
            exposedLogger.warn("\t\t\t'${pair.first}'.'${pair.second}' -> '${constraint.referencedTable}'.'${constraint.referencedColumn}':\t${fk.map{it.fkName}.joinToString(", ")}")
        }

        exposedLogger.info("SQL Queries to remove excessive keys:")
        excessiveConstraints.forEach {
            it.value.take(it.value.size - 1).forEach {
                exposedLogger.info("\t\t\t${it.dropStatement()};")
            }
        }
    }

    val excessiveIndices = currentDialect.existingIndices(*tables).flatMap { it.value }.groupBy { Triple(it.tableName, it.unique, it.columns.joinToString()) }.filter { it.value.size > 1}
    if (!excessiveIndices.isEmpty()) {
        exposedLogger.warn("List of excessive indices:")
        excessiveIndices.forEach {
            val (triple, indices) = it
            exposedLogger.warn("\t\t\t'${triple.first}'.'${triple.third}' -> ${indices.map{it.indexName}.joinToString(", ")}")
        }
        exposedLogger.info("SQL Queries to remove excessive indices:")
        excessiveIndices.forEach {
            it.value.take(it.value.size - 1).forEach {
                exposedLogger.info("\t\t\t${it.dropStatement()};")
            }
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

    val fKeyConstraints = currentDialect.columnConstraints(*tables).keys

    fun List<Index>.filterFKeys() = filterNot { it.tableName to it.columns.singleOrNull().orEmpty() in fKeyConstraints}

    val missingIndices = HashSet<Index>()
    val notMappedIndices = HashMap<String, MutableSet<Index>>()
    val nameDiffers = HashSet<Index>()
    for (table in tables) {
        val existingTableIndices = currentDialect.existingIndices(table)[table].orEmpty().filterFKeys()
        val mappedIndices = table.indices.map { Index.forColumns(*it.first, unique = it.second)}.filterFKeys()

        existingTableIndices.forEach { index ->
            mappedIndices.firstOrNull { it.onlyNameDiffer(index) }?.let {
                exposedLogger.trace("Index on table '${table.tableName}' differs only in name: in db ${index.indexName} -> in mapping ${it.indexName}")
                nameDiffers.add(index)
                nameDiffers.add(it)
            }
        }

        notMappedIndices.getOrPut(table.nameInDatabaseCase(), {hashSetOf()}).addAll(existingTableIndices.subtract(mappedIndices))

        missingIndices.addAll(mappedIndices.subtract(existingTableIndices))
    }

    val toCreate = missingIndices.subtract(nameDiffers)
    toCreate.log("Indices missed from database (will be created):")
    notMappedIndices.forEach { e -> e.value.subtract(nameDiffers).log("Indices exist in database and not mapped in code on class '${e.key}':") }
    return toCreate.toList()
}
