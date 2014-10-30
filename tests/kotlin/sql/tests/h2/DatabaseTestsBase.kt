package kotlin.sql.tests.h2

import kotlin.sql.*
import java.util.TimeZone

public abstract class DatabaseTestsBase {
    fun withDb( statement: Session.() -> Unit) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

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
            }  finally {
                drop (*tables)
            }
        }
    }
}
