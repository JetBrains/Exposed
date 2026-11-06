package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoriesRegistrar
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactoryBean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.AliasFor
import org.springframework.data.repository.config.BootstrapMode
import org.springframework.data.repository.config.DefaultRepositoryBaseClass
import java.lang.annotation.Inherited
import kotlin.reflect.KClass

@OptIn(InternalApi::class)
@ExposedRepositoriesConfig
@Suppress("LongParameterList")
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
@Import(ExposedRepositoriesRegistrar::class)
annotation class EnableExposedRepositories(
    @get:AliasFor(annotation = ExposedRepositoriesConfig::class, attribute = "value")
    vararg val value: String = [],
    @get:AliasFor(annotation = ExposedRepositoriesConfig::class, attribute = "basePackages")
    val basePackages: Array<String> = [],
    @get:AliasFor(annotation = ExposedRepositoriesConfig::class, attribute = "basePackageClasses")
    val basePackageClasses: Array<KClass<*>> = [],
    @get:AliasFor(annotation = ExposedRepositoriesConfig::class, attribute = "includeFilters")
    val includeFilters: Array<ComponentScan.Filter> = [],
    @get:AliasFor(annotation = ExposedRepositoriesConfig::class, attribute = "excludeFilters")
    val excludeFilters: Array<ComponentScan.Filter> = [],
    @get:AliasFor(annotation = ExposedRepositoriesConfig::class, attribute = "repositoryImplementationPostfix")
    val repositoryImplementationPostfix: String = "Impl",
    @get:AliasFor(annotation = ExposedRepositoriesConfig::class, attribute = "repositoryFactoryBeanClass")
    val repositoryFactoryBeanClass: KClass<*> = ExposedRepositoryFactoryBean::class,
    @get:AliasFor(annotation = ExposedRepositoriesConfig::class, attribute = "repositoryBaseClass")
    val repositoryBaseClass: KClass<*> = DefaultRepositoryBaseClass::class,
    @get:AliasFor(annotation = ExposedRepositoriesConfig::class, attribute = "considerNestedRepositories")
    val considerNestedRepositories: Boolean = false,
    @get:AliasFor(annotation = ExposedRepositoriesConfig::class, attribute = "bootstrapMode")
    val bootstrapMode: BootstrapMode = BootstrapMode.DEFAULT,
    @get:AliasFor(annotation = ExposedRepositoriesConfig::class, attribute = "transactionManagerRef")
    val transactionManagerRef: String = "transactionManager",
)
