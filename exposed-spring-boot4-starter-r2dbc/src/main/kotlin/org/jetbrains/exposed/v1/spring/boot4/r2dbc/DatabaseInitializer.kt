package org.jetbrains.exposed.v1.spring.boot4.r2dbc

import kotlinx.coroutines.reactor.mono
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.spring7.reactive.transaction.withExposedReactiveContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.core.type.filter.RegexPatternTypeFilter
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.util.regex.Pattern

/**
 * Base class responsible for the automatic creation of a database schema, using the results of [discoverExposedTables].
 *
 * The schema is created as soon as all singleton beans have been instantiated (see [afterSingletonsInstantiated]).
 * This is before any embedded web server starts to accept requests and before any `ApplicationRunner` is invoked.
 *
 * If more than just table creation is required, a derived class can be implemented to override the transactional
 * function, [initializeSchema], so that other schema operations can be performed when initialized.
 *
 * @property applicationContext The Spring [ApplicationContext] container responsible for managing beans.
 * @property excludedPackages List of packages to exclude, so that their contained tables are not auto-created.
 * @property transactionalOperator The operator used to execute [initializeSchema] in a Spring-managed transaction.
 */
open class DatabaseInitializer(
    private val applicationContext: ApplicationContext,
    private val excludedPackages: List<String>,
    private val transactionalOperator: TransactionalOperator,
) : SmartInitializingSingleton {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Invokes [initializeSchema] in a transaction once all singleton beans have been instantiated, so that the schema
     * already exists when any embedded web server starts to accept requests.
     *
     * Since this is being used for initial schema setup, the start-up thread is blocked until the database operations
     * of [initializeSchema] finish.
     */
    final override fun afterSingletonsInstantiated() {
        // Reactor's block() is used for this, in the same way as Spring Boot's own R2DBC schema initialization
        mono {
            withExposedReactiveContext {
                // unlike with JDBC, using @Transactional on a function that returns Unit is not acceptable and fails with:
                // Cannot apply reactive transaction to non-reactive return type [void] with specified transaction manager.
                // instead, we rely directly on a TransactionalOperator to execute the DDL
                transactionalOperator.executeAndAwait {
                    initializeSchema()
                }
            }
        }.block()
    }

    /** Creates any tables returned by [discoverExposedTables] & logs the schema generation. */
    open suspend fun initializeSchema() {
        val exposedTables = discoverExposedTables(applicationContext, excludedPackages)
        logger.info("Schema generation for tables '{}'", exposedTables.map { it.tableName })

        logger.info("ddl {}", exposedTables.map { it.ddl }.joinToString())
        SchemaUtils.create(tables = exposedTables.toTypedArray())
    }
}

/**
 * Returns a list of identified tables that extend Exposed's base [Table] class, without searching any packages
 * in [excludedPackages].
 */
fun discoverExposedTables(applicationContext: ApplicationContext, excludedPackages: List<String>): List<Table> {
    val provider = ClassPathScanningCandidateComponentProvider(false)
    provider.addIncludeFilter(AssignableTypeFilter(Table::class.java))
    excludedPackages.forEach { provider.addExcludeFilter(RegexPatternTypeFilter(Pattern.compile(it.replace(".", "\\.") + ".*"))) }
    val packages = AutoConfigurationPackages.get(applicationContext)
    val components = packages.flatMap { provider.findCandidateComponents(it) }
    return components.mapNotNull { Class.forName(it.beanClassName).kotlin.objectInstance as? Table }
}
