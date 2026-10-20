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
