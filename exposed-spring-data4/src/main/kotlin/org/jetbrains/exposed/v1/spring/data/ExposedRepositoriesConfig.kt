package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactoryBean
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.repository.config.BootstrapMode
import org.springframework.data.repository.config.DefaultRepositoryBaseClass
import org.springframework.data.repository.query.QueryLookupStrategy
import kotlin.reflect.KClass

/** @suppress */
@InternalApi
@Suppress("LongParameterList")
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExposedRepositoriesConfig(
    val value: Array<String> = [],
    val basePackages: Array<String> = [],
    val basePackageClasses: Array<KClass<*>> = [],
    val includeFilters: Array<ComponentScan.Filter> = [],
    val excludeFilters: Array<ComponentScan.Filter> = [],
    val repositoryImplementationPostfix: String = "Impl",
    val namedQueriesLocation: String = "",
    val queryLookupStrategy: QueryLookupStrategy.Key = QueryLookupStrategy.Key.CREATE_IF_NOT_FOUND,
    val repositoryFactoryBeanClass: KClass<*> = ExposedRepositoryFactoryBean::class,
    val repositoryBaseClass: KClass<*> = DefaultRepositoryBaseClass::class,
    val considerNestedRepositories: Boolean = false,
    val bootstrapMode: BootstrapMode = BootstrapMode.DEFAULT,
    val transactionManagerRef: String = "transactionManager",
)
