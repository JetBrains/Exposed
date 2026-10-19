package org.jetbrains.exposed.v1.spring.data

/**
 * Maps a property to the column [name] of the table, instead of the column with the same Kotlin property name:
 *
 * ```kotlin
 * object Orders : LongIdTable("orders") {
 *     val billingCustomer = reference("billing_customer_id", Customers)
 *     val shippingCustomer = reference("shipping_customer_id", Customers)
 * }
 *
 * data class Order(
 *     val id: Long? = null,
 *     @ExposedColumn("billingCustomer") val billing: Customer,
 *     @ExposedColumn("shippingCustomer") val shipping: Long,
 * )
 * ```
 *
 * [name] is the Kotlin property name of the column on the table object (e.g. `"billingCustomer"`, not `"billing_customer_id"`).
 * It works for every column, also for plain ones, and on entities, DTO and interface projections and nested types.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExposedColumn(val name: String)
