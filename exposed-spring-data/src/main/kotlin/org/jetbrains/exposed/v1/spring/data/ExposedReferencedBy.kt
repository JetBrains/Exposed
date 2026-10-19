package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.Table
import kotlin.reflect.KClass

/**
 * Declares that a property holds the rows of [table] that reference the owning table with a foreign key (a to-many relation),
 * so derived queries can filter by their properties:
 *
 * ```kotlin
 * data class Order(
 *     val id: Long? = null,
 *     @ExposedReferencedBy(OrderItems::class) val items: List<OrderItem> = emptyList(),
 * )
 *
 * fun findByItemsName(name: String): List<Order> // orders with at least one item with that name
 * ```
 *
 * The foreign key column of [table] is found automatically. If [table] has more than one foreign key to the owning table,
 * [column] must name the one to use, as the Kotlin property name of the column (e.g. `"order"`, not `"order_id"`).
 * [table] doesn't need an id column.
 *
 * The property is only used for queries: the repository neither loads nor saves it, so give it a default value.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExposedReferencedBy(
    val table: KClass<out Table>,
    val column: String = "",
)
