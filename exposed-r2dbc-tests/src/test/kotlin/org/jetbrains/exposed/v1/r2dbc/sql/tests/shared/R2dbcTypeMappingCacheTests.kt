package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.mappers.NoValueContainer
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcRegistryTypeMappingImpl
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcTypeMapping
import org.jetbrains.exposed.v1.r2dbc.mappers.TypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.ValueContainer
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class R2dbcTypeMappingCacheTests {

    /**
     * A test mapper that tracks how many times its [columnTypes] and [dialects] properties are accessed.
     */
    private class AccessCountingMapper : TypeMapper {
        var columnTypesAccessCount = 0
            private set
        var dialectsAccessCount = 0
            private set

        override val priority: Double get() = 1.0

        override val columnTypes: List<KClass<out IColumnType<*>>>
            get() {
                columnTypesAccessCount++
                return listOf(IntegerColumnType::class)
            }

        override val dialects: List<KClass<out DatabaseDialect>>
            get() {
                dialectsAccessCount++
                return listOf(H2Dialect::class)
            }

        override fun setValue(
            statement: Statement,
            dialect: DatabaseDialect,
            typeMapping: R2dbcTypeMapping,
            columnType: IColumnType<*>,
            value: Any?,
            index: Int
        ): Boolean = true

        override fun <T> getValue(
            row: Row,
            type: Class<T>?,
            index: Int,
            dialect: DatabaseDialect,
            columnType: IColumnType<*>,
        ): ValueContainer<T?> = NoValueContainer()
    }

    /* EXPOSED-1003 */
    @Test
    fun mapperResolutionShouldBeCachedPerDialectAndColumnType() {
        val mapper = AccessCountingMapper()
        val registry = R2dbcRegistryTypeMappingImpl()
        registry.register(mapper)

        val dialect = H2Dialect()
        val columnType = IntegerColumnType()

        val mockStatement = object : Statement {
            override fun add(): Statement = this
            override fun bind(index: Int, value: Any): Statement = this
            override fun bind(name: String, value: Any): Statement = this
            override fun bindNull(index: Int, type: Class<*>): Statement = this
            override fun bindNull(name: String, type: Class<*>): Statement = this
            override fun execute(): org.reactivestreams.Publisher<out io.r2dbc.spi.Result> {
                throw UnsupportedOperationException()
            }

            override fun returnGeneratedValues(vararg columns: String?): Statement = this
            override fun fetchSize(rows: Int): Statement = this
        }

        val result = registry.setValue(mockStatement, dialect, columnType, 42, 1)
        assertEquals(true, result, "setValue should find a matching mapper for IntegerColumnType")

        repeat(100) {
            registry.setValue(mockStatement, dialect, columnType, 42, 1)
        }

        assertTrue(
            mapper.columnTypesAccessCount < 100,
            "Expected mapper metadata to be cached, not re-evaluated on every call."
        )
        assertTrue(
            mapper.dialectsAccessCount < 100,
            "Expected mapper metadata to be cached, not re-evaluated on every call."
        )
    }
}
