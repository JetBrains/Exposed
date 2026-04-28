package org.jetbrains.exposed.v1.core.vendors

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Function
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder

enum class OracleVectorDistanceMetric { COSINE, EUCLIDEAN, DOT }

class OracleVectorDistance(
    private val column: Column<FloatArray>,
    private val target: FloatArray,
    private val metric: OracleVectorDistanceMetric
) : Function<Double>(object : ColumnType<Double>() {
    override fun sqlType() = "DOUBLE PRECISION"
    override fun valueFromDB(value: Any) = (value as Number).toDouble()
}) {
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

fun Column<FloatArray>.cosineDistance(target: FloatArray) =
    OracleVectorDistance(this, target, OracleVectorDistanceMetric.COSINE)

fun Column<FloatArray>.euclideanDistance(target: FloatArray) =
    OracleVectorDistance(this, target, OracleVectorDistanceMetric.EUCLIDEAN)

fun Column<FloatArray>.dotDistance(target: FloatArray) =
    OracleVectorDistance(this, target, OracleVectorDistanceMetric.DOT)
