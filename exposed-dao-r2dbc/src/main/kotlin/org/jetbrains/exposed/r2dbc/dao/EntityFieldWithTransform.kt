package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnTransformer

/**
 * Class responsible for enabling [R2dbcEntity] field transformations, which may be useful when advanced database
 * type conversions are necessary for entity mappings.
 *
 * Mirrors JDBC's `EntityFieldWithTransform` — duplicated here rather than shared because `exposed-dao` is the
 * JDBC-only DAO module and `exposed-dao-r2dbc` cannot depend on it.
 */
open class EntityFieldWithTransform<Unwrapped, Wrapped>(
    /** The original column that will be transformed. */
    val column: Column<Unwrapped>,
    /** Instance of [ColumnTransformer] with the transformation logic. */
    private val transformer: ColumnTransformer<Unwrapped, Wrapped>,
    /**
     * Whether the original and transformed values should be cached to avoid multiple conversion calls.
     */
    protected val cacheResult: Boolean = false
) : ColumnTransformer<Unwrapped, Wrapped> {
    private var cache: Pair<Unwrapped, Wrapped>? = null

    override fun unwrap(value: Wrapped): Unwrapped = transformer.unwrap(value)

    /** The function used to transform a value stored in the original column type. */
    override fun wrap(value: Unwrapped): Wrapped {
        return if (cacheResult) {
            val localCache = cache
            if (localCache != null && localCache.first == value) {
                localCache.second
            } else {
                transformer.wrap(value).also { cache = value to it }
            }
        } else {
            transformer.wrap(value)
        }
    }
}
