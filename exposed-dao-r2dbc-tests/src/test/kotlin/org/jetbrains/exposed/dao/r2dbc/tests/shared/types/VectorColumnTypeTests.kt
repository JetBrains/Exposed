package org.jetbrains.exposed.dao.r2dbc.tests.shared.types

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertTrue

class VectorColumnTypeTests : R2dbcDatabaseTestsBase() {
    private val vectorTypeSupportedDb = setOf(TestDB.ORACLE, TestDB.MARIADB, TestDB.POSTGRESQL, TestDB.SQLSERVER)

    object VectorEntityTable : IntIdTable("vector_tester") {
        val embedding = vector("embedding", dimensions = 5)
    }

    class VectorEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<VectorEntity>(VectorEntityTable)

        var embedding by VectorEntityTable.embedding
    }

    @Test
    fun testVectorTypeWithDAO() {
        withDb(vectorTypeSupportedDb) { testDb ->
            try {
                if (testDb == TestDB.POSTGRESQL) {
                    exec("CREATE EXTENSION IF NOT EXISTS vector;")
                }
                SchemaUtils.create(VectorEntityTable)

                val ve = VectorEntity.new {
                    embedding = floatArrayOf(0f, 1f, 0f, 0f, 0f)
                }.flush()

                val inserted = VectorEntity.all().single()
                assertEquals(ve.embedding, inserted.embedding)

                ve.embedding = floatArrayOf(1f, 0f, 0f, 0f, 0f)
                ve.flush()

                val updated = VectorEntity.all().single().embedding
                assertTargetWithinTolerance(updated)
            } finally {
                SchemaUtils.drop(VectorEntityTable)
                if (testDb == TestDB.POSTGRESQL) {
                    exec("DROP EXTENSION IF EXISTS vector CASCADE;")
                }
            }
        }
    }

    private fun assertTargetWithinTolerance(actual: FloatArray, target: FloatArray = floatArrayOf(1f, 0f, 0f), tolerance: Double = 1e-6) {
        assertTrue(
            abs(actual[0] - target[0]) < tolerance &&
                abs(actual[1] - target[1]) < tolerance &&
                abs(actual[2] - target[2]) < tolerance
        )
    }
}
