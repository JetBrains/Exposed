package org.jetbrains.exposed.v1.spring

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.sql.SchemaUtils
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.Ordered
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.core.type.filter.RegexPatternTypeFilter
import org.springframework.transaction.annotation.Transactional
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
open class DatabaseInitializer(private val applicationContext: ApplicationContext, private val excludedPackages: List<String>) :
    ApplicationRunner, Ordered {
    override fun getOrder(): Int = DATABASE_INITIALIZER_ORDER

    companion object {
        const val DATABASE_INITIALIZER_ORDER = 0
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments?) {
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
    val components = packages.map { provider.findCandidateComponents(it) }.flatten()
    return components.mapNotNull { Class.forName(it.beanClassName).kotlin.objectInstance as? Table }
}
