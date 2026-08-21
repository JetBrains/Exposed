package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.statements.api.RowApi
import org.jetbrains.exposed.v1.core.transactions.currentTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/**
 * Reproducer for the freeze reported in [PR #2881](https://github.com/JetBrains/Exposed/pull/2881).
 *
 * `R2dbcDatabase` resolves database metadata through `connectionMetadata`, which is `runBlocking { metadata() }`.
 * Rows, on the other hand, are handed to Exposed by the driver, on the driver's event loop thread. So when a column
 * type asks for one of those metadata properties while decoding a row - which `BasicUuidColumnType.readObject` does
 * on MariaDB, through `db.version` - the event loop thread parks itself in `runBlocking` and waits for a metadata
 * query that only that same thread could deliver the answer to. The query never finishes, the transaction is never
 * committed and the connection is never released: the reporter saw sleeping MariaDB sessions holding open read-only
 * transactions with no row locks.
 *
 * Three conditions have to hold at the same time, which is why the reporter could not turn their production freeze
 * into a test:
 * 1. the database's `version` has not been resolved yet;
 * 2. the connection streaming the rows has no metadata object of its own yet, so building one runs a catalog query
 *    (`R2dbcConnectionImpl.metadata` -> `getCatalog()`); and
 * 3. the row reaches a subscriber that is already waiting for it, which is what keeps the decoding on the driver's
 *    event loop thread. Rows that arrive before Exposed asks for them are drained on the collecting coroutine's
 *    thread instead, where blocking is survivable. This is why the freeze shows up against a real database under
 *    load, and never in a quick local test.
 *
 * The test arranges all three: a database instance of its own for (1), a second transaction for (2), and rows large
 * enough for the server to flush each one separately plus a server side sleep between them for (3). The assumption
 * at the end reports the test as skipped if (3) did not hold after all, because then the run proves nothing.
 *
 * The probed row reads `version` and then `fullVersion`, because both are resolved the same way. Resolving `version`
 * up front, as PR #2881 proposed, turns the first read into a cached one and leaves the second - along with
 * `identifierManager`, `dialectMode` and `vendor` - blocking on the event loop exactly as before, so this test only
 * goes green once metadata retrieval itself stops blocking.
 *
 * The freeze is not MariaDB specific, despite how it was reported: it reproduces on PostgreSQL as well, because it
 * belongs to `R2dbcDatabase` rather than to a driver or a column type.
 *
 * **While the bug is present, this test leaves one driver event loop thread parked and one connection holding an
 * open transaction for the rest of the JVM's life.** Nothing can undo that, so the test keeps the freeze off its own
 * thread in order to fail cleanly, and it empties the table it uses instead of expecting to drop it.
 */
class R2dbcMetadataFreezeTest : R2dbcDatabaseTestsBase() {

    object ProbeTable : Table(TABLE_NAME) {
        val value = integer("value")
        val payload = text("payload")
    }

    /** The same table, read through a column type that asks for database metadata while decoding. */
    object ProbedTable : Table(TABLE_NAME) {
        val value = registerColumn<Int>("value", VersionProbingColumnType())
        val payload = text("payload")
    }

    /**
     * An integer column that reads database metadata while decoding the last row, exactly like
     * `BasicUuidColumnType.readObject` does through `db.version` when the dialect is MariaDB.
     *
     * Only the last row asks for the version: the first row to ask resolves it for the whole database, and the
     * earlier rows are the ones that may still have been buffered before Exposed subscribed.
     */
    private class VersionProbingColumnType : ColumnType<Int>() {
        private val delegate = IntegerColumnType()

        override fun sqlType(): String = delegate.sqlType()

        override fun valueFromDB(value: Any): Int = delegate.valueFromDB(value)

        override fun readObject(rs: RowApi, index: Int): Any? {
            val value = delegate.readObject(rs, index)

            if ((value as? Number)?.toInt() == LAST_ROW) {
                decodedOn = Thread.currentThread().name

                @OptIn(InternalApi::class)
                val db = currentTransaction().db

                // The property the reported freeze went through.
                reading = "version"
                db.version

                // A sibling resolved the very same way, to keep this test honest about what is being fixed: making
                // `version` available up front leaves this one, and `identifierManager`, blocking exactly as before.
                reading = "fullVersion"
                db.fullVersion

                reading = null
            }

            return value
        }

        companion object {
            /** The thread the last row was decoded on. */
            @Volatile
            var decodedOn: String? = null

            /** The metadata property being read when the row froze, if it did. */
            @Volatile
            var reading: String? = null
        }
    }

