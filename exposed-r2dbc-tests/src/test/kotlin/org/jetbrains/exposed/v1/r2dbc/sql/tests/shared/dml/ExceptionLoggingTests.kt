package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Reproduces issue #547: Exception is printed even when exception is caught
 *
 * The problem: When a SQL exception occurs (e.g., unique constraint violation),
 * Exposed logs the exception with WARN level even when it's properly caught
 * with try-catch. This happens because the logging occurs during transaction
 * retry attempts and commit, before the exception is thrown to user code.
 *
 * Expected behavior: If the exception is caught, it should not be logged,
 * or there should be a way to control this logging behavior.
 */
class ExceptionLoggingTests : R2dbcDatabaseTestsBase() {

    companion object {
        private val logger = LoggerFactory.getLogger(ExceptionLoggingTests::class.java)
    }

    object UsersWithUniqueColumn : IntIdTable("users_with_unique") {
        val name = varchar("name", 50).uniqueIndex()
        val age = integer("age")
    }

    @Test
    fun testExceptionLoggedWhenCaughtOutsideTransaction() {
        withTables(UsersWithUniqueColumn) {
            // Insert first user successfully
            UsersWithUniqueColumn.insert {
                it[name] = "TestUser"
                it[age] = 25
            }

            // Try to insert duplicate - exception will be caught but still logged
            var exceptionCaught = false
            try {
                UsersWithUniqueColumn.insert {
                    it[name] = "TestUser" // Duplicate name
                    it[age] = 30
                }
                fail("Expected ExposedR2dbcException to be thrown")
            } catch (e: ExposedR2dbcException) {
                exceptionCaught = true
                logger.info("Exception caught successfully: ${e.message}")
                // Despite catching here, the exception stack trace is already logged as WARN
            }

            assertEquals(true, exceptionCaught, "Exception should have been caught")

            // Verify only one row exists
            val count = UsersWithUniqueColumn.selectAll().count()
            assertEquals(1, count, "Only one user should exist")
        }
    }

    @Test
    fun testTryCatchOutsideTransaction() {
        // Create table and insert first user in one transaction
        withDb {
            SchemaUtils.create(UsersWithUniqueColumn)

            UsersWithUniqueColumn.insert {
                it[name] = "TestUser"
                it[age] = 25
            }
        }

        // Try-catch is OUTSIDE the transaction block
        var exceptionCaught = false
        try {
            // This is a completely separate transaction
            withDb {
                UsersWithUniqueColumn.insert {
                    it[name] = "TestUser" // Duplicate name
                    it[age] = 30
                }
                fail("Expected ExposedR2dbcException to be thrown")
            }
        } catch (e: ExposedR2dbcException) {
            exceptionCaught = true
            logger.info("Exception caught successfully outside transaction: ${e.message}")
            // Exception should be logged before we catch it here
        }

        assertEquals(true, exceptionCaught, "Exception should have been caught")

        // Verify only one row exists and cleanup
        withDb {
            val count = UsersWithUniqueColumn.selectAll().count()
            assertEquals(1, count, "Only one user should exist")

            SchemaUtils.drop(UsersWithUniqueColumn)
        }
    }
}
