package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.jetbrains.exposed.v1.spring.data.auditing.ExposedAuditingRegistrar
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedMappingContext
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.getBeanProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.mapping.callback.EntityCallbacks
import org.springframework.data.repository.core.support.RepositoryFactorySupport
import org.springframework.data.repository.core.support.TransactionalRepositoryFactoryBeanSupport

internal class ExposedRepositoryFactoryBean<T : ExposedCrudRepository<S, ID>, S : Any, ID : Any>(
    repositoryInterface: Class<T>
) : TransactionalRepositoryFactoryBeanSupport<T, S, ID>(repositoryInterface) {
    private var mappingContext: ExposedMappingContext? = null
    private var entityCallbacks: EntityCallbacks? = null
    private var eventPublisher: ApplicationEventPublisher? = null
    private var auditing: Boolean = false

    override fun setBeanFactory(beanFactory: BeanFactory) {
        super.setBeanFactory(beanFactory)
        mappingContext = beanFactory.getBeanProvider<ExposedMappingContext>().ifAvailable
        entityCallbacks = EntityCallbacks.create(beanFactory)
        auditing = beanFactory.containsBean(ExposedAuditingRegistrar.AUDITING_HANDLER_BEAN_NAME)
    }

    override fun setApplicationEventPublisher(publisher: ApplicationEventPublisher) {
        super.setApplicationEventPublisher(publisher)
        eventPublisher = publisher
    }

    override fun doCreateRepositoryFactory(): RepositoryFactorySupport = ExposedRepositoryFactory(
        mappingContext = mappingContext ?: ExposedMappingContext(),
        entityCallbacks = entityCallbacks,
        eventPublisher = eventPublisher,
        auditing = auditing,
    )
}
