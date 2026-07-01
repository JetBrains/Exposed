package org.jetbrains.exposed.v1.tests

import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.statements.api.RowApi
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit tests for [ResultRow] cache behaviour.
 *
 * Most tests run without a database connection by constructing rows via
 * [ResultRow.createAndFillValues]; the shared-column-type tests use the
 * [ResultRow.create] factory with a stub [RowApi].
 */
@Tag(MISSING_R2DBC_TEST)
class ResultRowCacheTest {

    // ---------------------------------------------------------------------------
    // Shared fixtures
    // ---------------------------------------------------------------------------

    private object Users : IntIdTable("users") {
        val name = varchar("name", 50)
    }

    private object NullableTable : Table("nullable_t") {
        val value = integer("v").nullable()
    }

    // ---------------------------------------------------------------------------
    // Basic read / write semantics
    // ---------------------------------------------------------------------------

    @Test
    fun `reading a column returns the stored value`() {
        val row = ResultRow.createAndFillValues(mapOf(Users.name to "Alice"))
        assertEquals("Alice", row[Users.name])
    }

    @Test
    fun `repeated reads return the same cached value`() {
        val row = ResultRow.createAndFillValues(mapOf(Users.name to "Alice"))
        val first = row[Users.name]
        val second = row[Users.name]
        assertEquals(first, second)
        // Both reads must return identical references (cached object, not recomputed)
        assertTrue(first === second, "Expected cache hit to return the same instance")
    }

    @Test
    fun `null column value is cached correctly and not confused with sentinel`() {
        val row = ResultRow.createAndFillValues(mapOf(NullableTable.value to null))
        assertNull(row[NullableTable.value])
        // Second read must also be null (not UNCACHED sentinel leaking through)
        assertNull(row[NullableTable.value])
    }

    @Test
    fun `set invalidates cached value and subsequent read returns new value`() {
        val row = ResultRow.createAndFillValues(mapOf(Users.name to "Alice"))
        assertEquals("Alice", row[Users.name])
        row[Users.name] = "Bob"
        assertEquals("Bob", row[Users.name])
    }

    @Test
    fun `set then read then set again stays correct across multiple mutations`() {
        val row = ResultRow.createAndFillValues(mapOf(Users.name to "Alice"))
        row[Users.name] = "Bob"
        assertEquals("Bob", row[Users.name])
        row[Users.name] = "Carol"
        assertEquals("Carol", row[Users.name])
    }

    // ---------------------------------------------------------------------------
    // EntityID / raw-column dual-view semantics
    //
    // Column.equals() compares by (table, name) only, so the EntityID wrapper column
    // and the underlying raw column are "equals"-equal but carry different IColumnType
    // instances and must produce distinct converted values when cached.
    // ---------------------------------------------------------------------------

    @Test
    fun `EntityID column and its raw idColumn resolve to correct typed values independently`() {
        val entityIdCol = Users.id
        val rawIdCol = (entityIdCol.columnType as EntityIDColumnType<Int>).idColumn

        // Confirm the two columns are equals()-equal (same table + name) but NOT identical.
        assertTrue(
            entityIdCol == rawIdCol,
            "EntityID column and its idColumn must be equals()-equal for this test to be meaningful"
        )
        assertTrue(entityIdCol !== rawIdCol, "They must be different object instances")

        // Store the raw DB value (an Int, as it comes out of the JDBC layer before type conversion).
        val row = ResultRow.createAndFillValues(mapOf(entityIdCol to 42))

        // Accessing via the EntityID column must yield a wrapped EntityID<Int>.
        val entityIdValue = row[entityIdCol]
        assertEquals(42, entityIdValue.value)

        // Accessing via the raw column must yield a plain Int — not an EntityID.
        // This exercises the overflow path in ResultRowCache (type-view conflict for the same slot).
        val rawValue = row[rawIdCol]
        assertEquals(42, rawValue)
    }

