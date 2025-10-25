package org.jetbrains.exposed.v1.core.vendors

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table

/**
 * Clauses that perform a locking read at row-level for SELECT statements.
 *
 * @sample org.jetbrains.exposed.v1.tests.postgresql.PostgresqlTests.testForUpdateOptionsSyntax
 */
sealed class ForUpdateOption(open val querySuffix: String) {
    /** @suppress */
    @InternalApi
    data object NoForUpdateOption : ForUpdateOption("") {
        override val querySuffix: String get() = error("querySuffix should not be called for NoForUpdateOption object")
    }

    /** Interface that can be implemented in each database if they support modes **/
    interface ForUpdateOrShareMode {
        val statement: String
    }

    /** Common class since this is being used by at least two DBs **/
    abstract class ForUpdateBase(
        querySuffix: String,
        private val mode: ForUpdateOrShareMode? = null,
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

    /** Common clause that locks the rows retrieved by a SELECT statement against concurrent updates. */
    data object ForUpdate : ForUpdateOption("FOR UPDATE")

    // https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html for clarification
    object MySQL {
        /** Optional modes that determine what should happen if the retrieved rows are not immediately available. */
        // https://dev.mysql.com/doc/refman/8.4/en/select.html
        enum class MODE(override val statement: String) : ForUpdateOrShareMode {
            /** Indicates that an error should be reported. */
            NO_WAIT("NOWAIT"),

            /** Indicates that the unavailable rows should be skipped. */
            SKIP_LOCKED("SKIP LOCKED")
        }

        /** MySQL clause that locks the rows retrieved as though for update. */
        class ForUpdate(
            mode: MODE? = null,
            vararg ofTables: Table
        ) : ForUpdateBase("FOR UPDATE", mode, ofTables = ofTables)

        /** MySQL clause that acquires a shared lock for each row retrieved. */
        open class ForShare(
            mode: MODE? = null,
            vararg ofTables: Table
        ) : ForUpdateBase("FOR SHARE", mode, ofTables = ofTables) {
            companion object : ForShare()
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
        enum class MODE(override val statement: String) : ForUpdateOrShareMode {
            /** Indicates that an error should be reported. */
            NO_WAIT("NOWAIT"),

            /** Indicates that the unavailable rows should be skipped. */
            SKIP_LOCKED("SKIP LOCKED")
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
