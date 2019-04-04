package org.jetbrains.exposed.sql.vendors

class MariaDBDialect : MysqlDialect() {
    override val name: String = dialectName
    override val supportsOnlyIdentifiersInGeneratedKeys = true
    companion object {
        const val dialectName = "mariadb"
    }
}