package org.jetbrains.exposed.v1.spring.data.repository

import org.springframework.data.repository.core.RepositoryInformation
import org.springframework.data.repository.core.RepositoryMetadata


internal val RepositoryInformation.repositoryName: String
    get() = checkNotNull(repositoryInterface.kotlin.qualifiedName ?: repositoryInterface.kotlin.simpleName) {
        "Entity class has no qualified name or simple name"
    }

internal val RepositoryInformation.domainTypeName: String
    get() = checkNotNull(domainType.kotlin.qualifiedName ?: domainType.kotlin.simpleName) {
        "Entity class has no qualified name or simple name"
    }

internal val RepositoryMetadata.domainTypeName: String
    get() = checkNotNull(domainType.kotlin.qualifiedName ?: domainType.kotlin.simpleName) {
        "Entity class has no qualified name or simple name"
    }
