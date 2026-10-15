package org.jetbrains.exposed.v1.spring.data

import org.springframework.data.repository.ListCrudRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.transaction.annotation.Transactional

/**
 * Derived query methods declared on sub-interfaces run in a read-only transaction by default.
 * Modifying derived queries (`deleteBy…`, `removeBy…`) must be annotated with [Transactional].
 */
@NoRepositoryBean
@Transactional(readOnly = true)
interface ExposedCrudRepository<T : Any, ID : Any> : ListCrudRepository<T, ID>
