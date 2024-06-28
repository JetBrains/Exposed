import org.jetbrains.exposed.sql.Table

const val EMAIL_LIMIT = 320

object UsersV1 : Table("Users") {
    val id = uuid("id")
    val email = varchar("email", EMAIL_LIMIT)
}

object UsersV2 : Table("Users") {
    val id = uuid("id")
    val email = varchar("email", EMAIL_LIMIT)

    override val primaryKey = PrimaryKey(id)
}
