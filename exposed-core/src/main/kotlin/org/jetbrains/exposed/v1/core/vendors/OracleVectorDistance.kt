package org.jetbrains.exposed.v1.core.vendors

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.Function
import org.jetbrains.exposed.v1.core.QueryBuilder

private enum class OracleVectorDistanceMetric { COSINE, EUCLIDEAN, DOT }

private class OracleVectorDistance(
    private val column: Column<FloatArray>,
    private val target: FloatArray,
    private val metric: OracleVectorDistanceMetric
) : Function<Double>(DoubleColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            +"VECTOR_DISTANCE("
            append(column)
            +", "
            +"TO_VECTOR('[${target.joinToString(",")}]')"
            +", ${metric.name})"
        }
    }
}

fun Column<FloatArray>.cosineDistance(target: FloatArray): Function<Double> =
    OracleVectorDistance(this, target, OracleVectorDistanceMetric.COSINE)

fun Column<FloatArray>.euclideanDistance(target: FloatArray): Function<Double> =
    OracleVectorDistance(this, target, OracleVectorDistanceMetric.EUCLIDEAN)

fun Column<FloatArray>.dotDistance(target: FloatArray): Function<Double> =
    OracleVectorDistance(this, target, OracleVectorDistanceMetric.DOT)
