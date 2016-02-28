package org.jetbrains.exposed.sql.vendors

import org.h2.jdbc.JdbcConnection
import org.jetbrains.exposed.sql.*
import java.util.*

/**
 * User: Kviring Aleksey
 * Date: 21.03.2016
 */

internal object PostgreSQL : VendorDialect("postgresql") {

    val DEFAULT_SEQ = "id_seq"

    override fun configure(database: Database): Unit {
        createSequenceIfNotExists(database, DEFAULT_SEQ)
    }

    private fun createSequenceIfNotExists(database: Database, sequence: String) {
        database.transaction {
            var exists_sequence = HashSet<String>()
            exec("SELECT sequence_name FROM information_schema.sequences") {
                while (it.next()) {
                    exists_sequence.add(it.getString("sequence_name"))
                }
            }
            if (!exists_sequence.contains(sequence)) {
                exec("create sequence $sequence") {}
            }
        }
    }

    override fun getDatabase(): String {
        return Transaction.current().connection.catalog
    }

    override fun supportsSelectForUpdate() = true
    override fun shortAutoincType(): String = "INT DEFAULT nextval('$DEFAULT_SEQ')"
    override fun longAutoincType(): String = "LONG DEFAULT nextval('$DEFAULT_SEQ')"


    val quotes = setOf("user")
    override fun needQuotes(identity: String): Boolean = quotes.contains(identity)
}
