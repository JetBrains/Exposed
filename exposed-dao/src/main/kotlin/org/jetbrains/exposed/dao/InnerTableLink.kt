package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Suppress("UNCHECKED_CAST")
class InnerTableLink<SID:Comparable<SID>, Source: Entity<SID>, ID:Comparable<ID>, Target: Entity<ID>>(
        val table: Table,
        val target: EntityClass<ID, Target>,
        val sourceColumn: Column<EntityID<SID>>? = null,
        _targetColumn: Column<EntityID<ID>>? = null) : ReadWriteProperty<Source, SizedIterable<Target>> {
    init {
        _targetColumn?.let {
            requireNotNull(sourceColumn) { "Both source and target columns should be specified"}
            require(_targetColumn.referee?.table == target.table) {
                "Column $_targetColumn point to wrong table, expected ${target.table.tableName}"
            }
            require(_targetColumn.table == sourceColumn.table) {
                "Both source and target columns should be from the same table"
            }
        }
        sourceColumn?.let {
            requireNotNull(_targetColumn) { "Both source and target columns should be specified"}
        }
    }

    private val targetColumn = _targetColumn
            ?: table.columns.singleOrNull { it.referee == target.table.id } as? Column<EntityID<ID>>
            ?: error("Table does not reference target")

    private fun getSourceRefColumn(o: Source): Column<EntityID<SID>> {
        return sourceColumn ?: table.columns.singleOrNull { it.referee == o.klass.table.id } as? Column<EntityID<SID>>
        ?: error("Table does not reference source")
    }

    override operator fun getValue(o: Source, unused: KProperty<*>): SizedIterable<Target> {
        if (o.id._value == null) return emptySized()
        val sourceRefColumn = getSourceRefColumn(o)
        val alreadyInJoin = (target.dependsOnTables as? Join)?.alreadyInJoin(table)?: false
        val entityTables = if (alreadyInJoin) target.dependsOnTables else target.dependsOnTables.join(table, JoinType.INNER, target.table.id, targetColumn)

        val columns = (target.dependsOnColumns + (if (!alreadyInJoin) table.columns else emptyList())
            - sourceRefColumn).distinct() + sourceRefColumn

        val query = {target.wrapRows(entityTables.slice(columns).select{sourceRefColumn eq o.id})}
        return TransactionManager.current().entityCache.getOrPutReferrers(o.id, sourceRefColumn, query)
    }

    override fun setValue(o: Source, unused: KProperty<*>, value: SizedIterable<Target>) {
        val sourceRefColumn = getSourceRefColumn(o)

        val tx = TransactionManager.current()
        val entityCache = tx.entityCache
        entityCache.flush()
        val oldValue = getValue(o, unused)
        val existingIds = oldValue.map { it.id }.toSet()
        entityCache.clearReferrersCache()

        val targetIds = value.map { it.id }
        table.deleteWhere { (sourceRefColumn eq o.id) and (targetColumn notInList targetIds) }
        table.batchInsert(targetIds.filter { !existingIds.contains(it) }, shouldReturnGeneratedValues = false) { targetId ->
            this[sourceRefColumn] = o.id
            this[targetColumn] = targetId
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