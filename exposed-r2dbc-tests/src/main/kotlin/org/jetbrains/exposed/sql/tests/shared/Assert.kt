package org.jetbrains.exposed.sql.tests.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.sql.R2dbcTransaction
import org.jetbrains.exposed.sql.tests.currentDialectIfAvailableTest
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.currentTestDB
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

fun <T> assertEqualLists(actual: List<T>, expected: List<T>) {
    assertEquals(actual.size, expected.size, "Count mismatch on ${currentDialectIfAvailableTest?.name.orEmpty()}")
    expected.forEachIndexed { index, exp ->
        val act = actual.getOrElse(index) {
            fail("Value absent at pos $index on ${currentDialectIfAvailableTest?.name.orEmpty()}")
        }
        assertEquals(act, exp, "Error at pos $index on ${currentDialectIfAvailableTest?.name.orEmpty()}:")
    }
}

suspend fun <T> assertEqualLists(actual: Flow<T>, expected: List<T>) = assertEqualLists(actual.toList(), expected)

suspend fun <T> assertEqualLists(actual: Flow<T>, expected: Flow<T>) = assertEqualLists(actual.toList(), expected)

fun <T> assertEqualLists(actual: List<T>, vararg expected: T) = assertEqualLists(actual, expected.toList())

suspend fun <T> assertEqualLists(actual: Flow<T>, vararg expected: T) = assertEqualLists(actual.toList(), expected.toList())

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
