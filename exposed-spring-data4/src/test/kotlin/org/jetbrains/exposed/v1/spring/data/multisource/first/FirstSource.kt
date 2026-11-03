package org.jetbrains.exposed.v1.spring.data.multisource.first

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.spring.data.EnableExposedRepositories
import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.jetbrains.exposed.v1.spring.data.ExposedRepository
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType

/** The first of two `@EnableExposedRepositories` sources of one context, see `ExposedTableRegistryMultiSourceTest`. */
@Configuration
@EnableExposedRepositories(
    basePackageClasses = [FirstSourceConfiguration::class],
    considerNestedRepositories = true,
    includeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [FirstSourceConfiguration.MultiSourceParentRepository::class])],
)
open class FirstSourceConfiguration {
    @ExposedRepository(table = MultiSourceParents::class)
    interface MultiSourceParentRepository : ExposedCrudRepository<MultiSourceParent, Long>
}

object MultiSourceParents : LongIdTable("multi_source_parents") {
    val name = varchar("name", 64)
}

data class MultiSourceParent(val id: Long? = null, val name: String)
