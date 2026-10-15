package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedEntityMapper
import org.springframework.dao.IncorrectUpdateSemanticsDataAccessException
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Suppress("UNCHECKED_CAST")
@Transactional(readOnly = true)
open class SimpleExposedRepository<T : Any, ID : Any>(
    private val table: IdTable<ID>,
    private val entityInformation: ExposedEntityInformation<T, ID>,
    private val entityMapper: ExposedEntityMapper<T>,
) : ExposedCrudRepository<T, ID> {

    @Transactional
    override fun <S : T> saveAll(entities: Iterable<S>): List<S> {
        return entities.map { save(it) }
    }

    @Transactional
    override fun <S : T> save(entity: S): S =
        if (entityInformation.isNew(entity)) insert(entity) else update(entity)


    override fun findAll(): List<T> = table
        .selectAll()
        .map(entityMapper::toEntity)

    override fun findAllById(ids: Iterable<ID>): List<T> = table
        .selectAll()
        .where { table.id inList ids }
        .map(entityMapper::toEntity)

    override fun findById(id: ID): Optional<T> = table
        .selectAll()
        .where { table.id eq id }
        .map(entityMapper::toEntity)
        .firstOrNull()
        .let { Optional.ofNullable(it) }

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
        val id = table.insertAndGetId { entityMapper.fill(it, entity) }.value
        return entityMapper.toEntity(table.selectAll().where { table.id eq id }.single()) as S
    }

    private fun <S : T> update(entity: S): S {
        val id = entityInformation.getRequiredId(entity)
        val updated = table.update({ table.id eq id }) {
            entityMapper.fill(it, entity)
        }
        if (updated == 0) throw IncorrectUpdateSemanticsDataAccessException("No row with id $id in table ${table.tableName}")
        return entityMapper.toEntity(table.selectAll().where { table.id eq id }.single()) as S
    }
}
