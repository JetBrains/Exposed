package org.jetbrains.exposed.v1.postgresql.hstore

import org.jetbrains.exposed.v1.core.ComplexExpression
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.Function
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.arrayParam
import org.jetbrains.exposed.v1.core.stringParam

// Operator Classes

/**
 * Represents the PostgreSQL `->` operator, which returns the value stored under [key] in [target],
 * or `NULL` if [key] is not present.
 */
class HstoreGet(
    /** The `hstore` expression being read. */
    val target: Expression<*>,
    /** The expression for the key whose value should be returned. */
    val key: Expression<String>
) : Function<String?>(TextColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = with(queryBuilder) {
        append(target, " -> ", key)
    }
}

/**
 * Represents the PostgreSQL `@>` operator, which checks whether [target] contains all the key-value
 * pairs present in [candidate].
 */
class HstoreContains(
    /** The `hstore` expression being searched. */
    val target: Expression<*>,
    /** The `hstore` expression whose entries must all be present in [target]. */
    val candidate: Expression<*>
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = with(queryBuilder) {
        append(target, " @> ", candidate)
    }
}

/**
 * Represents PostgreSQL's `exist(hstore, text)` function, which checks whether [target] contains [key].
 *
 * **Note**: The `?` hstore operator is intentionally not used here, since a bare `?` in generated SQL
 * collides with JDBC's positional parameter placeholders.
 */
class HstoreExists(
    /** The `hstore` expression being checked. */
    val target: Expression<*>,
    /** The expression for the key to check for. */
    val key: Expression<String>
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = with(queryBuilder) {
        append("exist(")
        append(target)
        append(", ")
        append(key)
        append(")")
    }
}

/**
 * Represents the PostgreSQL `?&` operator, which checks whether [target] contains all of the [keys].
 *
 * **Note**: The operator is written as `??&` in the generated SQL. A bare `?` collides with JDBC's
 * positional parameter placeholders, and both the JDBC driver and Exposed's own R2DBC placeholder
 * translation recognize a doubled `??` as an escaped, literal `?` character.
 */
class HstoreExistsAll(
    /** The `hstore` expression being checked. */
    val target: Expression<*>,
    /** The expression for the array of keys that must all be present. */
    val keys: Expression<List<String>>
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = with(queryBuilder) {
        append(target, " ??& ", keys)
    }
}

/**
 * Represents the PostgreSQL `?|` operator, which checks whether [target] contains any of the [keys].
 *
 * **Note**: The operator is written as `??|` in the generated SQL. A bare `?` collides with JDBC's
 * positional parameter placeholders, and both the JDBC driver and Exposed's own R2DBC placeholder
 * translation recognize a doubled `??` as an escaped, literal `?` character.
 */
class HstoreExistsAny(
    /** The `hstore` expression being checked. */
    val target: Expression<*>,
    /** The expression for the array of keys of which at least one must be present. */
    val keys: Expression<List<String>>
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = with(queryBuilder) {
        append(target, " ??| ", keys)
    }
}

/**
 * Represents the PostgreSQL `-` operator, which returns a copy of [target] with [argument] removed.
 *
 * [argument] may be a single key, an array of keys, or another `hstore` whose matching key-value pairs
 * should be removed.
 */
class HstoreDelete(
    /** The `hstore` expression being modified. */
    val target: Expression<*>,
    /** The expression for the key(s) or `hstore` to remove from [target]. */
    val argument: Expression<*>
) : Function<Map<String, String?>>(HstoreColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = with(queryBuilder) {
        append(target, " - ", argument)
    }
}

/**
 * Represents the PostgreSQL `||` operator, which concatenates [target] with [other], returning a new `hstore`.
 * Where both operands share a key, the value from [other] wins.
 */
class HstoreConcat(
    /** The `hstore` expression on the left-hand side of the concatenation. */
    val target: Expression<*>,
    /** The `hstore` expression on the right-hand side of the concatenation. */
    val other: Expression<*>
) : Function<Map<String, String?>>(HstoreColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = with(queryBuilder) {
        append(target, " || ", other)
    }
}

