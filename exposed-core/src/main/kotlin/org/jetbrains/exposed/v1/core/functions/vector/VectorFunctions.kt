package org.jetbrains.exposed.v1.core.functions.vector

import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Function
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.vendors.currentDialect

/**
 * The underlying mathematical formula to use when building an SQL vector distance function,
 * to determine how similar or dissimilar vectors are.
 */
enum class VectorDistanceMetric {
    /**
     * Measures the cosine of the angle between two vectors.
     */
    COSINE,

    /**
     * Measures the straight-line distance between two vectors, using the Pythagorean theorem
     * applied to their coordinates.
     */
    EUCLIDEAN,

    /**
     * Measures the sum of the products of corresponding coordinates between two vectors.
     */
    DOT,
}

/**
 * Represents an SQL function that returns the mathematical distance between two provided,
 * or stored, vector operands, based on the logic provided  by [VectorDistanceMetric].
 */
class VectorDistance<T>(
    /** The vector expression that is accessed. */
    val expression: Expression<T>,
    /** The second vector expression accessed as the target operand. */
    val targetExpression: Expression<T>,
    /** The specific mathematical formula to use when calculating the distance. */
    val metric: VectorDistanceMetric
) : Function<Double>(DoubleColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.vectorDistance(expression, targetExpression, metric, queryBuilder)
    }
}
