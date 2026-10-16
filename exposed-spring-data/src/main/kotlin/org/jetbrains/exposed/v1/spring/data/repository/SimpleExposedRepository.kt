package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.jetbrains.exposed.v1.spring.data.mapping.ProjectionResolver
import org.jetbrains.exposed.v1.spring.data.mapping.orderBy
import org.springframework.dao.IncorrectUpdateSemanticsDataAccessException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.core.EntityInformation
import org.springframework.data.support.PageableExecutionUtils
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Suppress("UNCHECKED_CAST")
@Transactional(readOnly = true)
internal open class SimpleExposedRepository<T : Any, ID : Any>(
    private val entityInformation: EntityInformation<T, ID>,
    private val projectionResolver: ProjectionResolver<T, ID>,
) : ExposedCrudRepository<T, ID> {
    private val metadata = projectionResolver.metadata
    private val table = metadata.table
    private val entityConverter = projectionResolver.entityConverter
    private val versionColumn: Column<Any?>? = metadata.versionColumn

    /**
     * New entities are inserted with one batch insert per shape (with or without an assigned id), existing ones are updated
     * one by one (each update needs its own row count for the checks). Afterwards every saved row is read back with as few
     * queries as possible, so values set by the database (e.g. defaults) are part of the result.
     */
    @Transactional
    override fun <S : T> saveAll(entities: Iterable<S>): List<S> {
        val toSave = entities.toList()
        if (toSave.isEmpty()) return emptyList()

        val ids = arrayOfNulls<Any>(toSave.size) as Array<ID?>
        val (newIndices, existingIndices) = toSave.indices.partition { entityInformation.isNew(toSave[it]) }
        newIndices.groupBy { entityInformation.getId(toSave[it]) != null }.values.forEach { indices ->
            insertRows(indices.map(toSave::get)).forEachIndexed { i, id -> ids[indices[i]] = id }
        }
        existingIndices.forEach { ids[it] = updateRow(toSave[it]) }

        val rows = findRows(ids.map { checkNotNull(it) })
        return ids.map { id -> checkNotNull(rows[id]) { "Saved row with id $id not found in table ${table.tableName}" } as S }
    }

    @Transactional
    override fun <S : T> save(entity: S): S {
        val id = if (entityInformation.isNew(entity)) insertRow(entity) else updateRow(entity)
        return checkNotNull(findRow(id)) { "Saved row with id $id not found in table ${table.tableName}" } as S
    }

    override fun findAll(): List<T> = table
        .selectAll()
        .toEntities()

    override fun findAll(sort: Sort): List<T> = table
        .selectAll()
        .orderBy(sort, metadata)
        .toEntities()

    override fun findAll(pageable: Pageable): Page<T> {
        val query = table.selectAll().orderBy(pageable.sort, metadata)
        if (pageable.isPaged) query.limit(pageable.pageSize).offset(pageable.offset)
        return PageableExecutionUtils.getPage(query.toEntities(), pageable) { count() }
    }

    override fun findAllById(ids: Iterable<ID>): List<T> =
        ids.distinct().chunked(IN_LIST_CHUNK_SIZE).flatMap { chunk ->
            table.selectAll().where { table.id inList chunk }.toEntities()
        }

    override fun findById(id: ID): Optional<T> = Optional.ofNullable(findRow(id))

    override fun existsById(id: ID): Boolean = !table
        .select(table.id)
        .where { table.id eq id }
        .empty()

    override fun count(): Long = table
        .selectAll()
        .count()

    @Transactional
    override fun deleteById(id: ID) {
        table.deleteWhere { table.id eq id }
    }

    @Transactional
    override fun delete(entity: T) {
        val id = entityInformation.getRequiredId(entity)
        if (versionColumn == null) return deleteById(id)

        val deleted = table.deleteWhere { matchesIdAndVersion(id, entity, versionColumn) }
        if (deleted == 0) throw OptimisticLockingFailureException(versionConflict(id, entity))
    }

    @Transactional
    override fun deleteAllById(ids: Iterable<ID>) {
        ids.distinct().chunked(IN_LIST_CHUNK_SIZE).forEach { chunk -> table.deleteWhere { table.id inList chunk } }
    }

    @Transactional
    override fun deleteAll(entities: Iterable<T>) {
        if (versionColumn != null) return entities.forEach(::delete)
        deleteAllById(entities.map(entityInformation::getRequiredId))
    }

    @Transactional
    override fun deleteAll() {
        table.deleteAll()
    }

    private fun insertRow(entity: T): ID = table.insertAndGetId { writeInsert(it, entity) }.value

    private fun insertRows(entities: List<T>): List<ID> {
        if (entities.size == 1) return listOf(insertRow(entities.single()))
        val rows = table.batchInsert(entities, shouldReturnGeneratedValues = true) { writeInsert(this, it) }
        check(rows.size == entities.size) { "Batch insert into ${table.tableName} returned ${rows.size} rows for ${entities.size} entities" }
        return rows.map { it[table.id].value }
    }

    private fun writeInsert(statement: UpdateBuilder<*>, entity: T) {
        projectionResolver.fill(statement, entity)
        entityInformation.getId(entity)?.let { statement[table.id] = it }
        versionColumn?.let { statement[it] = metadata.initialVersion() }
    }

    private fun updateRow(entity: T): ID {
        val id = entityInformation.getRequiredId(entity)
        if (versionColumn == null) {
            val updated = table.update({ table.id eq id }) { projectionResolver.fill(it, entity) }
            if (updated == 0) throw IncorrectUpdateSemanticsDataAccessException("No row with id $id in table ${table.tableName}")
        } else {
            val updated = table.update({ matchesIdAndVersion(id, entity, versionColumn) }) {
                projectionResolver.fill(it, entity)
                it[versionColumn] = metadata.nextVersion(requiredVersion(entity))
            }
            if (updated == 0) throw OptimisticLockingFailureException(versionConflict(id, entity))
        }
        return id
    }

    private fun matchesIdAndVersion(id: ID, entity: T, versionColumn: Column<Any?>): Op<Boolean> =
        (table.id eq id) and (versionColumn eq requiredVersion(entity))

    private fun requiredVersion(entity: T): Number = checkNotNull(metadata.versionOf(entity)) {
        "${metadata.domainType.simpleName} with id ${entityInformation.getId(entity)} is not new, but has no version"
    }

    private fun versionConflict(id: ID, entity: T): String =
        "No row with id $id and version ${metadata.versionOf(entity)} in table ${table.tableName}: it was changed or deleted in the meantime"

    private fun findRow(id: ID): T? = table
        .selectAll()
        .where { table.id eq id }
        .singleOrNull()
        ?.let(entityConverter::convert)

    private fun findRows(ids: List<ID>): Map<ID, T> =
        ids.distinct().chunked(IN_LIST_CHUNK_SIZE).flatMap { chunk ->
            table.selectAll().where { table.id inList chunk }.map { it[table.id].value to entityConverter.convert(it) }
        }.toMap()

    private fun Query.toEntities(): List<T> = map(entityConverter::convert)

    private companion object {
        // Oracle allows at most 1000 values in an IN list
        const val IN_LIST_CHUNK_SIZE = 1000
    }
}
