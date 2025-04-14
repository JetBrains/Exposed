package org.jetbrains.exposed.r2dbc.sql.tests.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.r2dbc.sql.tests.currentDialectIfAvailableTest
import org.jetbrains.exposed.r2dbc.sql.tests.currentDialectTest
import org.jetbrains.exposed.r2dbc.sql.tests.currentTestDB
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

private fun <T> assertEqualCollectionsImpl(collection: Collection<T>, expected: Collection<T>) {
    assertEquals(expected.size, collection.size, "Count mismatch on ${currentDialectIfAvailableTest?.name ?: "N/A"}")
    assertEquals(expected.toSet(), collection.toSet())
}

fun <T> assertEqualCollections(actual: Collection<T>, expected: Collection<T>) {
    assertEqualCollectionsImpl(actual, expected)
}

fun <T> assertEqualCollections(actual: Collection<T>, vararg expected: T) {
    assertEqualCollectionsImpl(actual, expected.toList())
}

fun <T> assertEqualCollections(actual: Iterable<T>, vararg expected: T) {
    assertEqualCollectionsImpl(actual.toList(), expected.toList())
}

fun <T> assertEqualCollections(actual: Iterable<T>, expected: Collection<T>) {
    assertEqualCollectionsImpl(actual.toList(), expected)
}

suspend fun <T> assertEqualCollections(actual: Flow<T>, expected: Collection<T>) = assertEqualCollections(actual.toList(), expected)

fun <T> assertEqualLists(expected: List<T>, actual: List<T>) {
    assertEquals(expected.size, actual.size, "Count mismatch on ${currentDialectIfAvailableTest?.name.orEmpty()}")
    expected.forEachIndexed { index, exp ->
        val act = actual.getOrElse(index) {
            fail("Value absent at pos $index on ${currentDialectIfAvailableTest?.name.orEmpty()}")
        }
        assertEquals(expected, actual, "Error at pos $index on ${currentDialectIfAvailableTest?.name.orEmpty()}:")
    }
}

suspend fun <T> assertEqualLists(expected: Flow<T>, actual: List<T>) = assertEqualLists(expected.toList(), actual)

suspend fun <T> assertEqualLists(expected: List<T>, actual: Flow<T>) = assertEqualLists(expected, actual.toList())

suspend fun <T> assertEqualLists(expected: Flow<T>, actual: Flow<T>) = assertEqualLists(expected.toList(), actual.toList())

private val R2dbcTransaction.failedOn: String get() = currentTestDB?.name ?: currentDialectTest.name

fun R2dbcTransaction.assertTrue(actual: Boolean) = assertTrue(actual, "Failed on $failedOn")
fun R2dbcTransaction.assertFalse(actual: Boolean) = assertFalse(actual, "Failed on $failedOn")
fun <T> R2dbcTransaction.assertEquals(exp: T, act: T) = assertEquals(exp, act, "Failed on $failedOn")
fun <T> R2dbcTransaction.assertEquals(exp: T, act: List<T>) = assertEquals(exp, act.single(), "Failed on $failedOn")

suspend fun R2dbcTransaction.assertFailAndRollback(message: String, block: suspend () -> Unit) {
    commit()
    assertFails("Failed on ${currentDialectTest.name}. $message") {
        block()
        commit()
    }

    rollback()
}

inline fun <reified T : Throwable> expectException(body: () -> Unit) {
    assertFailsWith<T>(block = body, message = "Failed on ${currentDialectTest.name}.")
}
