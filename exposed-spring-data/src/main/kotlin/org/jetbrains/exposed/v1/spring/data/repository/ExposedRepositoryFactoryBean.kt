package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedMappingContext
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.getBeanProvider
import org.springframework.data.repository.core.support.RepositoryFactorySupport
import org.springframework.data.repository.core.support.TransactionalRepositoryFactoryBeanSupport

internal class ExposedRepositoryFactoryBean<T : ExposedCrudRepository<S, ID>, S : Any, ID : Any>(
    repositoryInterface: Class<T>
) : TransactionalRepositoryFactoryBeanSupport<T, S, ID>(repositoryInterface) {
    private var mappingContext: ExposedMappingContext? = null

    override fun setBeanFactory(beanFactory: BeanFactory) {
        super.setBeanFactory(beanFactory)
        mappingContext = beanFactory.getBeanProvider<ExposedMappingContext>().ifAvailable
    }

    override fun doCreateRepositoryFactory(): RepositoryFactorySupport {
        return ExposedRepositoryFactory(mappingContext ?: ExposedMappingContext())
    }
}
