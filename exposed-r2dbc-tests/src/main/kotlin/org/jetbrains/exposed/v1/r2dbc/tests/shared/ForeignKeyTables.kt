package org.jetbrains.exposed.v1.r2dbc.tests.shared

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object Category : Table("Category") {
    val id = integer("id")
    val name = varchar(name = "name", length = 20)

    override val primaryKey = PrimaryKey(id)
}

const val DEFAULT_CATEGORY_ID = 0

object Item : Table("Item") {
    val id = integer("id")
    val name = varchar(name = "name", length = 20)
    val categoryId = integer("categoryId")
        .default(DEFAULT_CATEGORY_ID)
        .references(
            Category.id,
            onDelete = ReferenceOption.SET_DEFAULT,
            onUpdate = ReferenceOption.NO_ACTION
        )

    override val primaryKey = PrimaryKey(id)
}
