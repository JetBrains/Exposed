package org.jetbrains.exposed.sql.vendors

class MariaDBDialect : MysqlDialect() {
    override val name: String = dialectName
    companion object {
        const val dialectName = "mariadb"
    }
}