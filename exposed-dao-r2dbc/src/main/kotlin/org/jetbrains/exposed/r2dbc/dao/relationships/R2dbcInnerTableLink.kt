package org.jetbrains.exposed.r2dbc.dao.relationships

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.r2dbc.dao.executeAsPartOfEntityLifecycle
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.r2dbc.SizedIterable
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.emptySized
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import kotlin.reflect.KProperty

@Suppress("UNCHECKED_CAST")
class R2dbcInnerTableLink<SID : Any, Source : R2dbcEntity<SID>, ID : Any, Target : R2dbcEntity<ID>>(
    val table: Table,
    sourceTable: IdTable<SID>,
    val target: R2dbcEntityClass<ID, Target>,
    _sourceColumn: Column<EntityID<SID>>? = null,
    _targetColumn: Column<EntityID<ID>>? = null,
) {
    private val orderByExpressions: MutableList<Pair<Expression<*>, SortOrder>> = mutableListOf()

    init {
        _targetColumn?.let {
            requireNotNull(_sourceColumn) { "Both source and target columns should be specified" }
            require(_targetColumn.referee?.table == target.table) {
                "Column $_targetColumn point to wrong table, expected ${target.table.tableName}"
            }
            require(_targetColumn.table == _sourceColumn.table) {
                "Both source and target columns should be from the same table"
            }
        }
        _sourceColumn?.let {
            requireNotNull(_targetColumn) { "Both source and target columns should be specified" }
            require(_sourceColumn.referee?.table == sourceTable) {
                "Column $_sourceColumn point to wrong table, expected ${sourceTable.tableName}"
            }
        }
    }

    val sourceColumn: Column<EntityID<SID>> = _sourceColumn
        ?: table.columns.singleOrNull { it.referee == sourceTable.id } as? Column<EntityID<SID>>
        ?: error("Table does not reference source")

    val targetColumn: Column<EntityID<ID>> = _targetColumn
        ?: table.columns.singleOrNull { it.referee == target.table.id } as? Column<EntityID<ID>>
        ?: error("Table does not reference target")

    internal val columnsAndTables by lazy {
        val alreadyInJoin = (target.dependsOnTables as? Join)?.alreadyInJoin(table) ?: false
        val entityTables = if (alreadyInJoin) {
            target.dependsOnTables
        } else {
            target.dependsOnTables.join(table, JoinType.INNER, target.table.id, targetColumn)
        }
        val columns = (target.dependsOnColumns + (if (!alreadyInJoin) table.columns else emptyList()) - sourceColumn)
            .distinct() + sourceColumn
        columns to entityTables
    }

    internal fun orderByExpressionsArray(): Array<Pair<Expression<*>, SortOrder>> = orderByExpressions.toTypedArray()

    operator fun provideDelegate(
        thisRef: Source,
        property: KProperty<*>
    ): R2dbcInnerTableLinkAccessor<SID, Source, ID, Target> = R2dbcInnerTableLinkAccessor(this, thisRef)

    infix fun orderBy(order: List<Pair<Expression<*>, SortOrder>>) = this.also {
        orderByExpressions.addAll(order)
    }

    infix fun orderBy(order: Pair<Expression<*>, SortOrder>) = orderBy(listOf(order))

    infix fun orderBy(expression: Expression<*>) = orderBy(listOf(expression to SortOrder.ASC))
}

@Suppress("UNCHECKED_CAST")
class R2dbcInnerTableLinkAccessor<SID : Any, Source : R2dbcEntity<SID>, ID : Any, Target : R2dbcEntity<ID>>(
    val link: R2dbcInnerTableLink<SID, Source, ID, Target>,
    val entity: Source
) {
    operator fun getValue(thisRef: Source, property: KProperty<*>): R2dbcInnerTableLinkAccessor<SID, Source, ID, Target> = this

    suspend operator fun invoke(): SizedIterable<Target> {
        if (entity.id._value == null && !entity.isNewEntity()) return emptySized()
        val transaction = TransactionManager.currentOrNull()
            ?: return entity.getReferenceFromCache(link.sourceColumn)

        val (columns, entityTables) = link.columnsAndTables

        val query: suspend () -> SizedIterable<Target> = {
            @Suppress("SpreadOperator")
            link.target.wrapRows(
                entityTables.select(columns)
                    .where { link.sourceColumn eq entity.id }
                    .orderBy(*link.orderByExpressionsArray())
            )
        }
        return transaction.entityCache.getOrPutReferrers<ID, Target>(link.sourceColumn, entity.id, query).also {
            entity.storeReferenceInCache(link.sourceColumn, it)
        }
    }

    suspend infix fun set(value: List<Target>) {
        val tx = TransactionManager.current()
        val entityCache = tx.entityCache
        entityCache.flush()
        val oldValue = invoke().toList()
        val existingIds = oldValue.map { it.id }.toSet()
        entityCache.removeReferrer(link.sourceColumn, entity.id)

        val targetIds = value.map { it.id }
        executeAsPartOfEntityLifecycle {
            link.table.deleteWhere { (link.sourceColumn eq entity.id) and (link.targetColumn notInList targetIds) }
            link.table.batchInsert(
                targetIds.filter { it !in existingIds },
                shouldReturnGeneratedValues = false
            ) { targetId ->
                this[link.sourceColumn] = entity.id
                this[link.targetColumn] = targetId
            }
        }
    }
}
