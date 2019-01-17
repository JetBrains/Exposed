package org.jetbrains.exposed.sql.vendors

class MariaDBDialect : MysqlDialect() {
    companion object {
        const val dialectName = "mariadb"
    }
}