package org.jetbrains.exposed.v1.tests.oracle

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.vector
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OracleVectorTests : DatabaseTestsBase() {
    object VectorItems : Table("vector_items") {
        val id = integer("id")
        val embedding = vector("embedding", dimensions = 3)

        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun testVectorRoundTripWithoutConnectionProperty() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ORACLE, VectorItems) {
            val expected = floatArrayOf(0.9f, 0.1f, 0.3f)

            VectorItems.insert {
                it[id] = 1
                it[embedding] = expected
            }

            val row = VectorItems.selectAll().single()
            val actual = row[VectorItems.embedding]

            assertEquals(expected.size, actual.size)
            assertTrue(expected.indices.all { i -> abs(expected[i] - actual[i]) < 1e-6f })
        }
    }
}