// Extension Functions

/**
 * Returns the value stored under [key] in [this] `hstore` expression, or `NULL` if [key] is not present.
 *
 * @sample org.jetbrains.exposed.v1.postgresql.hstore.HstoreColumnTests.testHstoreGet
 */
fun ExpressionWithColumnType<*>.get(key: String): HstoreGet = HstoreGet(this, stringParam(key))

/**
 * Checks whether [candidate] is contained within [this] `hstore` expression.
 *
 * @sample org.jetbrains.exposed.v1.postgresql.hstore.HstoreColumnTests.testHstoreContains
 */
fun ExpressionWithColumnType<*>.contains(candidate: Map<String, String?>): HstoreContains =
    HstoreContains(this, QueryParameter(candidate, HstoreColumnType()))

/**
 * Checks whether [this] `hstore` expression contains [key], using PostgreSQL's `exist()` function.
 *
 * @sample org.jetbrains.exposed.v1.postgresql.hstore.HstoreColumnTests.testHstoreExists
 */
fun ExpressionWithColumnType<*>.exists(key: String): HstoreExists = HstoreExists(this, stringParam(key))

/**
 * Checks whether [this] `hstore` expression contains all of the given [keys], using PostgreSQL's `?&` operator.
 *
 * @sample org.jetbrains.exposed.v1.postgresql.hstore.HstoreColumnTests.testHstoreExistsAll
 */
fun ExpressionWithColumnType<*>.existsAll(keys: List<String>): HstoreExistsAll =
    HstoreExistsAll(this, arrayParam(keys, TextColumnType()))

/**
 * Checks whether [this] `hstore` expression contains any of the given [keys], using PostgreSQL's `?|` operator.
 *
 * @sample org.jetbrains.exposed.v1.postgresql.hstore.HstoreColumnTests.testHstoreExistsAny
 */
fun ExpressionWithColumnType<*>.existsAny(keys: List<String>): HstoreExistsAny =
    HstoreExistsAny(this, arrayParam(keys, TextColumnType()))

/**
 * Returns a copy of [this] `hstore` expression with [key] removed, using PostgreSQL's `-` operator.
 *
 * @sample org.jetbrains.exposed.v1.postgresql.hstore.HstoreColumnTests.testHstoreDeleteKey
 */
fun ExpressionWithColumnType<*>.delete(key: String): HstoreDelete = HstoreDelete(this, stringParam(key))

/**
 * Returns a copy of [this] `hstore` expression with all of the given [keys] removed, using PostgreSQL's
 * `-` operator.
 *
 * @sample org.jetbrains.exposed.v1.postgresql.hstore.HstoreColumnTests.testHstoreDeleteKeys
 */
fun ExpressionWithColumnType<*>.delete(keys: List<String>): HstoreDelete =
    HstoreDelete(this, arrayParam(keys, TextColumnType()))

/**
 * Returns a copy of [this] `hstore` expression with all key-value pairs also present in [other] removed,
 * using PostgreSQL's `-` operator.
 *
 * @sample org.jetbrains.exposed.v1.postgresql.hstore.HstoreColumnTests.testHstoreDeleteMatching
 */
fun ExpressionWithColumnType<*>.delete(other: Map<String, String?>): HstoreDelete =
    HstoreDelete(this, QueryParameter(other, HstoreColumnType()))

/**
 * Concatenates [this] `hstore` expression with [other], using PostgreSQL's `||` operator. Where both
 * operands share a key, the value from [other] wins.
 *
 * @sample org.jetbrains.exposed.v1.postgresql.hstore.HstoreColumnTests.testHstoreConcat
 */
fun ExpressionWithColumnType<*>.concat(other: Map<String, String?>): HstoreConcat =
    HstoreConcat(this, QueryParameter(other, HstoreColumnType()))

private fun QueryBuilder.append(target: Expression<*>, operator: String, other: Expression<*>) {
    append(target)
    append(operator)
    append(other)
}
