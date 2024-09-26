package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.transactions.TransactionManager
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Class responsible for implementing property delegates of the read-write properties involved in a many-to-many
 * relation, which uses an intermediate (join) table.
 *
 * @param table The intermediate table containing reference columns to both child and parent entities.
 * @param sourceTable The [IdTable] associated with the source child entity.
 * @param target The [EntityClass] for the target parent entity.
 * @param _sourceColumn The intermediate table's reference column for the child entity class. If left `null`,
 * this will be inferred from the provided intermediate [table] columns.
 * @param _targetColumn The intermediate table's reference column for the parent entity class. If left `null`,
 * this will be inferred from the provided intermediate [table] columns.
 * @param additionalColumns Any additional columns from the intermediate table that should be included when inserting.
 * If left `null`, these will be inferred from the provided intermediate [table] columns.
 */
@Suppress("UNCHECKED_CAST")
class InnerTableLink<SID : Comparable<SID>, Source : Entity<SID>, ID : Comparable<ID>, Target : Entity<ID>>(
    val table: Table,
    sourceTable: IdTable<SID>,
    val target: EntityClass<ID, Target>,
    _sourceColumn: Column<EntityID<SID>>? = null,
    _targetColumn: Column<EntityID<ID>>? = null,
    additionalColumns: List<Column<*>>? = null,
) : ReadWriteProperty<Source, SizedIterable<Target>> {
    /** The list of columns and their [SortOrder] for ordering referred entities in many-to-many relationship. */
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
        additionalColumns?.let {
            require(it.all { column -> column.table == table }) {
                "All additional columns should be from the same intermediate table ${table.tableName}"
            }
        }
    }

    /** The reference identity column for the child entity class. */
    val sourceColumn = _sourceColumn
        ?: table.columns.singleOrNull { it.referee == sourceTable.id } as? Column<EntityID<SID>>
        ?: error("Table does not reference source")

    /** The reference identity column for the parent entity class. */
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

    private val additionalColumns = (additionalColumns ?: (table.columns - sourceColumn - targetColumn).filter { !it.columnType.isAutoInc })
        .takeIf { it.isEmpty() || target is InnerTableLinkEntityClass<ID, *> }
        ?: error("Target entity must extend InnerTableLinkEntity to properly store and cache additional column data")

    override operator fun getValue(o: Source, unused: KProperty<*>): SizedIterable<Target> {
        if (o.id._value == null && !o.isNewEntity()) return emptySized()
        val transaction = TransactionManager.currentOrNull()
            ?: return o.getReferenceFromCache(sourceColumn)

        val (columns, entityTables) = columnsAndTables

        val query = {
            @Suppress("SpreadOperator")
            val row = entityTables.select(columns)
                .where { sourceColumn eq o.id }
                .orderBy(*orderByExpressions.toTypedArray())
            (target as? InnerTableLinkEntityClass<ID, *>)
                ?.wrapLinkRows(row, targetColumn, sourceColumn) as? SizedIterable<Target>
                ?: target.wrapRows(row)
        }
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
        val existingValues = oldValue.mapIdToAdditionalValues()
        val existingIds = existingValues.keys
        entityCache.referrers[sourceColumn]?.remove(o.id)

        val targetValues = value.mapIdToAdditionalValues()
        val targetIds = targetValues.keys
        val additionalColumnsExist = additionalColumns.isNotEmpty()
        if (additionalColumnsExist) {
            entityCache.innerTableLinks[targetColumn]?.get(o.id)?.removeAll { cached ->
                targetValues[cached.id]?.any { (column, targetValue) ->
                    cached.getInnerTableLinkValue(column) != targetValue
                } != false
            }
        }

        executeAsPartOfEntityLifecycle {
            val deleteCondition = if (additionalColumnsExist) {
                val targetAdditionalValues = targetValues.map { it.value.values.toList() + it.key }
                (sourceColumn eq o.id) and (additionalColumns + targetColumn notInList targetAdditionalValues)
            } else {
                (sourceColumn eq o.id) and (targetColumn notInList targetIds)
            }
            val newTargets = targetValues.filter { (targetId, additionalValues) ->
                if (additionalColumnsExist) {
                    targetId !in existingIds ||
                        existingValues[targetId]?.entries?.containsAll(additionalValues.entries) == false
                } else {
                    targetId !in existingIds
                }
            }
            table.deleteWhere { deleteCondition }
            table.batchInsert(newTargets.entries, shouldReturnGeneratedValues = false) { (targetId, additionalValues) ->
                this[sourceColumn] = o.id
                this[targetColumn] = targetId
                additionalValues.forEach { (column, value) ->
                    this[column as Column<Any?>] = value
                }
            }
        }

        // current entity updated
        tx.registerChange(o.klass, o.id, EntityChangeType.Updated)

        // linked entities updated
        val targetClass = (value.firstOrNull() ?: oldValue.firstOrNull())?.let {
            (it as? InnerTableLinkEntity<ID>)?.wrapped?.klass ?: it.klass
        }
        if (targetClass != null) {
            existingIds.plus(targetIds).forEach {
                tx.registerChange(targetClass, it, EntityChangeType.Updated)
            }
        }
    }

    private fun SizedIterable<Target>.mapIdToAdditionalValues(): Map<EntityID<ID>, Map<Column<*>, Any?>> {
        return associate { target ->
            target.id to additionalColumns.associateWith { (target as InnerTableLinkEntity<ID>).getInnerTableLinkValue(it) }
        }
    }

    /** Modifies this reference to sort entities based on multiple columns as specified in [order]. **/
    infix fun orderBy(order: List<Pair<Expression<*>, SortOrder>>) = this.also {
        orderByExpressions.addAll(order)
    }

    /** Modifies this reference to sort entities according to the specified [order]. **/
    infix fun orderBy(order: Pair<Expression<*>, SortOrder>) = orderBy(listOf(order))

    /** Modifies this reference to sort entities by a column specified in [expression] using ascending order. **/
    infix fun orderBy(expression: Expression<*>) = orderBy(listOf(expression to SortOrder.ASC))
}
