@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package sample

import org.jetbrains.exposed.v1.core.Table

const val EMAIL_LENGTH = 320

object Users : Table("users") {
    val id = uuid("id")
    val email = varchar("email", EMAIL_LENGTH)

    override val primaryKey = PrimaryKey(id)
}
