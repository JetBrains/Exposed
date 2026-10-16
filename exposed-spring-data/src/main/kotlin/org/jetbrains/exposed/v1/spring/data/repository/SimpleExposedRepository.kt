package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.jetbrains.exposed.v1.spring.data.mapping.ProjectionResolver
import org.jetbrains.exposed.v1.spring.data.mapping.orderBy
import org.springframework.dao.IncorrectUpdateSemanticsDataAccessException
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

    @Transactional
    override fun <S : T> saveAll(entities: Iterable<S>): List<S> {
        return entities.map { save(it) }
    }

    @Transactional
    override fun <S : T> save(entity: S): S =
        if (entityInformation.isNew(entity)) insert(entity) else update(entity)

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

    override fun findAllById(ids: Iterable<ID>): List<T> = table
        .selectAll()
        .where { table.id inList ids }
        .toEntities()

    override fun findById(id: ID): Optional<T> = Optional.ofNullable(findRow(id))

    override fun existsById(id: ID): Boolean = table
        .selectAll()
        .where { table.id eq id }
        .count() > 0

    override fun count(): Long = table
        .selectAll()
        .count()

    @Transactional
    override fun deleteById(id: ID) {
        table.deleteWhere { table.id eq id }
    }

    @Transactional
    override fun delete(entity: T) {
        deleteById(entityInformation.getRequiredId(entity))
    }

    @Transactional
    override fun deleteAllById(ids: Iterable<ID>) {
        table.deleteWhere { table.id inList ids }
    }

    @Transactional
    override fun deleteAll(entities: Iterable<T>) {
        table.deleteWhere { table.id inList entities.map(entityInformation::getRequiredId) }
    }

    @Transactional
    override fun deleteAll() {
        table.deleteAll()
    }

    private fun <S : T> insert(entity: S): S {
        val id = table.insertAndGetId { projectionResolver.fill(it, entity) }.value
        return checkNotNull(findRow(id)) as S
    }

    private fun <S : T> update(entity: S): S {
        val id = entityInformation.getRequiredId(entity)
        val updated = table.update({ table.id eq id }) {
            projectionResolver.fill(it, entity)
        }
        if (updated == 0) throw IncorrectUpdateSemanticsDataAccessException("No row with id $id in table ${table.tableName}")
        return checkNotNull(findRow(id)) as S
    }

    private fun findRow(id: ID): T? = table
        .selectAll()
        .where { table.id eq id }
        .singleOrNull()
        ?.let(entityConverter::convert)

    private fun Query.toEntities(): List<T> = map(entityConverter::convert)
}
