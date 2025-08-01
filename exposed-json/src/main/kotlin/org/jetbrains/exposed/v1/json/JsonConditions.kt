package org.jetbrains.exposed.v1.json

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.asLiteral
import org.jetbrains.exposed.v1.core.vendors.currentDialect

// Operator Classes

/**
 * Represents an SQL operator that checks whether a [candidate] expression is contained within a JSON [target].
 */
class Contains(
    /** The JSON expression being searched. */
    val target: Expression<*>,
    /** The expression being searched for in [target]. */
    val candidate: Expression<*>,
    /** An optional String representing JSON path/keys that match specific fields to search for [candidate]. */
    val path: String?,
    /** The column type of [target] to check, if casting to JSONB is required. */
    val jsonType: IColumnType<*>
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        currentDialect.functionProvider.jsonContains(target, candidate, path, jsonType, queryBuilder)
}

/**
 * Represents an SQL operator that checks whether data exists within a JSON [expression] at the specified [path].
 */
class Exists(
    /** The JSON expression being checked. */
    val expression: Expression<*>,
    /** The array of Strings representing JSON path/keys that match fields to check for existing data. */
    vararg val path: String,
    /** An optional String representing any vendor-specific clause or argument. */
    val optional: String?,
    /** The column type of [expression] to check, if casting to JSONB is required. */
    val jsonType: IColumnType<*>
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        currentDialect.functionProvider.jsonExists(expression, path = path, optional, jsonType, queryBuilder)
}

// Extension Functions

/**
 * Checks whether a [candidate] expression is contained within [this] JSON expression.
 *
 * @param candidate Expression to search for in [this] JSON expression.
 * @param path String representing JSON path/keys that match specific fields to search for [candidate].
 * **Note:** Optional [path] argument is not supported by all vendors; please check the documentation.
 * @sample org.jetbrains.exposed.v1.json.JsonColumnTests.testJsonContains
 */
fun ExpressionWithColumnType<*>.contains(candidate: Expression<*>, path: String? = null): Contains =
    Contains(this, candidate, path, columnType)

/**
 * Checks whether a [candidate] value is contained within [this] JSON expression.
 *
 * @param candidate Value to search for in [this] JSON expression.
 * @param path String representing JSON path/keys that match specific fields to search for [candidate].
 * **Note:** Optional [path] argument is not supported by all vendors; please check the documentation.
 * @sample org.jetbrains.exposed.v1.json.JsonColumnTests.testJsonContains
 */
fun <T> ExpressionWithColumnType<*>.contains(candidate: T, path: String? = null): Contains = when (candidate) {
    is String -> Contains(this, stringLiteral(candidate), path, columnType)
    else -> Contains(this, asLiteral(candidate), path, columnType)
}

/**
 * Checks whether data exists within [this] JSON expression at the specified [path].
 *
 * @param path String(s) representing JSON path/keys that match fields to check for existing data.
 * If none are provided, the root context item `'$'` will be used by default.
 * **Note:** Multiple [path] arguments are not supported by all vendors; please check the documentation.
 * @param optional String representing any optional vendor-specific clause or argument.
 * **Note:** [optional] function arguments are not supported by all vendors; please check the documentation.
 * @sample org.jetbrains.exposed.v1.json.JsonColumnTests.testJsonExists
 */
fun ExpressionWithColumnType<*>.exists(vararg path: String, optional: String? = null): Exists =
    Exists(this, path = path, optional, columnType)
