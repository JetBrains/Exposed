package org.jetbrains.exposed.v1.perf.jmh

import org.jetbrains.exposed.v1.r2dbc.mappers.ArrayTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.BinaryTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.DateTimeTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.DefaultTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.ExposedColumnTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.PostgresSpecificTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.PrimitiveTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.TypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.ValueTypeMapper
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class MapperPropertyAccessBenchmark {

    private lateinit var mappers: List<TypeMapper>

    @Setup
    fun setup() {
        mappers = listOf(
            ExposedColumnTypeMapper(),
            PrimitiveTypeMapper(),
            DateTimeTypeMapper(),
            BinaryTypeMapper(),
            ArrayTypeMapper(),
            PostgresSpecificTypeMapper(),
            ValueTypeMapper(),
            DefaultTypeMapper(),
        )
    }

    @Benchmark
    fun readDialects(bh: Blackhole) {
        for (m in mappers) bh.consume(m.dialects)
    }

    @Benchmark
    fun readColumnTypes(bh: Blackhole) {
        for (m in mappers) bh.consume(m.columnTypes)
    }
}
