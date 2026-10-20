package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoriesRegistrar
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactoryBean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.data.repository.config.BootstrapMode
import org.springframework.data.repository.config.DefaultRepositoryBaseClass
import org.springframework.data.repository.query.QueryLookupStrategy
import java.lang.annotation.Inherited
import kotlin.reflect.KClass

@Suppress("LongParameterList")
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
@Import(ExposedRepositoriesRegistrar::class)
annotation class EnableExposedRepositories(
    vararg val value: String = [],
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
