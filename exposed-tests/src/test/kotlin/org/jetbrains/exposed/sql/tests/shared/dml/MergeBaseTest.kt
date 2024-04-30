package org.jetbrains.exposed.sql.tests.shared.dml

import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB

val TEST_DEFAULT_DATE_TIME = LocalDateTime(2000, 1, 1, 0, 0, 0, 0)

abstract class MergeBaseTest : DatabaseTestsBase() {
    protected fun allDbExcept(includeSettings: List<TestDB>) = TestDB.entries - includeSettings.toSet()

    protected val defaultExcludeSettings = listOf(TestDB.SQLITE, TestDB.MYSQL, TestDB.MARIADB)

    protected fun withMergeTestsTables(excludeSettings: List<TestDB> = emptyList(), statement: Transaction.(TestDB) -> Unit) = withTables(
        defaultExcludeSettings + excludeSettings, Source, Dest
    ) {
        statement(it)
    }

    object Source : IntIdTable("merge_test_source") {
        val key = varchar("merge_test_key", 128)
        val value = integer("merge_test_value")
        val optional = text("merge_test_optional_value").nullable()
        val at = datetime("merge_test_at").clientDefault { TEST_DEFAULT_DATE_TIME }
    }

    object Dest : IntIdTable("merge_test_dest") {
        val key = varchar("merge_test_key", 128)
        val value = integer("merge_test_value")
        val optional = text("merge_test_optional_value").nullable()
        val at = datetime("merge_test_at").clientDefault { TEST_DEFAULT_DATE_TIME }

        fun getByKey(key: String) = Dest.selectAll().where { Dest.key eq key }.single()
        fun getByKeyOrNull(key: String) = Dest.selectAll().where { Dest.key eq key }.singleOrNull()
    }

    object Insert {
        fun source(key: String, value: Int, optional: String? = null, at: LocalDateTime? = null) {
            Source.insert {
                it[Source.key] = key
                it[Source.value] = value
                optional?.let { optional -> it[Source.optional] = optional }
                at?.let { at -> it[Source.at] = at }
            }
        }

        fun dest(key: String, value: Int, optional: String? = null, at: LocalDateTime? = null) {
            Dest.insert {
                it[Dest.key] = key
                it[Dest.value] = value
                optional?.let { optional -> it[Source.optional] = optional }
                at?.let { at -> it[Source.at] = at }
            }
        }
    }
}
