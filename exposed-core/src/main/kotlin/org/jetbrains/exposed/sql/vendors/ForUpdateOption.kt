package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.Table

sealed class ForUpdateOption(open val querySuffix: String) {

    internal object NoForUpdateOption : ForUpdateOption("") {
        override val querySuffix: String get() = error("querySuffix should not be called for NoForUpdateOption object")
    }

    object ForUpdate : ForUpdateOption("FOR UPDATE")

    // https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html for clarification
    object MySQL {
        object ForShare : ForUpdateOption("FOR SHARE")

        object LockInShareMode : ForUpdateOption("LOCK IN SHARE MODE")
    }

    // https://mariadb.com/kb/en/select/#lock-in-share-modefor-update
    object MariaDB {
        object LockInShareMode : ForUpdateOption("LOCK IN SHARE MODE")
    }

    // https://www.postgresql.org/docs/current/sql-select.html
    // https://www.postgresql.org/docs/12/explicit-locking.html#LOCKING-ROWS for clarification
    object PostgreSQL {
        enum class MODE(val statement: String) {
            NO_WAIT("NOWAIT"), SKIP_LOCKED("SKIP LOCKED")
        }

        abstract class ForUpdateBase(
            querySuffix: String,
            private val mode: MODE? = null,
            private vararg val ofTables: Table
        ) : ForUpdateOption("") {
            private val preparedQuerySuffix = buildString {
                append(querySuffix)
                ofTables.takeIf { it.isNotEmpty() }?.let { tables ->
                    append(" OF ")
                    tables.joinTo(this, separator = ",") { it.tableName }
                }
                mode?.let {
                    append(" ${it.statement}")
                }
            }
            final override val querySuffix: String = preparedQuerySuffix
        }

        class ForUpdate(
            mode: MODE? = null,
            vararg ofTables: Table
        ) : ForUpdateBase("FOR UPDATE", mode, ofTables = ofTables)

        open class ForNoKeyUpdate(
            mode: MODE? = null,
            vararg ofTables: Table
        ) : ForUpdateBase("FOR NO KEY UPDATE", mode, ofTables = ofTables) {
            companion object : ForNoKeyUpdate()
        }

        open class ForShare(
            mode: MODE? = null,
            vararg ofTables: Table
        ) : ForUpdateBase("FOR SHARE", mode, ofTables = ofTables) {
            companion object : ForShare()
        }

        open class ForKeyShare(
            mode: MODE? = null,
            vararg ofTables: Table
        ) : ForUpdateBase("FOR KEY SHARE", mode, ofTables = ofTables) {
            companion object : ForKeyShare()
        }
    }

    // https://docs.oracle.com/cd/B19306_01/server.102/b14200/statements_10002.htm#i2066346
    object Oracle {
        object ForUpdateNoWait : ForUpdateOption("FOR UPDATE NOWAIT")

        class ForUpdateWait(timeout: Int) : ForUpdateOption("FOR UPDATE WAIT $timeout")
    }
}
