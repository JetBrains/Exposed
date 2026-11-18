package org.jetbrains.exposed.v1.tests.shared

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Identifier-manager instances with fixed JDBC-metadata flags, one per identifier-folding family:
 *
 *  - upper-folding (H2 regular/MySQL mode, SQL Server, Oracle): unquoted identifiers are stored upper case.
 *  - lower-folding (PostgreSQL): unquoted identifiers are stored lower case.
 *  - mixed (MySQL, SQLite): unquoted identifiers keep the case they were created with.
 *
 * The instances are pure: their behavior is fully determined by the flags below and no database
 * is touched; the surrounding transaction only supplies the ambient config/dialect lookups that
 * [IdentifierManagerApi] reads.
 */
private class SimulatedIdentifierManager(
    override val quoteString: String = "\"",
    private val upper: Boolean = false,
    private val lower: Boolean = false,
    private val mixed: Boolean = false,
    private val quotedMixed: Boolean = true
) : IdentifierManagerApi() {
    override val isUpperCaseIdentifiers = upper
    override val isUpperCaseQuotedIdentifiers = false
    override val isLowerCaseIdentifiers = lower
    override val isLowerCaseQuotedIdentifiers = false
    override val supportsMixedIdentifiers = mixed
    override val supportsMixedQuotedIdentifiers = quotedMixed
    override fun dbKeywords(): List<String> = emptyList()
    override val extraNameCharacters = "$"
    override val oracleVersion = OracleVersion.NonOracle
    override val maxColumnNameLength = 128
}

/** The semantics of `quoteIdentifierWhenWrongCaseOrNecessary`, per dialect-folding family. */
class IdentifierQuotingSemanticsTest : DatabaseTestsBase() {

    private fun upperFolding() = SimulatedIdentifierManager(upper = true)
    private fun lowerFolding() = SimulatedIdentifierManager(lower = true)
    private fun mixedCase() = SimulatedIdentifierManager(mixed = true)

