package org.jetbrains.exposed.v1.spring.boot

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.core.type.filter.RegexPatternTypeFilter
import java.util.regex.Pattern

/**
 * Base class responsible for the automatic creation of a database schema, using the results of [discoverExposedTables].
 *
 * If more than just table creation is required, a derived class can be implemented to override the transactional
 * function, [run], so that other schema operations can be performed when initialized.
 *
 * @property applicationContext The Spring ApplicationContext container responsible for managing beans.
 * @property excludedPackages List of packages to exclude, so that their contained tables are not auto-created.
 */
open class DatabaseInitializer(
    private val applicationContext: ApplicationContext,
    private val excludedPackages: List<String>
) : InitializingBean {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun afterPropertiesSet() {
        createTransaction()

        val exposedTables = discoverExposedTables(applicationContext, excludedPackages)
        logger.info("Schema generation for tables '{}'", exposedTables.map { it.tableName })

        logger.info("ddl {}", exposedTables.map { it.ddl }.joinToString())
        SchemaUtils.create(tables = exposedTables.toTypedArray())
    }

    @OptIn(InternalApi::class)
    private fun createTransaction() {
        val transaction = TransactionManager.manager.newTransaction(readOnly = false)
        ThreadLocalTransactionsStack.pushTransaction(transaction)
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
