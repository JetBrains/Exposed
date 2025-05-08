import org.jetbrains.exposed.v1.sql.Table

const val EMAIL_LIMIT = 320

object Users : Table("Users") {
    val id = uuid("id")
    val email = varchar("email", EMAIL_LIMIT)

    override val primaryKey = PrimaryKey(id)
}
