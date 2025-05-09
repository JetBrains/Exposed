package org.example.examples

import org.jetbrains.exposed.v1.TextColumnType
import org.jetbrains.exposed.v1.append
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Function
import org.jetbrains.exposed.v1.core.QueryBuilder

enum class TrimSpecifier { BOTH, LEADING, TRAILING }

class CustomTrim<T : String?>(
    val expression: Expression<T>,
    val toRemove: Expression<T>?,
    val trimSpecifier: TrimSpecifier
) : Function<String>(TextColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            append("TRIM(")
            append(trimSpecifier.name)
            toRemove?.let { +" $it" }
            append(" FROM ", expression, ")")
        }
    }
}

fun <T : String?> Expression<T>.customTrim(
    toRemove: Expression<T>? = null,
    specifier: TrimSpecifier = TrimSpecifier.BOTH
): CustomTrim<T> = CustomTrim(this, toRemove, specifier)
