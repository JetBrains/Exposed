package org.jetbrains.exposed.v1.spring.data.repository

import org.springframework.data.domain.Persistable
import org.springframework.data.repository.core.RepositoryMetadata
import org.springframework.data.repository.core.support.AbstractEntityInformation

/** Entity information for entities that implement [Persistable]: the entity itself tells its id and whether it is new. */
@Suppress("UNCHECKED_CAST")
internal class PersistableEntityInformation<T : Persistable<ID>, ID : Any>(
    metadata: RepositoryMetadata,
) : AbstractEntityInformation<T, ID>(metadata.domainType as Class<T>) {
    private val idType = metadata.idType as Class<ID>

    override fun isNew(entity: T): Boolean = entity.isNew

    override fun getId(entity: T): ID? = entity.id

    override fun getIdType(): Class<ID> = idType
}
