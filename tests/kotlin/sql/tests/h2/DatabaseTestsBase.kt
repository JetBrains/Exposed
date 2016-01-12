package org.jetbrains.exposed.sql.tests.h2

import org.joda.time.DateTimeZone
import java.util.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.Table

abstract class DatabaseTestsBase {
    fun withDb( statement: Transaction.() -> Unit) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        DateTimeZone.setDefault(DateTimeZone.UTC)

        var db = Database.connect("jdbc:h2:mem:", driver = "org.h2.Driver")

        db.transaction {
            statement()
        }
    }

    fun withTables (vararg tables: Table, statement: Transaction.() -> Unit) {
        withDb {
            create(*tables)
            try {
                statement()
                commit() // Need commit to persist data before drop tables
            }  finally {
                drop (*tables)
            }
        }
    }
}
