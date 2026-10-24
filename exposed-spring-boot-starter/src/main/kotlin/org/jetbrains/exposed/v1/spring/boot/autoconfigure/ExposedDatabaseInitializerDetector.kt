package org.jetbrains.exposed.v1.spring.boot.autoconfigure

import org.jetbrains.exposed.v1.spring.boot.DatabaseInitializer
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.sql.init.dependency.DatabaseInitializerDetector

/**
 * Reports the [SpringTransactionManager] beans as database initializers for Spring Boot's automatic dependency
 * ordering, because Exposed's schema is created while the auto-configured transaction manager bean is initialized.
 *
 * Beans annotated with [org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization], and Boot's
 * built-in detection points such as `JdbcTemplate`, are therefore initialized after the schema exists.
 * Nothing is reported when no [DatabaseInitializer] bean is defined, so startup order is unchanged when DDL
 * generation is disabled.
 */
class ExposedDatabaseInitializerDetector : DatabaseInitializerDetector {

    companion object {
        /**
         * Runs Exposed DDL after Flyway (`FlywayMigrationInitializer` detector order 1) and Liquibase (order 0),
         * and before Spring Boot's `schema.sql`/`data.sql` script initializers (`LOWEST_PRECEDENCE - 100`).
         */
        const val DETECTOR_ORDER = 2
    }

    override fun detect(beanFactory: ConfigurableListableBeanFactory): Set<String> {
        val hasInitializer = beanFactory.getBeanNamesForType(DatabaseInitializer::class.java, true, false).isNotEmpty()
        if (!hasInitializer) return emptySet()
        return beanFactory.getBeanNamesForType(SpringTransactionManager::class.java, true, false).toSet()
    }

    override fun getOrder(): Int = DETECTOR_ORDER
}
