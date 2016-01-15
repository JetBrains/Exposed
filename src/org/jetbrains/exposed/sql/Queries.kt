package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.*
import org.jetbrains.exposed.sql.vendors.*
import java.util.*

inline fun FieldSet.select(where: SqlExpressionBuilder.()->Op<Boolean>) : Query {
    return select(SqlExpressionBuilder.where())
}

fun FieldSet.select(where: Op<Boolean>) : Query {
    return Query(Transaction.current(), this, where)
}

fun FieldSet.selectAll() : Query {
    return Query(Transaction.current(), this, null)
}

inline fun Table.deleteWhere(op: SqlExpressionBuilder.()->Op<Boolean>) {
    DeleteStatement.where(Transaction.current(), this@deleteWhere, SqlExpressionBuilder.op())
}

inline fun Table.deleteIgnoreWhere(op: SqlExpressionBuilder.()->Op<Boolean>) {
    DeleteStatement.where(Transaction.current(), this@deleteIgnoreWhere, SqlExpressionBuilder.op(), true)
}

fun Table.deleteAll() {
    DeleteStatement.all(Transaction.current(), this@deleteAll)
}

fun <T:Table> T.insert(body: T.(InsertStatement)->Unit): InsertStatement = InsertStatement(this).apply {
    body(this)
    execute(Transaction.current())
}

fun <T:Table, E:Any> T.batchInsert(data: Iterable<E>, ignore: Boolean = false, body: BatchInsertStatement.(E)->Unit): List<Int> {
    if (data.count() == 0) return emptyList()
    BatchInsertStatement(this, ignore).let {
        for (element in data) {
            it.addBatch()
            it.body(element)
        }
        return it.execute(Transaction.current()).first!!
    }
}

fun <T:Table> T.insertIgnore(body: T.(UpdateBuilder<*>)->Unit): InsertStatement {
    val answer = InsertStatement(this, isIgnore = true)
    body(answer)
    answer.execute(Transaction.current())
    return answer
}

fun <T:Table> T.replace(body: T.(UpdateBuilder<*>)->Unit): ReplaceStatement {
    return ReplaceStatement(this).apply {
        body(this)
        execute(Transaction.current())
    }
}

fun <T:Table> T.insert (selectQuery: Query): Unit {
    InsertSelectStatement(this, selectQuery).execute(Transaction.current())
}

fun <T:Table> T.insertIgnore (selectQuery: Query): Unit {
    InsertSelectStatement(this, selectQuery, true).execute(Transaction.current())
}

fun <T:Table> T.update(where: SqlExpressionBuilder.()->Op<Boolean>, limit: Int? = null, body: T.(UpdateStatement)->Unit): Int {
    val query = UpdateStatement(this, limit, SqlExpressionBuilder.where())
    body(query)
    return query.execute(Transaction.current()).first!!
}

fun Join.update(where: (SqlExpressionBuilder.()->Op<Boolean>)? =  null, limit: Int? = null, body: (UpdateStatement)->Unit) : Int {
    val query = UpdateStatement(this, limit, where?.let { SqlExpressionBuilder.it() })
    body(query)
    return query.execute(Transaction.current()).first!!
}

fun Table.exists (): Boolean = currentDialect.tableExists(this)

/**
 * Log entity <-> database mapping problems and returns DDL Statements to fix them
 */
fun checkMappingConsistence(vararg tables: Table): List<String> {
    checkExcessiveIndices(*tables)
    return checkMissingIndices(*tables).map{ it.createStatement() }
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

        exposedLogger.info("SQL Queries to remove excessive keys:");
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
        exposedLogger.info("SQL Queries to remove excessive indices:");
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

    fun List<Index>.filterFKeys() = filterNot { it.tableName to it.columns.singleOrNull()?.orEmpty() in fKeyConstraints}

    val allExistingIndices = currentDialect.existingIndices(*tables)
    val missingIndices = HashSet<Index>()
    val notMappedIndices = HashMap<String, MutableSet<Index>>()
    val nameDiffers = HashSet<Index>()
    for (table in tables) {
        val existingTableIndices = allExistingIndices[table.tableName].orEmpty().filterFKeys()
        val mappedIndices = table.indices.map { Index.forColumns(*it.first, unique = it.second)}.filterFKeys()

        existingTableIndices.forEach { index ->
            mappedIndices.firstOrNull { it.onlyNameDiffer(index) }?.let {
                exposedLogger.trace("Index on table '${table.tableName}' differs only in name: in db ${index.indexName} -> in mapping ${it.indexName}")
                nameDiffers.add(index)
                nameDiffers.add(it)
            }
        }

        notMappedIndices.getOrPut(table.javaClass.simpleName, {hashSetOf()}).addAll(existingTableIndices.subtract(mappedIndices))

        missingIndices.addAll(mappedIndices.subtract(existingTableIndices))
    }

    val toCreate = missingIndices.subtract(nameDiffers)
    toCreate.log("Indices missed from database (will be created):")
    notMappedIndices.forEach { it.value.subtract(nameDiffers).log("Indices exist in database and not mapped in code on class '${it.key}':") }
    return toCreate.toList()
}
