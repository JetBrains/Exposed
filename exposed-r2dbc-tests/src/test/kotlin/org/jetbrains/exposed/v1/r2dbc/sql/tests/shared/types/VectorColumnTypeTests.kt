package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.types

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertFailAndRollback
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorColumnTypeTests : R2dbcDatabaseTestsBase() {
    // Only MySQL9+ supports VECTOR, so it is excluded until we add this version to tests;
    // Tested locally on Docker image mysql:9.7;
    // mssql-r2dbc not set up with VECTOR type mapping.
    private val vectorTypeSupportedDb = setOf(TestDB.ORACLE, TestDB.MARIADB, TestDB.POSTGRESQL)
    // When MySQL9 is added, it will need to be excluded from all VECTOR_DISTANCE tests, because this function
    // is available only for users of HeatWave MySQL on OCI; it is not included in MySQL Commercial or Community distributions.

    object VectorItems : Table("vector_items") {
        val id = integer("id")
        val embedding = vector("embedding", dimensions = 3)
        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun testVectorInsertAndSelect() {
        withVectorTableAndData { vectorItems, _, _ ->
            val result = vectorItems.selectAll().map { it[vectorItems.embedding] }.first()
            assertTargetWithinTolerance(result)
        }
    }

    @Test
    fun testCosineDistanceOrdering() {
        withVectorTableAndData { vectorItems, targetArray, _ ->
            val distance = vectorItems.embedding.cosineDistance(targetArray)
            val rows = vectorItems.select(vectorItems.id, distance).orderBy(distance to SortOrder.ASC).toList()
            val nearestId = rows.first()[vectorItems.id]
            val nearestDistance = rows.first()[distance]
            val secondDistance = rows.last()[distance]

            assertEquals(1, nearestId)
            assertDistanceEquals(0.0, nearestDistance)
            assertDistanceEquals(1.0, secondDistance)
        }
    }

    @Test
    fun testEuclideanDistanceOrdering() {
        withVectorTableAndData { vectorItems, targetArray, _ ->
            val distance = vectorItems.embedding.euclideanDistance(targetArray)
            val rows = vectorItems.select(vectorItems.id, distance).orderBy(distance to SortOrder.ASC).toList()
            val nearestId = rows.first()[vectorItems.id]
            val nearestDistance = rows.first()[distance]
            val secondDistance = rows.last()[distance]

            assertEquals(1, nearestId)
            assertDistanceEquals(0.0, nearestDistance)
            assertDistanceEquals(sqrt(2.0), secondDistance)
        }
    }

    @Test
    fun testDotDistanceOrdering() {
        // MARIADB does not support DOT/INNER_PRODUCT distance functions
        withVectorTableAndData(exclude = setOf(TestDB.MARIADB)) { vectorItems, targetArray, _ ->
            val distance = vectorItems.embedding.dotDistance(targetArray)
            val rows = vectorItems.select(vectorItems.id, distance).orderBy(distance to SortOrder.ASC).toList()
            val nearestId = rows.first()[vectorItems.id]
            val nearestDistance = rows.first()[distance]
            val secondDistance = rows.last()[distance]

            assertEquals(1, nearestId)
            assertDistanceEquals(-1.0, nearestDistance)
            assertDistanceEquals(0.0, secondDistance)
        }
    }

    @Test
    fun testDistanceAsPartOfOpClause() {
        withVectorTableAndData { vectorItems, targetArray, _ ->
            vectorItems.insert {
                it[id] = 3
                it[embedding] = floatArrayOf(4f, 0f, 0f)
            }
            vectorItems.insert {
                it[id] = 4
                it[embedding] = floatArrayOf(9f, 0f, 0f) // will be well outside threshold
            }

            val result1 = vectorItems.select(vectorItems.id)
                .where { vectorItems.embedding.euclideanDistance(targetArray) less 5.0 }
                .orderBy(vectorItems.id to SortOrder.ASC)
                .map { it[vectorItems.id] }
            assertEqualCollections(result1, listOf(1, 2, 3))

            val result2 = vectorItems.select(vectorItems.id)
                .where {
                    vectorItems.embedding.euclideanDistance(vectorParam(targetArray)) greater 5.0
                }
                .orderBy(vectorItems.id to SortOrder.ASC)
                .map { it[vectorItems.id] }
            assertEqualCollections(result2, listOf(4))
        }
    }

    @Test
    fun testDistanceTieBreakWithSecondaryOrderById() {
        withVectorTableAndData(
            secondArray = floatArrayOf(1f, 0f, 0f),
        ) { vectorItems, targetArray, _ ->
            val distance = vectorItems.embedding.cosineDistance(targetArray)
            val rows = vectorItems.select(vectorItems.id, distance)
                .orderBy(distance to SortOrder.ASC, vectorItems.id to SortOrder.ASC)
                .toList()

            assertEqualCollections(rows.map { it[vectorItems.id] }, listOf(1, 2))
            assertDistanceEquals(0.0, rows[0][distance])
            assertDistanceEquals(0.0, rows[1][distance])
        }
    }

    @Test
    fun testDistanceWithNegativeAndNonUnitValues() {
        withVectorTableAndData(
            firstArray = floatArrayOf(-2f, 3f, 0.5f),
            secondArray = floatArrayOf(4f, -1f, 2f),
        ) { vectorItems, targetArray, _ ->
            val distance = vectorItems.embedding.euclideanDistance(targetArray)
            val rows = vectorItems.select(vectorItems.id, distance).orderBy(distance to SortOrder.ASC).toList()

            assertEquals(1, rows.first()[vectorItems.id])
            assertDistanceEquals(0.0, rows.first()[distance])
            assertDistanceEquals(sqrt(54.25), rows.last()[distance], tolerance = 1e-4)
        }
    }

    @Test
    fun testDimensionMismatchThrows() {
        withVectorTableAndData { vectorItems, _, testDb ->
            val invalidTargetArray = floatArrayOf(1f, 0f)

            if (testDb != TestDB.MARIADB) { // MariaDB only restricts on insert
                assertFailAndRollback("Incorrect vector size") {
                    val distance = vectorItems.embedding.cosineDistance(invalidTargetArray)
                    vectorItems.select(vectorItems.id, distance).toList()
                }
            }

            assertFailAndRollback("Incorrect vector size") {
                vectorItems.insert {
                    it[id] = 3
                    it[embedding] = invalidTargetArray
                }
            }
        }
    }

    @Test
    fun testNullableVectorRoundTrip() {
        val nullTester = object : Table("nullable_vector_items") {
            val id = integer("id")
            val embedding = vector("embedding", dimensions = 3).nullable()
            override val primaryKey = PrimaryKey(id)
        }

        withDb(vectorTypeSupportedDb) { testDb ->
            try {
                if (testDb == TestDB.POSTGRESQL) {
                    exec("CREATE EXTENSION IF NOT EXISTS vector;")
                }
                SchemaUtils.create(nullTester)
                nullTester.insert {
                    it[id] = 1
                    it[embedding] = null
                }
                nullTester.insert {
                    it[id] = 2
                    it[embedding] = floatArrayOf(1f, 0f, 0f)
                }
                nullTester.insert {
                    it[id] = 3
                }

                val result = nullTester.selectAll()
                    .where { nullTester.embedding.isNull() }
                    .orderBy(nullTester.id to SortOrder.ASC)
                    .toList()
                assertEqualCollections(result.map { it[nullTester.id] }, listOf(1, 3))
                assertNull(result.first()[nullTester.embedding])
                assertNull(result.last()[nullTester.embedding])
            } finally {
                SchemaUtils.drop(nullTester)
                if (testDb == TestDB.POSTGRESQL) {
                    exec("DROP EXTENSION IF EXISTS vector CASCADE;")
                }
            }
        }
    }

    @Test
    fun testDefaultVectorRoundTrip() {
        val targetArray = floatArrayOf(1f, 0f, 0f, 0f)
        val defaultTester = object : Table("default_vector_items") {
            val id = integer("id")
            val embedding1 = vector("embedding_1", 4).default(targetArray)
            val embedding2 = vector("embedding_2", 4).defaultExpression(vectorLiteral(targetArray))
            override val primaryKey = PrimaryKey(id)
        }

        withDb(vectorTypeSupportedDb) { testDb ->
            // MariaDB & PostgreSQL are the only db without a restriction against default Vector constraints
            if (testDb != TestDB.MARIADB && testDb != TestDB.POSTGRESQL) {
                assertFailAndRollback("Default constraint not supported") {
                    SchemaUtils.create(defaultTester)
                }
            } else {
                try {
                    if (testDb == TestDB.POSTGRESQL) {
                        exec("CREATE EXTENSION IF NOT EXISTS vector;")
                    }
                    SchemaUtils.create(defaultTester)
                    defaultTester.insert { it[id] = 1 }
                    val result = defaultTester.selectAll().single()
                    assertContentEquals(targetArray, result[defaultTester.embedding1])
                    assertContentEquals(targetArray, result[defaultTester.embedding2])
                } finally {
                    if (testDb == TestDB.POSTGRESQL) {
                        exec("DROP EXTENSION IF EXISTS vector CASCADE;")
                    }
                    SchemaUtils.drop(defaultTester)
                }
            }
        }
    }

    @Test
    fun testMultiRowRankingOrder() {
        withVectorTableAndData(
            secondArray = floatArrayOf(0.8f, 0.2f, 0f),
        ) { vectorItems, targetArray, _ ->
            vectorItems.insert {
                it[id] = 3
                it[embedding] = floatArrayOf(0f, 1f, 0f)
            }
            vectorItems.insert {
                it[id] = 4
                it[embedding] = floatArrayOf(-1f, 0f, 0f)
            }

            val distance = vectorItems.embedding.euclideanDistance(targetArray)
            val ids = vectorItems.select(vectorItems.id, distance).orderBy(distance to SortOrder.ASC).map { it[vectorItems.id] }

            assertEqualCollections(ids, listOf(1, 2, 3, 4))
        }
    }

    @Test
    fun testPrecisionToleranceBoundary() {
        withVectorTableAndData(
            firstArray = floatArrayOf(1f, 1f, 1f),
            secondArray = floatArrayOf(1f, 1f, 1.0001f),
        ) { vectorItems, targetArray, _ ->
            val distance = vectorItems.embedding.euclideanDistance(targetArray)
            val rows = vectorItems.select(vectorItems.id, distance).orderBy(distance to SortOrder.ASC).toList()

            assertEquals(1, rows.first()[vectorItems.id])
            assertDistanceEquals(0.0, rows.first()[distance], tolerance = 1e-6)
            assertDistanceEquals(0.0001, rows.last()[distance], tolerance = 5e-5)
        }
    }

    @Test
    fun testVectorUpdateRoundTripAndDistance() {
        withDb(vectorTypeSupportedDb) { testDb ->
            try {
                if (testDb == TestDB.POSTGRESQL) {
                    exec("CREATE EXTENSION IF NOT EXISTS vector;")
                }
                SchemaUtils.create(VectorItems)
                VectorItems.insert {
                    it[id] = 1
                    it[embedding] = floatArrayOf(0f, 1f, 0f)
                }
                VectorItems.update(where = { VectorItems.id eq 1 }) {
                    it[embedding] = floatArrayOf(1f, 0f, 0f)
                }

                val updated = VectorItems.selectAll().single()[VectorItems.embedding]
                assertTargetWithinTolerance(updated)

                val distance = VectorItems.embedding.cosineDistance(floatArrayOf(1f, 0f, 0f))
                val actualDistance = VectorItems.select(distance).single()[distance]
                assertDistanceEquals(0.0, actualDistance)
            } finally {
                SchemaUtils.drop(VectorItems)
                if (testDb == TestDB.POSTGRESQL) {
                    exec("DROP EXTENSION IF EXISTS vector CASCADE;")
                }
            }
        }
    }

    private fun withVectorTableAndData(
        exclude: Set<TestDB> = emptySet(),
        firstArray: FloatArray = floatArrayOf(1f, 0f, 0f),
        secondArray: FloatArray = floatArrayOf(0f, 1f, 0f),
        statement: suspend R2dbcTransaction.(vectorItems: VectorItems, targetArray: FloatArray, testDb: TestDB) -> Unit
    ) {
        withDb(vectorTypeSupportedDb, excludeSettings = exclude) { testDb ->
            try {
                if (testDb == TestDB.POSTGRESQL) {
                    exec("CREATE EXTENSION IF NOT EXISTS vector;")
                }
                SchemaUtils.create(VectorItems)
                VectorItems.insert {
                    it[id] = 1
                    it[embedding] = firstArray
                }
                VectorItems.insert {
                    it[id] = 2
                    it[embedding] = secondArray
                }
                statement(VectorItems, firstArray, testDb)
            } finally {
                SchemaUtils.drop(VectorItems)
                if (testDb == TestDB.POSTGRESQL) {
                    exec("DROP EXTENSION IF EXISTS vector CASCADE;")
                }
            }
        }
    }

    private fun assertDistanceEquals(expected: Double, actual: Double, tolerance: Double = 1e-6) {
        assertTrue(abs(expected - actual) < tolerance, "Expected $expected, got $actual")
    }

    private fun assertTargetWithinTolerance(actual: FloatArray, target: FloatArray = floatArrayOf(1f, 0f, 0f), tolerance: Double = 1e-6) {
        assertTrue(
            abs(actual[0] - target[0]) < tolerance &&
                abs(actual[1] - target[1]) < tolerance &&
                abs(actual[2] - target[2]) < tolerance
        )
    }
}
