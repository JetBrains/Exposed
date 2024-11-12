package org.jetbrains.exposed.sql.tests.shared.dml

import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.JdbcTransaction

val TEST_DEFAULT_DATE_TIME = LocalDateTime(2000, 1, 1, 0, 0, 0, 0)

abstract class MergeBaseTest : DatabaseTestsBase() {
    protected fun allDbExcept(includeSettings: Collection<TestDB>) = TestDB.ALL - includeSettings.toSet()

    protected val defaultExcludeSettings = TestDB.ALL_MARIADB + TestDB.ALL_MYSQL + TestDB.SQLITE + TestDB.ALL_H2_V1

    protected fun withMergeTestTables(
        excludeSettings: Collection<TestDB> = emptyList(),
        statement: JdbcTransaction.(dest: Dest, source: Source) -> Unit
    ) = withTables(
        excludeSettings = defaultExcludeSettings + excludeSettings, Source, Dest
    ) {
        statement(Dest, Source)
    }

    protected fun withMergeTestTablesAndDefaultData(
        excludeSettings: Collection<TestDB> = emptyList(),
        statement: JdbcTransaction.(dest: Dest, source: Source) -> Unit
    ) {
        withMergeTestTables(excludeSettings) { dest, source ->
            source.insert(key = "only-in-source-1", value = 1)
            source.insert(key = "only-in-source-2", value = 2)
            source.insert(key = "only-in-source-3", value = 3, optional = "optional-is-present")
            source.insert(key = "only-in-source-4", value = 4, at = LocalDateTime(2050, 1, 1, 0, 0, 0, 0))

            dest.insert(key = "only-in-dest-1", value = 10)
            dest.insert(key = "only-in-dest-2", value = 20)
            dest.insert(key = "only-in-dest-3", value = 30, optional = "optional-is-present")
            dest.insert(key = "only-in-dest-4", value = 40, at = LocalDateTime(2050, 1, 1, 0, 0, 0, 0))

            source.insert(key = "in-source-and-dest-1", value = 1)
            dest.insert(key = "in-source-and-dest-1", value = 10)
            source.insert(key = "in-source-and-dest-2", value = 2)
            dest.insert(key = "in-source-and-dest-2", value = 20)
            source.insert(key = "in-source-and-dest-3", value = 3, optional = "optional-is-present")
            dest.insert(key = "in-source-and-dest-3", value = 30, optional = "optional-is-present")
            source.insert(key = "in-source-and-dest-4", value = 4, at = LocalDateTime(1950, 1, 1, 0, 0, 0, 0))
            dest.insert(key = "in-source-and-dest-4", value = 40, at = LocalDateTime(1950, 1, 1, 0, 0, 0, 0))

            statement(Dest, Source)
        }
    }

    object Source : IntIdTable("merge_test_source") {
        val key = varchar("merge_test_key", 128)
        val value = integer("merge_test_value")
        val optional = text("merge_test_optional_value").nullable()
        val at = datetime("merge_test_at").clientDefault { TEST_DEFAULT_DATE_TIME }

        fun insert(key: String, value: Int, optional: String? = null, at: LocalDateTime? = null) {
            Source.insert {
                it[Source.key] = key
                it[Source.value] = value
                optional?.let { optional -> it[Source.optional] = optional }
                at?.let { at -> it[Source.at] = at }
            }
        }
    }

    object Dest : IntIdTable("merge_test_dest") {
        val key = varchar("merge_test_key", 128)
        val value = integer("merge_test_value")
        val optional = text("merge_test_optional_value").nullable()
        val at = datetime("merge_test_at").clientDefault { TEST_DEFAULT_DATE_TIME }

        fun insert(key: String, value: Int, optional: String? = null, at: LocalDateTime? = null) {
            Dest.insert {
                it[Dest.key] = key
                it[Dest.value] = value
                optional?.let { optional -> it[Dest.optional] = optional }
                at?.let { at -> it[Dest.at] = at }
            }
        }

        fun getByKey(key: String) = Dest.selectAll().where { Dest.key eq key }.single()
        fun getByKeyOrNull(key: String) = Dest.selectAll().where { Dest.key eq key }.singleOrNull()
    }
}
