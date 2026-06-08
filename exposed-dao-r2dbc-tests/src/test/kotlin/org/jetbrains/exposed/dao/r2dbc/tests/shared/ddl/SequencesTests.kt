package org.jetbrains.exposed.dao.r2dbc.tests.shared.ddl

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.r2dbc.dao.UuidEntity
import org.jetbrains.exposed.r2dbc.dao.UuidEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class SequencesTests : R2dbcDatabaseTestsBase() {
    object TesterTable : UuidTable("Tester") {
        val index = integer("index").autoIncrement()
        val name = text("name")
    }

    class TesterEntity(id: EntityID<Uuid>) : UuidEntity(id) {
        companion object : UuidEntityClass<TesterEntity>(TesterTable)

        var index by TesterTable.index
        var name by TesterTable.name
    }

    @Test
    fun testAutoIncrementColumnAccessWithEntity() = runTest {
        Assumptions.assumeTrue(TestDB.POSTGRESQL in TestDB.enabledDialects())

        TestDB.POSTGRESQL.connect()

        try {
            suspendTransaction {
                SchemaUtils.create(TesterTable)
            }

            val testerEntity = suspendTransaction {
                TesterEntity.new {
                    name = "test row"
                }.flush()
            }

            assertEquals(1, testerEntity.index)
        } finally {
            suspendTransaction {
                SchemaUtils.drop(TesterTable)
            }
        }
    }
}
