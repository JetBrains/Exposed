package org.jetbrains.exposed.v1.spring.data.repository

import org.springframework.data.repository.core.RepositoryMetadata

internal val RepositoryMetadata.domainTypeName: String
    get() = checkNotNull(domainType.kotlin.qualifiedName ?: domainType.kotlin.simpleName) {
        "Entity class has no qualified name or simple name"
    }
