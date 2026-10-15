package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.springframework.data.repository.core.support.RepositoryFactorySupport
import org.springframework.data.repository.core.support.TransactionalRepositoryFactoryBeanSupport

class ExposedRepositoryFactoryBean<T : ExposedCrudRepository<S, ID>, S : Any, ID : Any>(
    repositoryInterface: Class<T>
) : TransactionalRepositoryFactoryBeanSupport<T, S, ID>(repositoryInterface) {

    override fun doCreateRepositoryFactory(): RepositoryFactorySupport {
        return ExposedRepositoryFactory()
    }
}
