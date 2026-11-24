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

/**
 * Enables Exposed repositories: every interface extending [ExposedCrudRepository] in the scanned packages becomes a bean.
 *
 * ## Ahead-of-time processing and native images
 *
 * When processed ahead of time (Spring AOT, GraalVM native image), the runtime hints for the table `object` of
 * [ExposedRepository], the domain type and every projection type declared in the return type of a repository method
 * (query methods and `query { }` default methods, also inside `List`, `Optional`, `Page`, `Slice`, `Window` or `Stream`)
 * are registered automatically.
 *
 * Projection types that are only known when the method is called can't be found at build time and need
 * `@RegisterReflectionForBinding` (e.g. on a configuration class):
 * - dynamic projections, e.g. `fun <T : Any> findByEmail(email: String, type: Class<T>): T?`;
 * - `query<T> { }` with a type that doesn't appear in the return type of the repository method, e.g. in a generic method
 *   or when the converted rows are processed further before returning.
 *
 * Such interface projections also need a JDK proxy hint for the interface together with `TargetAware`, `SpringProxy` and
 * `DecoratingProxy` (`RuntimeHints.proxies().registerJdkProxy(...)`).
 */
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
