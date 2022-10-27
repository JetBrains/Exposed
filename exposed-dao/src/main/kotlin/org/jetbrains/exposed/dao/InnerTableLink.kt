package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.transactions.TransactionManager
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Suppress("UNCHECKED_CAST")
class InnerTableLink<SID : Comparable<SID>, Source : Entity<SID>, ID : Comparable<ID>, Target : Entity<ID>>(
    val table: Table,
    sourceTable: IdTable<SID>,
    val target: EntityClass<ID, Target>,
    _sourceColumn: Column<EntityID<SID>>? = null,
    _targetColumn: Column<EntityID<ID>>? = null,
) : ReadWriteProperty<Source, SizedIterable<Target>> {
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

    val sourceColumn = _sourceColumn
        ?: table.columns.singleOrNull { it.referee == sourceTable.id } as? Column<EntityID<SID>>
        ?: error("Table does not reference source")

    val targetColumn = _targetColumn
        ?: table.columns.singleOrNull { it.referee == target.table.id } as? Column<EntityID<ID>>
        ?: error("Table does not reference target")

    private val columnsAndTables by lazy {
        val alreadyInJoin = (target.dependsOnTables as? Join)?.alreadyInJoin(table) ?: false
        val entityTables =
            if (alreadyInJoin) target.dependsOnTables else target.dependsOnTables.join(table, JoinType.INNER, target.table.id, targetColumn)

        val columns = (target.dependsOnColumns + (if (!alreadyInJoin) table.columns else emptyList()) - sourceColumn).distinct() + sourceColumn

        columns to entityTables
    }

    override operator fun getValue(o: Source, unused: KProperty<*>): SizedIterable<Target> {
        if (o.id._value == null && !o.isNewEntity()) return emptySized()
        val transaction = TransactionManager.currentOrNull()
            ?: return o.getReferenceFromCache(sourceColumn)

        val (columns, entityTables) = columnsAndTables

        val query = { target.wrapRows(entityTables.slice(columns).select { sourceColumn eq o.id }) }
        return transaction.entityCache.getOrPutReferrers(o.id, sourceColumn, query).also {
            o.storeReferenceInCache(sourceColumn, it)
        }
    }

    override fun setValue(o: Source, unused: KProperty<*>, value: SizedIterable<Target>) {
        val entityCache = TransactionManager.current().entityCache
        if (entityCache.isEntityInInitializationState(o)) {
            entityCache.pendingInitializationLambdas.getOrPut(o) { arrayListOf() }.add {
                setReference(it as Source, unused, value)
            }
        } else {
            setReference(o, unused, value)
        }
    }

    private fun setReference(o: Source, unused: KProperty<*>, value: SizedIterable<Target>) {
        val tx = TransactionManager.current()
        val entityCache = tx.entityCache
        entityCache.flush()
        val oldValue = getValue(o, unused)
        val existingIds = oldValue.map { it.id }.toSet()
        entityCache.referrers[sourceColumn]?.remove(o.id)

        val targetIds = value.map { it.id }
        executeAsPartOfEntityLifecycle {
            table.deleteWhere { (sourceColumn eq o.id) and (targetColumn notInList targetIds) }
            table.batchInsert(targetIds.filter { !existingIds.contains(it) }, shouldReturnGeneratedValues = false) { targetId ->
                this[sourceColumn] = o.id
                this[targetColumn] = targetId
            }
        }

        // current entity updated
        tx.registerChange(o.klass, o.id, EntityChangeType.Updated)

        // linked entities updated
        val targetClass = (value.firstOrNull() ?: oldValue.firstOrNull())?.klass
        if (targetClass != null) {
            existingIds.plus(targetIds).forEach {
                tx.registerChange(targetClass, it, EntityChangeType.Updated)
            }
        }
    }
}
