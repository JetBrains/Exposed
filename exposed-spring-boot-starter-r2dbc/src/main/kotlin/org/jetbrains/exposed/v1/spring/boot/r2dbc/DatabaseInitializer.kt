package org.jetbrains.exposed.v1.spring.boot.r2dbc

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.spring.reactive.transaction.SpringReactiveTransactionManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.Ordered
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.core.type.filter.RegexPatternTypeFilter
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.util.regex.Pattern

/**
 * Base class responsible for the automatic creation of a database schema, using the results of [discoverExposedTables].
 *
 * If more than just table creation is required, a derived class can be implemented to override the
 * function, [run], so that other schema operations can be performed when initialized.
 *
 * @property applicationContext The Spring ApplicationContext container responsible for managing beans.
 * @property excludedPackages List of packages to exclude, so that their contained tables are not auto-created.
 */
open class DatabaseInitializer(
    private val applicationContext: ApplicationContext,
    private val excludedPackages: List<String>
) :
    ApplicationRunner, Ordered {
    override fun getOrder(): Int = DATABASE_INITIALIZER_ORDER

    companion object {
        const val DATABASE_INITIALIZER_ORDER = 0
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    lateinit var springReactiveTransactionManager: SpringReactiveTransactionManager

//    @Transactional
    // Cannot apply reactive transaction to non-reactive return type [void] with specified transaction manager
    // https://github.com/spring-projects/spring-framework/issues/23277
    override fun run(args: ApplicationArguments) {
        // synchronous & ordered nature of ApplicationRunner for app start-up tasks does not permit use with coroutines
        // https://github.com/spring-projects/spring-boot/issues/34338
        runBlocking {
            val operator = TransactionalOperator.create(springReactiveTransactionManager)
            operator.executeAndAwait {
                val exposedTables = discoverExposedTables(applicationContext, excludedPackages)
                logger.info("Schema generation for tables '{}'", exposedTables.map { it.tableName })

                logger.info("ddl {}", exposedTables.map { it.ddl }.joinToString())
                SchemaUtils.create(tables = exposedTables.toTypedArray())
            }
        }
    }
}

/**
 * Returns a list of identified tables that extend Exposed's base [Table] class, without searching any packages
 * in [excludedPackages].
 */
fun discoverExposedTables(applicationContext: ApplicationContext, excludedPackages: List<String>): List<Table> {
    val provider = ClassPathScanningCandidateComponentProvider(false)
    provider.addIncludeFilter(AssignableTypeFilter(Table::class.java))
    excludedPackages.forEach {
        provider.addExcludeFilter(
            RegexPatternTypeFilter(Pattern.compile(it.replace(".", "\\.") + ".*"))
        )
    }
    val packages = AutoConfigurationPackages.get(applicationContext)
    val components = packages.map { provider.findCandidateComponents(it) }.flatten()
    return components.mapNotNull { Class.forName(it.beanClassName).kotlin.objectInstance as? Table }
}
