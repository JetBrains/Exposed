package kotlin.sql.tests.h2

import org.joda.time.DateTimeZone
import java.util.*
import kotlin.sql.Database
import kotlin.sql.Session
import kotlin.sql.Table

public abstract class DatabaseTestsBase {
    fun withDb( statement: Session.() -> Unit) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        DateTimeZone.setDefault(DateTimeZone.UTC)

        var db = Database.connect("jdbc:h2:mem:", driver = "org.h2.Driver")

        db.withSession {
            statement()
        }
    }

    fun withTables (vararg tables: Table, statement: Session.() -> Unit) {
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
