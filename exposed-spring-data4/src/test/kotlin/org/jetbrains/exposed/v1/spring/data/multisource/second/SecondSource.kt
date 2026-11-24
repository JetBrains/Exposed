package org.jetbrains.exposed.v1.spring.data.multisource.second

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.spring.data.EnableExposedRepositories
import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.jetbrains.exposed.v1.spring.data.ExposedRepository
import org.jetbrains.exposed.v1.spring.data.multisource.first.MultiSourceParents
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType

/** The second of two `@EnableExposedRepositories` sources of one context; its table references a table of the first source. */
@Configuration
@EnableExposedRepositories(
    basePackageClasses = [SecondSourceConfiguration::class],
    considerNestedRepositories = true,
    includeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [SecondSourceConfiguration.MultiSourceChildRepository::class])],
)
open class SecondSourceConfiguration {
    @ExposedRepository(table = MultiSourceChildren::class)
    interface MultiSourceChildRepository : ExposedCrudRepository<MultiSourceChild, Long>
}

object MultiSourceChildren : LongIdTable("multi_source_children") {
    val parent = reference("parent_id", MultiSourceParents)
    val note = varchar("note", 64)
}

data class MultiSourceChild(val id: Long? = null, val note: String)