    @Test
    fun `all-caps identifier on a lower-folding dialect is quoted with its case preserved`() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        withDb {
            // PostgreSQL-style: unquoted identifiers fold to lower case, so an upper-case identifier
            // must be quoted to survive, exactly as provided.
            assertEquals("\"MY_COLUMN\"", lowerFolding().quoteIdentifierWhenWrongCaseOrNecessary("MY_COLUMN"))
            assertEquals("\"G\"", lowerFolding().quoteIdentifierWhenWrongCaseOrNecessary("G"))
        }
    }

    @Test
    fun `all-lower identifier on an upper-folding dialect is quoted with its case preserved`() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        withDb {
            // H2-style: unquoted identifiers fold to upper case, so a lower-case identifier must be
            // quoted to survive, exactly as provided.
            assertEquals("\"something\"", upperFolding().quoteIdentifierWhenWrongCaseOrNecessary("something"))
            assertEquals("\"my_column\"", upperFolding().quoteIdentifierWhenWrongCaseOrNecessary("my_column"))
        }
    }

    @Test
    fun `camelCase identifier is quoted with its case preserved on folding dialects`() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        withDb {
            assertEquals("\"someThing\"", upperFolding().quoteIdentifierWhenWrongCaseOrNecessary("someThing"))
            assertEquals("\"someThing\"", lowerFolding().quoteIdentifierWhenWrongCaseOrNecessary("someThing"))
        }
    }

    @Test
    fun `identifier already equal to the dialect default folding is emitted unfolded`() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        withDb {
            // Upper-case identifier against an upper-folding dialect needs no quotes.
            assertEquals("MY_COLUMN", upperFolding().quoteIdentifierWhenWrongCaseOrNecessary("MY_COLUMN"))
            assertEquals("SOMETHING", upperFolding().quoteIdentifierWhenWrongCaseOrNecessary("SOMETHING"))
            // Lower-case identifier against a lower-folding dialect needs no quotes.
            assertEquals("my_column", lowerFolding().quoteIdentifierWhenWrongCaseOrNecessary("my_column"))
            assertEquals("something", lowerFolding().quoteIdentifierWhenWrongCaseOrNecessary("something"))
            // Any identifier against a mixed-case dialect needs no quotes.
            assertEquals("MY_COLUMN", mixedCase().quoteIdentifierWhenWrongCaseOrNecessary("MY_COLUMN"))
            assertEquals("something", mixedCase().quoteIdentifierWhenWrongCaseOrNecessary("something"))
            assertEquals("someThing", mixedCase().quoteIdentifierWhenWrongCaseOrNecessary("someThing"))
        }
    }

    @Test
    fun `keyword identifier is quoted with its case preserved`() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        withDb {
            // "G" is part of the SQL:2003 keyword list; keyword identifiers keep the dedicated
            // keyword handling: always quoted, case preserved (preserveKeywordCasing default).
            assertEquals("\"G\"", upperFolding().quoteIdentifierWhenWrongCaseOrNecessary("G"))
            assertEquals("\"G\"", lowerFolding().quoteIdentifierWhenWrongCaseOrNecessary("G"))
            assertEquals("\"user\"", upperFolding().quoteIdentifierWhenWrongCaseOrNecessary("user"))
            assertEquals("\"user\"", lowerFolding().quoteIdentifierWhenWrongCaseOrNecessary("user"))
        }
    }

    @Test
    fun `identifier with reserved characters is always quoted`() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        withDb {
            assertEquals("\"my col\"", upperFolding().quoteIdentifierWhenWrongCaseOrNecessary("my col"))
            assertEquals("\"my col\"", lowerFolding().quoteIdentifierWhenWrongCaseOrNecessary("my col"))
            assertEquals("\"my col\"", mixedCase().quoteIdentifierWhenWrongCaseOrNecessary("my col"))
        }
    }

    @Test
    fun `already quoted identifier passes through unchanged`() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        withDb {
            assertEquals("\"G\"", upperFolding().quoteIdentifierWhenWrongCaseOrNecessary("\"G\""))
            assertEquals("\"someThing\"", upperFolding().quoteIdentifierWhenWrongCaseOrNecessary("\"someThing\""))
            assertEquals("\"G\"", lowerFolding().quoteIdentifierWhenWrongCaseOrNecessary("\"G\""))
            assertEquals("\"someThing\"", lowerFolding().quoteIdentifierWhenWrongCaseOrNecessary("\"someThing\""))
        }
    }

    @Test
    fun `ddl keeps the provided column case on upper-folding H2`() {
        withDb(TestDB.H2_V2_MYSQL) {
            val tester = object : Table("ddl_case_683") {
                val c1 = integer("MYCOL")
                val c2 = integer("something")
                val c3 = integer("someThing")
                val c4 = integer("my col")
            }

            // H2 (MySQL mode) folds unquoted identifiers to upper case, so:
            // - 'MYCOL' equals that folding and is emitted unfolded;
            // - 'something', 'someThing' and 'my col' differ from it and are quoted, keeping the exact case.
            assertEquals("MYCOL INT NOT NULL", tester.c1.descriptionDdl(false))
            assertEquals("\"something\" INT NOT NULL", tester.c2.descriptionDdl(false))
            assertEquals("\"someThing\" INT NOT NULL", tester.c3.descriptionDdl(false))
            assertEquals("\"my col\" INT NOT NULL", tester.c4.descriptionDdl(false))

            // The generated identifiers round-trip through an actual table.
            SchemaUtils.create(tester)
            tester.insert {
                it[c1] = 1
                it[c2] = 2
                it[c3] = 3
                it[c4] = 4
            }
            val row = tester.selectAll().single()
            assertEquals(1, row[tester.c1])
            assertEquals(2, row[tester.c2])
            assertEquals(3, row[tester.c3])
            assertEquals(4, row[tester.c4])
            SchemaUtils.drop(tester)
        }
    }

    @Test
    fun `ddl keeps the provided column case on lower-folding H2`() {
        withDb(TestDB.H2_V2_PSQL) {
            val tester = object : Table("ddl_case_683") {
                val c1 = integer("MYCOL")
                val c2 = integer("something")
                val c3 = integer("someThing")
            }

            // H2 (PostgreSQL mode) folds unquoted identifiers to lower case, so:
            // - 'MYCOL' differs from that folding and is quoted, keeping the exact case;
            // - 'something' equals it and is emitted unfolded.
            assertEquals("\"MYCOL\" INT NOT NULL", tester.c1.descriptionDdl(false))
            assertEquals("something INT NOT NULL", tester.c2.descriptionDdl(false))
            assertEquals("\"someThing\" INT NOT NULL", tester.c3.descriptionDdl(false))

            // The generated identifiers round-trip through an actual table.
            SchemaUtils.create(tester)
            tester.insert {
                it[c1] = 1
                it[c2] = 2
                it[c3] = 3
            }
            val row = tester.selectAll().single()
            assertEquals(1, row[tester.c1])
            assertEquals(2, row[tester.c2])
            assertEquals(3, row[tester.c3])
            SchemaUtils.drop(tester)
        }
    }
}
