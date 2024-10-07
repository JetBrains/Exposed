package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect

/** Represents all the DSL methods available when building SQL statements. */
@Suppress("TooManyFunctions")
interface IStatementBuilder {
    fun <T : Table> deleteWhere(
        table: T,
        limit: Int?,
        op: T.(ISqlExpressionBuilder) -> Op<Boolean>
    ): DeleteStatement {
        return DeleteStatement(table, table.op(SqlExpressionBuilder), false, limit, emptyList())
    }

    fun <T : Table> deleteIgnoreWhere(
        table: T,
        limit: Int?,
        op: T.(ISqlExpressionBuilder) -> Op<Boolean>
    ): DeleteStatement {
        return DeleteStatement(table, table.op(SqlExpressionBuilder), true, limit, emptyList())
    }

    fun deleteAll(table: Table): DeleteStatement = DeleteStatement(table)

    fun <T : Table> deleteReturning(
        table: T,
        returning: List<Expression<*>>,
        where: (SqlExpressionBuilder.() -> Op<Boolean>)?
    ): ReturningStatement {
        val delete = DeleteStatement(table, where?.let { SqlExpressionBuilder.it() }, false, null)
        return ReturningStatement(table, returning, delete)
    }

    fun delete(
        join: Join,
        targetTable: Table,
        vararg targetTables: Table,
        ignore: Boolean,
        limit: Int?,
        where: (SqlExpressionBuilder.() -> Op<Boolean>)?
    ): DeleteStatement {
        val targets = listOf(targetTable) + targetTables
        return DeleteStatement(join, where?.let { SqlExpressionBuilder.it() }, ignore, limit, targets)
    }

    fun <T : Table> insert(table: T, body: T.(InsertStatement<Number>) -> Unit): InsertStatement<Number> {
        return InsertStatement<Number>(table).apply { table.body(this) }
    }

    fun <Key : Any, T : IdTable<Key>> insertAndGetId(
        table: T,
        body: T.(InsertStatement<EntityID<Key>>) -> Unit
    ): InsertStatement<EntityID<Key>> {
        return InsertStatement<EntityID<Key>>(table, false).apply { table.body(this) }
    }

    fun <T : Table> batchInsert(
        table: T,
        ignoreErrors: Boolean,
        shouldReturnGeneratedValues: Boolean
    ): BatchInsertStatement {
        return if (currentDialect is SQLServerDialect && table.autoIncColumn != null) {
            SQLServerBatchInsertStatement(table, ignoreErrors, shouldReturnGeneratedValues)
        } else {
            BatchInsertStatement(table, ignoreErrors, shouldReturnGeneratedValues)
        }
    }

    fun <T : Table> batchReplace(
        table: T,
        shouldReturnGeneratedValues: Boolean = true
    ): BatchReplaceStatement {
        return BatchReplaceStatement(table, shouldReturnGeneratedValues)
    }

    fun <T : Table> insertIgnore(table: T, body: T.(UpdateBuilder<*>) -> Unit): InsertStatement<Long> {
        return InsertStatement<Long>(table, true).apply { table.body(this) }
    }

    fun <Key : Any, T : IdTable<Key>> insertIgnoreAndGetId(
        table: T,
        body: T.(UpdateBuilder<*>) -> Unit
    ): InsertStatement<EntityID<Key>> {
        return InsertStatement<EntityID<Key>>(table, true).apply { table.body(this) }
    }

    fun <T : Table> replace(table: T, body: T.(UpdateBuilder<*>) -> Unit): ReplaceStatement<Long> {
        return ReplaceStatement<Long>(table).apply { table.body(this) }
    }

    fun replace(
        selectQuery: AbstractQuery<*>,
        columns: List<Column<*>>
    ): ReplaceSelectStatement {
        return ReplaceSelectStatement(columns, selectQuery)
    }

    fun insert(
        selectQuery: AbstractQuery<*>,
        columns: List<Column<*>>
    ): InsertSelectStatement {
        return InsertSelectStatement(columns, selectQuery, false)
    }

    fun insertIgnore(
        selectQuery: AbstractQuery<*>,
        columns: List<Column<*>>
    ): InsertSelectStatement {
        return InsertSelectStatement(columns, selectQuery, true)
    }

