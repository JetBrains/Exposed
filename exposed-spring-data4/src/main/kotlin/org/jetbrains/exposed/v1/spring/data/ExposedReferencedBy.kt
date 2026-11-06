package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.Table
import kotlin.reflect.KClass

/**
 * Sets the to-many relation of a collection property: the rows of [table] that reference the owning table with a foreign key.
 *
 * Most collection properties don't need this annotation. The repository infers the relation from the foreign keys of the
 * table objects in the packages of the Exposed repositories. Use the annotation in these cases:
 *
 * - The relation is ambiguous: more than one foreign key or join target fits. Repository creation fails with a message
 *   that lists the candidates and names the attribute to set ([table], [column] or [target]).
 * - No relation is found, for example because [table] isn't in a scanned package.
 * - You need a different relation than the inferred one.
 *
 * An annotated property always uses the annotation, and inference is skipped.
 *
 * `Transfers` has two foreign keys to `Accounts`, so the relation is ambiguous and needs [column]:
 *
 * ```kotlin
 * object Transfers : LongIdTable("transfers") {
 *     val from = reference("from_id", Accounts)
 *     val to = reference("to_id", Accounts)
 *     val amount = long("amount")
 * }
 *
 * data class Account(
 *     val id: Long? = null,
 *     @ExposedReferencedBy(Transfers::class, column = "from") val outgoing: List<Transfer> = emptyList(),
 *     @ExposedReferencedBy(Transfers::class, column = "to") val incoming: List<Transfer> = emptyList(),
 * )
 * ```
 *
 * The relation is one of two kinds:
 *
 * - Without [target], the rows of [table] are the elements and are **owned** by the parent (one-to-many). They are loaded
 *   with the parent. Saving the parent inserts, updates and deletes them to match the collection.
 * - With [target], [table] is a join table (many-to-many). The elements are the rows referenced by its foreign key column [target].
 *   These rows and the rows of [table] are only loaded, never written.
 *
 * Deleting the parent deletes only the parent row. The rows of [table] follow the `onDelete` rule of their foreign key:
 * `CASCADE` deletes them, `SET_NULL` sets the key to `NULL`, and `RESTRICT` makes the delete fail. Without a rule, the database default applies,
 * which rejects the delete in most databases.
 *
 * [column] and [target] are Kotlin property names of columns of [table] (for example `"order"`, not `"order_id"`).
 * If [table] has exactly one foreign key to the owning table, [column] can stay empty.
 * [table] doesn't need an id column. The collection can be a `List`, `Set`, `Collection` or `Iterable`.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExposedReferencedBy(
    val table: KClass<out Table>,
    val column: String = "",
    val target: String = "",
)
