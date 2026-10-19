package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.Table
import kotlin.reflect.KClass

/**
 * Declares that a collection property holds the rows of [table] that reference the owning table with a foreign key (a to-many relation):
 *
 * ```kotlin
 * data class Order(
 *     val id: Long? = null,
 *     @ExposedReferencedBy(OrderItems::class) val items: List<OrderItem> = emptyList(),
 *     @ExposedReferencedBy(OrderTags::class, target = "tag") val tags: Set<Tag> = emptySet(),
 * )
 *
 * fun findByItemsName(name: String): List<Order> // orders with at least one item with that name
 * ```
 *
 * - without [target], the rows of [table] are the elements and **owned** by the parent (one-to-many): they are loaded with it,
 *   saving the parent inserts, updates and deletes them to match the collection, and deleting the parent deletes them.
 * - with [target], [table] is a join table (many-to-many): the elements are the rows referenced by its foreign key column [target]
 *   (the Kotlin property name, e.g. `"tag"`). They are only loaded, never written, also the rows of [table] aren't.
 *
 * The foreign key column of [table] to the owning table is found automatically. If [table] has more than one,
 * [column] must name the one to use, as the Kotlin property name of the column (e.g. `"order"`, not `"order_id"`).
 * [table] doesn't need an id column. The collection can be a `List`, `Set`, `Collection` or `Iterable`.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExposedReferencedBy(
    val table: KClass<out Table>,
    val column: String = "",
    val target: String = "",
)
