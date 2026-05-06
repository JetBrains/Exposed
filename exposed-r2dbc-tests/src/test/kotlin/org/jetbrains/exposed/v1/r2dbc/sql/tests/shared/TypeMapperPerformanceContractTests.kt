package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.mappers.ArrayTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.BinaryTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.DateTimeMySqlTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.DateTimeOracleTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.ExposedColumnTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.PostgresSpecificTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.PrimitiveTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcRegistryTypeMappingImpl
import org.jetbrains.exposed.v1.r2dbc.mappers.TypeMapper
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Regression tests for the type-mapper performance fixes shipped with EXPOSED-1003.
 */
class TypeMapperPerformanceContractTests {

    @Test
    fun testGetMatchingMappersIsCachedPerDialectAndColumnType() {
        val countingMapper = object : TypeMapper {
            var dialectsAccessCount = 0
            var columnTypesAccessCount = 0
            override val dialects: List<KClass<out DatabaseDialect>>
                get() {
                    dialectsAccessCount++
                    return emptyList()
                }
            override val columnTypes: List<KClass<out IColumnType<*>>>
                get() {
                    columnTypesAccessCount++
                    return emptyList()
                }
        }
        val registry = R2dbcRegistryTypeMappingImpl().apply { register(countingMapper) }
        val dialect = H2Dialect()
        val columnType = IntegerColumnType()
        val row = ConstantRow(42)

        repeat(100) { registry.getValue<Any?>(row, null, 1, dialect, columnType) }

        assertEquals(
            1,
            countingMapper.dialectsAccessCount,
            "TypeMapper.dialects must be read at most once per (dialect, columnType) pair; " +
                "without the matchingMappersCache it would be read 100 times.",
        )
        assertEquals(
            1,
            countingMapper.columnTypesAccessCount,
            "TypeMapper.columnTypes must be read at most once per (dialect, columnType) pair; " +
                "without the matchingMappersCache it would be read 100 times.",
        )
    }

    @Test
    fun testColumnTypesAreBackingFieldVal() {
        assertColumnTypesIsBackingVal(PrimitiveTypeMapper())
        assertColumnTypesIsBackingVal(BinaryTypeMapper())
        assertColumnTypesIsBackingVal(ArrayTypeMapper())
        assertColumnTypesIsBackingVal(ExposedColumnTypeMapper())
    }

    @Test
    fun testDialectsAreBackingFieldVal() {
        assertDialectsIsBackingVal(PostgresSpecificTypeMapper())
        assertDialectsIsBackingVal(ArrayTypeMapper())
        assertDialectsIsBackingVal(DateTimeMySqlTypeMapper())
        assertDialectsIsBackingVal(DateTimeOracleTypeMapper())
    }

    private fun assertColumnTypesIsBackingVal(mapper: TypeMapper) {
        val first = mapper.columnTypes
        val second = mapper.columnTypes
        assertNotNull(first)
        assertSame(
            first,
            second,
            "${mapper::class.simpleName}.columnTypes must be a backing-field `val`; " +
                "a `get() = listOf(...)` accessor allocates a fresh KClass[] on every call, " +
                "which is read once per mapper resolution.",
        )
    }

    private fun assertDialectsIsBackingVal(mapper: TypeMapper) {
        val first = mapper.dialects
        val second = mapper.dialects
        assertNotNull(first)
        assertSame(
            first,
            second,
            "${mapper::class.simpleName}.dialects must be a backing-field `val`; " +
                "a `get() = listOf(...)` accessor allocates a fresh KClass[] on every call.",
        )
    }

    private class ConstantRow(private val value: Any?) : Row {
        override fun getMetadata(): RowMetadata = error("metadata not used in this test")
        override fun get(index: Int): Any? = value
        override fun get(name: String): Any? = value

        @Suppress("UNCHECKED_CAST")
        override fun <T> get(index: Int, type: Class<T>): T? = value as T?

        @Suppress("UNCHECKED_CAST")
        override fun <T> get(name: String, type: Class<T>): T? = value as T?
    }
}