    /**
     * A predicate that makes the database pause before it produces each row, so that the last row is pushed to a
     * subscriber that is already waiting for it instead of being buffered before Exposed subscribes.
     */
    private fun sleepPerRow(column: Expression<*>): Op<Boolean> = object : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
            // The sleep reads the row's own value, otherwise the server is free to evaluate it once for the whole
            // query and hand every row over at the same time.
            when (dialect) {
                in TestDB.ALL_MYSQL_MARIADB -> {
                    append("SLEEP(")
                    append(column)
                    append(" * $SLEEP_PER_ROW_SECONDS) = 0")
                }
                in TestDB.ALL_POSTGRES -> {
                    append("PG_SLEEP(")
                    append(column)
                    append(" * $SLEEP_PER_ROW_SECONDS) IS NOT NULL")
                }
                else -> error("No server side sleep expression for $dialect")
            }
        }
    }

    @Test
    fun `reading database metadata while decoding a row does not freeze the driver`() {
        // Only drivers that hand rows over on an event loop thread can freeze, and only these two have a sleep
        // expression above. H2 answers in the calling thread, so it has nothing to block.
        Assumptions.assumeTrue(dialect in TestDB.enabledDialects())
        Assumptions.assumeTrue(dialect in TestDB.ALL_MYSQL_MARIADB + TestDB.ALL_POSTGRES)

        val setup = dialect.connect()
        // The probe gets a database instance of its own: on the instance shared by the suite, `version` was
        // resolved by an earlier test long ago.
        val probe = dialect.connect()

        try {
            onSeparateThread("metadata-freeze-setup") {
                suspendTransaction(db = setup) {
                    SchemaUtils.create(ProbeTable)
                    ProbeTable.deleteAll()
                    ProbeTable.batchInsert(1..LAST_ROW) { row ->
                        this[ProbeTable.value] = row
                        this[ProbeTable.payload] = "x".repeat(PAYLOAD_SIZE)
                    }
                }
            } ?: fail("Preparing the table timed out")

            // Resolves everything else the SELECT needs on this thread - in particular the identifier manager,
            // whose resolution builds the metadata object of the connection it runs on. The query returns no rows,
            // so the probing column type never runs and `version` stays unresolved.
            onSeparateThread("metadata-freeze-warm-up") {
                suspendTransaction(db = probe) {
                    ProbedTable.selectAll().where { ProbedTable.value less 0 }.toList()
                }
            } ?: fail("Warming up the connection metadata timed out")

            val values = onSeparateThread("metadata-freeze-probe") {
                // A new transaction, therefore a new connection with no metadata object of its own.
                suspendTransaction(db = probe) {
                    ProbedTable.selectAll()
                        .where { sleepPerRow(ProbedTable.value) }
                        .map { it[ProbedTable.value] }
                        .toList()
                }
            } ?: fail(
                "The SELECT never finished. Row $LAST_ROW was decoded on ${VersionProbingColumnType.decodedOn} " +
                    "while reading R2dbcDatabase.${VersionProbingColumnType.reading}, where that read calls " +
                    "runBlocking, and the metadata query it waits for can only be answered by that same thread."
            )

            assertEquals((1..LAST_ROW).toList(), values)

            val decodedOn = VersionProbingColumnType.decodedOn.orEmpty()
            Assumptions.assumeTrue(
                decodedOn.startsWith(DRIVER_THREAD_PREFIX),
                "The last row was decoded on $decodedOn instead of a driver thread, so this run says nothing about " +
                    "the freeze."
            )
        } finally {
            // The table is left behind if a driver thread is parked: it still holds the metadata lock taken by the
            // SELECT's transaction, so DROP TABLE would wait for it. The next run empties the table instead.
            onSeparateThread("metadata-freeze-clean-up") {
                suspendTransaction(db = setup) { SchemaUtils.drop(ProbeTable) }
            }
            TransactionManager.closeAndUnregister(probe)
            TransactionManager.closeAndUnregister(setup)
        }
    }

    /**
     * Runs [block] on a throwaway daemon thread and waits [TIMEOUT] for the result, so that a parked driver thread
     * fails this test instead of hanging the whole test JVM. Returns `null` if [block] did not finish in time.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun <T> onSeparateThread(name: String, block: suspend () -> T): T? {
        val outcome = CompletableFuture<T>()

        thread(isDaemon = true, name = name) {
            try {
                outcome.complete(runBlocking { block() })
            } catch (failure: Throwable) {
                outcome.completeExceptionally(failure)
            }
        }

        return try {
            outcome.get(TIMEOUT.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            null
        } catch (failed: ExecutionException) {
            throw failed.cause ?: failed
        }
    }

    companion object {
        private const val TABLE_NAME = "metadata_probe"

        /** The row that reads the database version, and the number of rows the query returns. */
        private const val LAST_ROW = 3

        /** Big enough that the server flushes each row to the network instead of batching the whole result. */
        private const val PAYLOAD_SIZE = 60_000

        private const val SLEEP_PER_ROW_SECONDS = 0.4

        private const val DRIVER_THREAD_PREFIX = "reactor-"

        private val TIMEOUT = 30.seconds
    }
}
