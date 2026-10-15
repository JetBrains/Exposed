package org.jetbrains.exposed.v1.spring.data

import org.springframework.data.repository.ListCrudRepository
import org.springframework.data.repository.NoRepositoryBean

@NoRepositoryBean
interface ExposedCrudRepository<T : Any, ID : Any> : ListCrudRepository<T, ID>
