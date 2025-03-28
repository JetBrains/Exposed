package org.jetbrains.exposed.r2dbc.sql.tests.shared.dml

import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB

val TEST_DEFAULT_DATE_TIME = LocalDateTime(2000, 1, 1, 0, 0, 0, 0)

abstract class MergeBaseTest : R2dbcDatabaseTestsBase() {
    protected fun allDbExcept(includeSettings: Collection<TestDB>) = TestDB.ALL - includeSettings.toSet()

    protected val defaultExcludeSettings = TestDB.ALL_MARIADB + TestDB.ALL_MYSQL

    protected fun withMergeTestTables(
        excludeSettings: Collection<TestDB> = emptyList(),
        statement: suspend R2dbcTransaction.(dest: Dest, source: Source) -> Unit
    ) = withTables(
        excludeSettings = defaultExcludeSettings + excludeSettings, Source, Dest
    ) {
        statement(Dest, Source)
    }

    protected fun withMergeTestTablesAndDefaultData(
        excludeSettings: Collection<TestDB> = emptyList(),
        statement: suspend R2dbcTransaction.(dest: Dest, source: Source) -> Unit
    ) {
        withMergeTestTables(excludeSettings) { dest, source ->
            source.insertSource(key = "only-in-source-1", value = 1)
            source.insertSource(key = "only-in-source-2", value = 2)
            source.insertSource(key = "only-in-source-3", value = 3, optional = "optional-is-present")
            source.insertSource(key = "only-in-source-4", value = 4, at = LocalDateTime(2050, 1, 1, 0, 0, 0, 0))

            dest.insertDest(key = "only-in-dest-1", value = 10)
            dest.insertDest(key = "only-in-dest-2", value = 20)
            dest.insertDest(key = "only-in-dest-3", value = 30, optional = "optional-is-present")
            dest.insertDest(key = "only-in-dest-4", value = 40, at = LocalDateTime(2050, 1, 1, 0, 0, 0, 0))

            source.insertSource(key = "in-source-and-dest-1", value = 1)
            dest.insertDest(key = "in-source-and-dest-1", value = 10)
            source.insertSource(key = "in-source-and-dest-2", value = 2)
            dest.insertDest(key = "in-source-and-dest-2", value = 20)
            source.insertSource(key = "in-source-and-dest-3", value = 3, optional = "optional-is-present")
            dest.insertDest(key = "in-source-and-dest-3", value = 30, optional = "optional-is-present")
            source.insertSource(key = "in-source-and-dest-4", value = 4, at = LocalDateTime(1950, 1, 1, 0, 0, 0, 0))
            dest.insertDest(key = "in-source-and-dest-4", value = 40, at = LocalDateTime(1950, 1, 1, 0, 0, 0, 0))

            statement(Dest, Source)
        }
    }

    object Source : IntIdTable("merge_test_source") {
        val key = varchar("merge_test_key", 128)
        val value = integer("merge_test_value")
        val optional = text("merge_test_optional_value").nullable()
        val at = datetime("merge_test_at").clientDefault { TEST_DEFAULT_DATE_TIME }

        suspend fun insertSource(key: String, value: Int, optional: String? = null, at: LocalDateTime? = null) {
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

        suspend fun insertDest(key: String, value: Int, optional: String? = null, at: LocalDateTime? = null) {
            Dest.insert {
                it[Dest.key] = key
                it[Dest.value] = value
                optional?.let { optional -> it[Dest.optional] = optional }
                at?.let { at -> it[Dest.at] = at }
            }
        }

        suspend fun getByKey(key: String) = Dest.selectAll().where { Dest.key eq key }.single()
        suspend fun getByKeyOrNull(key: String) = Dest.selectAll().where { Dest.key eq key }.singleOrNull()
    }
}
