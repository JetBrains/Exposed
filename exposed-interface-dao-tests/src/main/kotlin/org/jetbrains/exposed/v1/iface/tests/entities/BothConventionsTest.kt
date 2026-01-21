package org.jetbrains.exposed.v1.iface.tests.entities

import org.jetbrains.exposed.v1.iface.annotations.*

/**
 * Demonstrates that both naming conventions work:
 * - Clean convention: User -> UserTable, UserEntity
 * - Legacy convention: ICustomer -> CustomerTable, CustomerEntity
 */

// Clean convention (preferred) - no companion object needed!
@ExposedEntity
interface Order {
    @Varchar(100)
    var orderNumber: String

    var quantity: Int
}

// Legacy convention with "I" prefix (still supported for backwards compatibility)
@ExposedEntity
interface ICustomer {
    @Varchar(100)
    var customerName: String

    @Varchar(255)
    var email: String
}

/**
 * Both generate the same pattern:
 *
 * Order -> OrderTable, OrderEntity, OrderData, OrderExtensions.kt
 * ICustomer -> CustomerTable, CustomerEntity, CustomerData, ICustomerExtensions.kt
 */
