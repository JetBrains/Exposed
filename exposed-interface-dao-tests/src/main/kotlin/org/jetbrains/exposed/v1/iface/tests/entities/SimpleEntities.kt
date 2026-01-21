package org.jetbrains.exposed.v1.iface.tests.entities

import org.jetbrains.exposed.v1.iface.annotations.*

/**
 * Simple entity with basic types - tests basic code generation.
 */
@ExposedEntity
interface User {
    @Varchar(1000)
    var name: String
    var email: String
    var age: Int?
}

/**
 * Entity with type-specific annotations - tests varchar, text, decimal.
 */
@ExposedEntity
interface Product {
    @Varchar(100)
    var name: String

    @Text
    var description: String

    @Decimal(precision = 10, scale = 2)
    var price: java.math.BigDecimal

    var stock: Int
}

/**
 * Entity with constraints - tests unique and index annotations.
 */
@ExposedEntity
interface Account {
    @Varchar(50)
    @Unique
    var username: String

    @Varchar(255)
    @Unique
    var email: String

    @Varchar(100)
    @Index
    var displayName: String

    var isActive: Boolean
}

/**
 * Entity with default values - tests client-side defaults.
 */
@ExposedEntity
interface Post {
    @Varchar(200)
    var title: String

    @Text
    var content: String

    @ClientDefault("0")
    var viewCount: Int

    @ClientDefault("false")
    var isPublished: Boolean
}

/**
 * Entity with custom column names - tests @Column annotation.
 */
@ExposedEntity(tableName = "app_settings")
interface Setting {
    @Column(name = "setting_key")
    @Varchar(100)
    @Unique
    var key: String

    @Column(name = "setting_value")
    @Text
    var value: String
}

/**
 * Entity with nullable types - tests nullable handling.
 */
@ExposedEntity
interface Profile {
    @Varchar(100)
    var firstName: String

    @Varchar(100)
    var lastName: String

    @Varchar(255)
    var bio: String?

    @Varchar(50)
    var website: String?
}
