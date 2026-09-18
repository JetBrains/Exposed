package org.jetbrains.exposed.v1.spring.boot4.autoconfigure

import org.jetbrains.exposed.v1.spring.boot4.DatabaseInitializer
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.sql.init.dependency.DatabaseInitializerDetector

/**
 * Registers Exposed's [DatabaseInitializer] as a database initializer bean for Spring Boot's automatic
 * dependency ordering, so that beans annotated with
 * [org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization] are initialized after the
 * schema has been created by [DatabaseInitializer.afterPropertiesSet].
 */
class ExposedDatabaseInitializerDetector : DatabaseInitializerDetector {

    override fun detect(beanFactory: ConfigurableListableBeanFactory): Set<String> {
        return beanFactory.getBeanNamesForType(
            DatabaseInitializer::class.java, true, false
        ).toSet()
    }
}
