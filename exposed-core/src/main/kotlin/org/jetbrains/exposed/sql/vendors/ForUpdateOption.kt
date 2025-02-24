package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.Table

/**
 * Clauses that perform a locking read at row-level for SELECT statements.
 *
 * @sample org.jetbrains.exposed.sql.tests.postgresql.PostgresqlTests.testForUpdateOptionsSyntax
 */
sealed class ForUpdateOption(open val querySuffix: String) {

    internal data object NoForUpdateOption : ForUpdateOption("") {
        override val querySuffix: String get() = error("querySuffix should not be called for NoForUpdateOption object")
    }

    /** Common clause that locks the rows retrieved by a SELECT statement against concurrent updates. */
    data object ForUpdate : ForUpdateOption("FOR UPDATE")

    // https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html for clarification
    object MySQL {
        /** Optional modes that determine what should happen if the retrieved rows are not immediately available. */
        enum class MODE(val statement: String) {
            /** Indicates that an error should be reported. */
            NO_WAIT("NOWAIT"),

            /** Indicates that the unavailable rows should be skipped. */
            SKIP_LOCKED("SKIP LOCKED")
        }

        abstract class ForUpdateBase(
            querySuffix: String,
            private val mode: MODE? = null
        ) : ForUpdateOption("") {
            private val preparedQuerySuffix = buildString {
                append(querySuffix)
                mode?.let {
                    append(" ${it.statement}")
                }
            }
            final override val querySuffix: String = preparedQuerySuffix
        }

        /** MySQL clause that locks the rows retrieved against concurrent updates. */
        class ForUpdate(mode: MODE? = null) : ForUpdateBase("FOR UPDATE", mode) {
            companion object Default : ForUpdateBase("FOR UPDATE")
        }

        /** MySQL clause that acquires a shared lock for each row retrieved. */
        class ForShare(mode: MODE? = null) : ForUpdateBase("FOR SHARE", mode) {
            companion object Default : ForUpdateBase("FOR SHARE")
        }

        /** This MySQL clause is equivalent to [ForShare] but exists for backward compatibility. */
        data object LockInShareMode : ForUpdateOption("LOCK IN SHARE MODE")
    }

    // https://mariadb.com/kb/en/select/#lock-in-share-modefor-update
    object MariaDB {
        /** MariaDB clause that acquires a shared lock for each row retrieved. */
        data object LockInShareMode : ForUpdateOption("LOCK IN SHARE MODE")
    }

    // https://www.postgresql.org/docs/current/sql-select.html
    // https://www.postgresql.org/docs/12/explicit-locking.html#LOCKING-ROWS for clarification
    object PostgreSQL {
        /** Optional modes that determine what should happen if the retrieved rows are not immediately available. */
        enum class MODE(val statement: String) {
            /** Indicates that an error should be reported. */
            NO_WAIT("NOWAIT"),

            /** Indicates that the unavailable rows should be skipped. */
            SKIP_LOCKED("SKIP LOCKED")
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

        /** PostgreSQL clause that locks the rows retrieved as though for update. */
        class ForUpdate(
            mode: MODE? = null,
            vararg ofTables: Table
        ) : ForUpdateBase("FOR UPDATE", mode, ofTables = ofTables)

        /** PostgreSQL clause that locks the rows retrieved, but at a weaker strength than [ForUpdate]. */
        open class ForNoKeyUpdate(
            mode: MODE? = null,
            vararg ofTables: Table
        ) : ForUpdateBase("FOR NO KEY UPDATE", mode, ofTables = ofTables) {
            companion object : ForNoKeyUpdate()
        }

        /** PostgreSQL clause that acquires a shared lock for each row retrieved. */
        open class ForShare(
            mode: MODE? = null,
            vararg ofTables: Table
        ) : ForUpdateBase("FOR SHARE", mode, ofTables = ofTables) {
            companion object : ForShare()
        }

        /** PostgreSQL clause that acquires a shared lock for each row, but at a weaker strength than [ForShare]. */
        open class ForKeyShare(
            mode: MODE? = null,
            vararg ofTables: Table
        ) : ForUpdateBase("FOR KEY SHARE", mode, ofTables = ofTables) {
            companion object : ForKeyShare()
        }
    }

    // https://docs.oracle.com/cd/B19306_01/server.102/b14200/statements_10002.htm#i2066346
    object Oracle {
        /** Oracle clause that never waits to acquire a row lock. */
        data object ForUpdateNoWait : ForUpdateOption("FOR UPDATE NOWAIT")

        /** Oracle clause that waits for the provided timeout until the row becomes available. */
        class ForUpdateWait(timeout: Int) : ForUpdateOption("FOR UPDATE WAIT $timeout")
    }
}