    @Test
    fun `set via entityId column invalidates the primary slot but not other-type overflow views`() {
        val entityIdCol = Users.id
        val rawIdCol = (entityIdCol.columnType as EntityIDColumnType<Int>).idColumn

        val row = ResultRow.createAndFillValues(mapOf(entityIdCol to 7))

        // Prime both cache entries: the entityId view occupies the primary slot, the raw view spills
        // to the overflow map under (rawIdCol, IntegerColumnType).
        assertEquals(7, row[entityIdCol].value)
        assertEquals(7, row[rawIdCol])

        // Mutate via the entityId column. This calls remove(entityIdCol), which clears the primary slot
        // and removes the overflow key (entityIdCol, EntityIDColumnType) — but NOT the surviving
        // (rawIdCol, IntegerColumnType) overflow entry.
        val newId = EntityID(99, Users)
        row[entityIdCol] = newId

        // Primary slot was invalidated: the entityId view recomputes from the new stored value.
        assertEquals(99, row[entityIdCol].value)

        // The raw view's overflow entry was NOT invalidated, so it still returns the pre-set value.
        // This documents the known per-key invalidation limitation (see ResultRowCache.remove KDoc);
        // it is not a regression versus the original pre-cache behaviour.
        assertEquals(7, row[rawIdCol])
    }

    // ---------------------------------------------------------------------------
    // Multiple columns in the same row
    // ---------------------------------------------------------------------------

    @Test
    fun `independent columns are cached and retrieved independently`() {
        val row = ResultRow.createAndFillValues(
            mapOf(
                Users.id to 1,
                Users.name to "Dana"
            )
        )
        assertEquals(1, row[Users.id].value)
        assertEquals("Dana", row[Users.name])

        row[Users.name] = "Eve"
        // id cache must be unaffected by the name mutation.
        assertEquals(1, row[Users.id].value)
        assertEquals("Eve", row[Users.name])
    }

    // ---------------------------------------------------------------------------
    // Shared column-type array (the per-result-set optimization)
    //
    // The query iterators build the column-type array once via columnTypesOf and pass it to every
    // row's create(). These tests lock in that the shared path behaves identically to the per-row path.
    // ---------------------------------------------------------------------------

    /** Minimal [RowApi] that returns pre-set raw values by one-based column index. */
    private class StubRow(private val values: Array<Any?>) : RowApi {
        override fun getObject(index: Int): Any? = values[index - 1]
        override fun getObject(name: String): Any? = error("not used")
        override fun <T> getObject(index: Int, type: Class<T>): T? = error("not used")
        override fun <T> getObject(name: String, type: Class<T>): T? = error("not used")
        override fun getString(index: Int): String? = values[index - 1]?.toString()
    }

    @OptIn(InternalApi::class)
    @Test
    fun `columnTypesOf maps each slot to its projected column type`() {
        val fieldIndex = mapOf<Expression<*>, Int>(Users.id to 0, Users.name to 1)
        val types = ResultRow.columnTypesOf(fieldIndex)
        assertEquals(2, types.size)
        assertSame(Users.id.columnType, types[0])
        assertSame(Users.name.columnType, types[1])
    }

    @OptIn(InternalApi::class)
    @Test
    fun `shared column-type array yields the same typed values as the per-row path`() {
        val entityIdCol = Users.id
        val rawIdCol = (entityIdCol.columnType as EntityIDColumnType<Int>).idColumn
        val fieldIndex = mapOf<Expression<*>, Int>(entityIdCol to 0, Users.name to 1)

        // Build a row through the shared-types factory the query iterators use.
        val sharedTypes = ResultRow.columnTypesOf(fieldIndex)
        val row = ResultRow.create(StubRow(arrayOf<Any?>(42, "Alice")), fieldIndex, sharedTypes)

        // Canonical (projected) views hit the primary slot.
        assertEquals(42, row[entityIdCol].value)
        assertEquals("Alice", row[Users.name])
        // The raw idColumn is a non-canonical view of the same slot and must still resolve to a plain Int.
        assertEquals(42, row[rawIdCol])
    }
}