    fun <T : Table> insertReturning(
        table: T,
        returning: List<Expression<*>>,
        ignoreErrors: Boolean,
        body: T.(InsertStatement<Number>) -> Unit
    ): ReturningStatement {
        val insert = InsertStatement<Number>(table, ignoreErrors)
        table.body(insert)
        return ReturningStatement(table, returning, insert)
    }

    fun <T : Table> update(
        table: T,
        where: (SqlExpressionBuilder.() -> Op<Boolean>)?,
        limit: Int?,
        body: T.(UpdateStatement) -> Unit
    ): UpdateStatement {
        return UpdateStatement(table, limit, where?.let { SqlExpressionBuilder.it() }).apply { table.body(this) }
    }

    fun update(
        join: Join,
        where: (SqlExpressionBuilder.() -> Op<Boolean>)?,
        limit: Int?,
        body: (UpdateStatement) -> Unit
    ): UpdateStatement {
        return UpdateStatement(join, limit, where?.let { SqlExpressionBuilder.it() }).apply(body)
    }

    fun <T : Table> updateReturning(
        table: T,
        returning: List<Expression<*>>,
        where: (SqlExpressionBuilder.() -> Op<Boolean>)?,
        body: T.(UpdateStatement) -> Unit
    ): ReturningStatement {
        val update = UpdateStatement(table, null, where?.let { SqlExpressionBuilder.it() })
        table.body(update)
        return ReturningStatement(table, returning, update)
    }

    fun <T : Table> upsert(
        table: T,
        vararg keys: Column<*>,
        onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)?,
        onUpdateExclude: List<Column<*>>?,
        where: (SqlExpressionBuilder.() -> Op<Boolean>)?,
        body: T.(UpsertStatement<Long>) -> Unit
    ): UpsertStatement<Long> {
        return UpsertStatement<Long>(table, keys = keys, onUpdateExclude = onUpdateExclude, where = where?.let { SqlExpressionBuilder.it() }).apply {
            onUpdate?.let { storeUpdateValues(it) }
            table.body(this)
        }
    }

    fun <T : Table> upsertReturning(
        table: T,
        vararg keys: Column<*>,
        returning: List<Expression<*>>,
        onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)?,
        onUpdateExclude: List<Column<*>>?,
        where: (SqlExpressionBuilder.() -> Op<Boolean>)?,
        body: T.(UpsertStatement<Long>) -> Unit
    ): ReturningStatement {
        val upsert = UpsertStatement<Long>(table, keys = keys, onUpdateExclude, where?.let { SqlExpressionBuilder.it() })
        onUpdate?.let { upsert.storeUpdateValues(it) }
        table.body(upsert)
        return ReturningStatement(table, returning, upsert)
    }

    @Suppress("LongParameterList")
    fun <T : Table, E> batchUpsert(
        table: T,
        data: Iterator<E>,
        onUpdateList: List<Pair<Column<*>, Any?>>?,
        onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)?,
        onUpdateExclude: List<Column<*>>?,
        where: (SqlExpressionBuilder.() -> Op<Boolean>)?,
        shouldReturnGeneratedValues: Boolean,
        vararg keys: Column<*>,
        body: BatchUpsertStatement.(E) -> Unit
    ): BatchUpsertStatement {
        return BatchUpsertStatement(
            table,
            keys = keys,
            onUpdateExclude = onUpdateExclude,
            where = where?.let { SqlExpressionBuilder.it() },
            shouldReturnGeneratedValues = shouldReturnGeneratedValues
        ).apply {
            onUpdate?.let { storeUpdateValues(it) }
                ?: onUpdateList?.let { updateValues.putAll(it) }
        }
    }

    fun <D : Table, S : Table> mergeFrom(
        target: D,
        source: S,
        on: (SqlExpressionBuilder.() -> Op<Boolean>)?,
        body: MergeTableStatement.() -> Unit
    ): MergeTableStatement {
        return MergeTableStatement(target, source, on = on?.invoke(SqlExpressionBuilder)).apply(body)
    }

    fun <T : Table> mergeFrom(
        table: T,
        selectQuery: QueryAlias,
        on: SqlExpressionBuilder.() -> Op<Boolean>,
        body: MergeSelectStatement.() -> Unit
    ): MergeSelectStatement {
        return MergeSelectStatement(table, selectQuery, SqlExpressionBuilder.on()).apply(body)
    }
}

/** Builder object for creating SQL statements. */
object StatementBuilder : IStatementBuilder {
    operator fun <S> invoke(body: StatementBuilder.() -> S): S = body(this)
}
