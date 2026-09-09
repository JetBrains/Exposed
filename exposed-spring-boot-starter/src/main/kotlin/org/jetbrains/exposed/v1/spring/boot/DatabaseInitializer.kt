package org.jetbrains.exposed.v1.spring.boot

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.Ordered
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.core.type.filter.RegexPatternTypeFilter
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.regex.Pattern

/**
 * Creates the database schema using discovered [Table][org.jetbrains.exposed.v1.core.Table] objects
 * during the bean initialization phase.
 *
 * A derived class can override [run] to perform additional schema operations.
 */
open class DatabaseInitializer(
    private val applicationContext: ApplicationContext,
    private val excludedPackages: List<String>,
    private val transactionManager: PlatformTransactionManager
) : InitializingBean, Ordered {
    override fun getOrder(): Int = DATABASE_INITIALIZER_ORDER

    companion object {
        const val DATABASE_INITIALIZER_ORDER = 0
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun afterPropertiesSet() {
        TransactionTemplate(transactionManager).execute {
            run(null)
        }
    }

    /**
     * Discovers and creates database tables. Subclasses can override this to add custom schema operations.
     */
    open fun run(args: ApplicationArguments?) {
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
