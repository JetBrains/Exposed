package org.jetbrains.exposed.v1.perf.jmh

import io.r2dbc.spi.Row
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcRegistryTypeMapping
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcTypeMapping
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class TypeMapperResolutionBenchmark {

    @Param("Integer", "String")
    lateinit var columnTypeName: String

    private lateinit var registry: R2dbcTypeMapping
    private lateinit var dialect: H2Dialect
    private lateinit var columnType: IColumnType<*>
    private lateinit var row: Row

    @Setup
    fun setup() {
        registry = R2dbcRegistryTypeMapping.default()
        dialect = H2Dialect()
        columnType = when (columnTypeName) {
            "Integer" -> IntegerColumnType()
            "String" -> VarCharColumnType()
            else -> error("unknown column type $columnTypeName")
        }
        row = StubRow(when (columnTypeName) {
            "Integer" -> 42
            "String" -> "hello"
            else -> error("unknown")
        })
    }

    @Benchmark
    fun getValue(): Any? {
        return registry.getValue(row, null, 1, dialect, columnType)
    }
}

private class StubRow(private val value: Any?) : Row {
    override fun getMetadata(): io.r2dbc.spi.RowMetadata = error("not implemented")
    override fun get(index: Int): Any? = value
    override fun get(name: String): Any? = value
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(index: Int, type: Class<T>): T? = value as T?
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(name: String, type: Class<T>): T? = value as T?
}
