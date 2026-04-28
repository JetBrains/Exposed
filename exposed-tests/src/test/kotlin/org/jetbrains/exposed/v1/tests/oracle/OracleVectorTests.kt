package org.jetbrains.exposed.v1.tests.oracle

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.cosineDistance
import org.jetbrains.exposed.v1.core.vendors.dotDistance
import org.jetbrains.exposed.v1.core.vendors.euclideanDistance
import org.jetbrains.exposed.v1.core.vendors.vector
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.expectException
import org.junit.jupiter.api.Test
import java.sql.SQLException
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OracleVectorTests : DatabaseTestsBase() {
    private fun assertDistanceEquals(expected: Double, actual: Double, tolerance: Double = 1e-6) {
        assertTrue(abs(expected - actual) < tolerance, "Expected $expected, got $actual")
    }

    object VectorItems : Table("vector_items") {
        val id = integer("id")
        val embedding = vector("embedding", dimensions = 3)
        override val primaryKey = PrimaryKey(id)
    }

    object NullableVectorItems : Table("nullable_vector_items") {
        val id = integer("id")
        val embedding = vector("embedding", dimensions = 3).nullable()
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

    @Test
    fun testCosineDistanceOrdering() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ORACLE, VectorItems) {
            VectorItems.insert {
                it[id] = 1
                it[embedding] = floatArrayOf(1f, 0f, 0f)
            }
            VectorItems.insert {
                it[id] = 2
                it[embedding] = floatArrayOf(0f, 1f, 0f)
            }

            val distance = VectorItems.embedding.cosineDistance(floatArrayOf(1f, 0f, 0f))
            val rows = VectorItems.select(VectorItems.id, distance).orderBy(distance to SortOrder.ASC).toList()
            val nearestId = rows.first()[VectorItems.id]
            val nearestDistance = rows.first()[distance]
            val secondDistance = rows.last()[distance]

            assertEquals(1, nearestId)
            assertDistanceEquals(0.0, nearestDistance)
            assertDistanceEquals(1.0, secondDistance)
        }
    }

    @Test
    fun testEuclideanDistanceOrdering() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ORACLE, VectorItems) {
            VectorItems.insert {
                it[id] = 1
                it[embedding] = floatArrayOf(1f, 0f, 0f)
            }
            VectorItems.insert {
                it[id] = 2
                it[embedding] = floatArrayOf(0f, 1f, 0f)
            }

            val distance = VectorItems.embedding.euclideanDistance(floatArrayOf(1f, 0f, 0f))
            val rows = VectorItems.select(VectorItems.id, distance).orderBy(distance to SortOrder.ASC).toList()
            val nearestId = rows.first()[VectorItems.id]
            val nearestDistance = rows.first()[distance]
            val secondDistance = rows.last()[distance]

            assertEquals(1, nearestId)
            assertDistanceEquals(0.0, nearestDistance)
            assertDistanceEquals(sqrt(2.0), secondDistance)
        }
    }

    @Test
    fun testDotDistanceOrdering() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ORACLE, VectorItems) {
            VectorItems.insert {
                it[id] = 1
                it[embedding] = floatArrayOf(1f, 0f, 0f)
            }
            VectorItems.insert {
                it[id] = 2
                it[embedding] = floatArrayOf(0f, 1f, 0f)
            }

            val distance = VectorItems.embedding.dotDistance(floatArrayOf(1f, 0f, 0f))
            val rows = VectorItems.select(VectorItems.id, distance).orderBy(distance to SortOrder.ASC).toList()
            val nearestId = rows.first()[VectorItems.id]
            val nearestDistance = rows.first()[distance]
            val secondDistance = rows.last()[distance]

            assertEquals(1, nearestId)
            assertDistanceEquals(-1.0, nearestDistance)
            assertDistanceEquals(0.0, secondDistance)
        }
    }

    @Test
    fun testDistanceTieBreakWithSecondaryOrderById() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ORACLE, VectorItems) {
            VectorItems.insert { it[id] = 1; it[embedding] = floatArrayOf(1f, 0f, 0f) }
            VectorItems.insert { it[id] = 2; it[embedding] = floatArrayOf(1f, 0f, 0f) }

            val distance = VectorItems.embedding.cosineDistance(floatArrayOf(1f, 0f, 0f))
            val rows = VectorItems.select(VectorItems.id, distance)
                .orderBy(distance to SortOrder.ASC, VectorItems.id to SortOrder.ASC)
                .toList()

            assertEquals(listOf(1, 2), rows.map { it[VectorItems.id] })
            assertDistanceEquals(0.0, rows[0][distance])
            assertDistanceEquals(0.0, rows[1][distance])
        }
    }

    @Test
    fun testDistanceWithNegativeAndNonUnitValues() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ORACLE, VectorItems) {
            VectorItems.insert { it[id] = 1; it[embedding] = floatArrayOf(-2f, 3f, 0.5f) }
            VectorItems.insert { it[id] = 2; it[embedding] = floatArrayOf(4f, -1f, 2f) }

            val target = floatArrayOf(-2f, 3f, 0.5f)
            val distance = VectorItems.embedding.euclideanDistance(target)
            val rows = VectorItems.select(VectorItems.id, distance).orderBy(distance to SortOrder.ASC).toList()

            assertEquals(1, rows.first()[VectorItems.id])
            assertDistanceEquals(0.0, rows.first()[distance])
            assertDistanceEquals(sqrt(54.25), rows.last()[distance], tolerance = 1e-4)
        }
    }

    @Test
    fun testDimensionMismatchThrows() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ORACLE, VectorItems) {
            VectorItems.insert { it[id] = 1; it[embedding] = floatArrayOf(1f, 0f, 0f) }

            expectException<SQLException> {
                val distance = VectorItems.embedding.cosineDistance(floatArrayOf(1f, 0f))
                VectorItems.select(VectorItems.id, distance).toList()
            }
        }
    }

    @Test
    fun testNullableVectorRoundTrip() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ORACLE, NullableVectorItems) {
            NullableVectorItems.insert { it[id] = 1; it[embedding] = null }
            NullableVectorItems.insert { it[id] = 2; it[embedding] = floatArrayOf(1f, 0f, 0f) }

            expectException<SQLException> {
                NullableVectorItems.selectAll().orderBy(NullableVectorItems.id to SortOrder.ASC).toList()
            }
        }
    }

    @Test
    fun testDistanceSqlContainsMetricName() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ORACLE, VectorItems) {
            val cosine = VectorItems.embedding.cosineDistance(floatArrayOf(1f, 0f, 0f))
            val euclidean = VectorItems.embedding.euclideanDistance(floatArrayOf(1f, 0f, 0f))
            val dot = VectorItems.embedding.dotDistance(floatArrayOf(1f, 0f, 0f))

            val cosineSql = VectorItems.select(VectorItems.id, cosine).prepareSQL(this, prepared = false)
            val euclideanSql = VectorItems.select(VectorItems.id, euclidean).prepareSQL(this, prepared = false)
            val dotSql = VectorItems.select(VectorItems.id, dot).prepareSQL(this, prepared = false)

            assertTrue(cosineSql.contains("VECTOR_DISTANCE(") && cosineSql.contains("COSINE"))
            assertTrue(euclideanSql.contains("VECTOR_DISTANCE(") && euclideanSql.contains("EUCLIDEAN"))
            assertTrue(dotSql.contains("VECTOR_DISTANCE(") && dotSql.contains("DOT"))
        }
    }

    @Test
    fun testMultiRowRankingOrder() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ORACLE, VectorItems) {
            VectorItems.insert { it[id] = 1; it[embedding] = floatArrayOf(1f, 0f, 0f) }
            VectorItems.insert { it[id] = 2; it[embedding] = floatArrayOf(0.8f, 0.2f, 0f) }
            VectorItems.insert { it[id] = 3; it[embedding] = floatArrayOf(0f, 1f, 0f) }
            VectorItems.insert { it[id] = 4; it[embedding] = floatArrayOf(-1f, 0f, 0f) }

            val distance = VectorItems.embedding.euclideanDistance(floatArrayOf(1f, 0f, 0f))
            val ids = VectorItems.select(VectorItems.id, distance).orderBy(distance to SortOrder.ASC).map { it[VectorItems.id] }

            assertEquals(listOf(1, 2, 3, 4), ids)
        }
    }

    @Test
    fun testPrecisionToleranceBoundary() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ORACLE, VectorItems) {
            VectorItems.insert { it[id] = 1; it[embedding] = floatArrayOf(1f, 1f, 1f) }
            VectorItems.insert { it[id] = 2; it[embedding] = floatArrayOf(1f, 1f, 1.0001f) }

            val distance = VectorItems.embedding.euclideanDistance(floatArrayOf(1f, 1f, 1f))
            val rows = VectorItems.select(VectorItems.id, distance).orderBy(distance to SortOrder.ASC).toList()

            assertEquals(1, rows.first()[VectorItems.id])
            assertDistanceEquals(0.0, rows.first()[distance], tolerance = 1e-6)
            assertDistanceEquals(0.0001, rows.last()[distance], tolerance = 5e-5)
        }
    }

    @Test
    fun testVectorUpdateRoundTripAndDistance() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ORACLE, VectorItems) {
            VectorItems.insert { it[id] = 1; it[embedding] = floatArrayOf(0f, 1f, 0f) }
            VectorItems.update({ VectorItems.id eq 1 }) { it[embedding] = floatArrayOf(1f, 0f, 0f) }

            val updated = VectorItems.selectAll().single()[VectorItems.embedding]
            val distance = VectorItems.embedding.cosineDistance(floatArrayOf(1f, 0f, 0f))
            val actualDistance = VectorItems.select(distance).single()[distance]

            assertTrue(abs(updated[0] - 1f) < 1e-6f && abs(updated[1]) < 1e-6f && abs(updated[2]) < 1e-6f)
            assertDistanceEquals(0.0, actualDistance)
        }
    }
}
