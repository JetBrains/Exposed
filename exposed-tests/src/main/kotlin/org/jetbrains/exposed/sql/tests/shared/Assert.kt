package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.Transaction
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

fun <T> assertEqualLists(actual: List<T>, vararg expected: T) {
    assertEqualLists(actual, expected.toList())
}

private val Transaction.failedOn: String get() = currentTestDB?.name ?: currentDialectTest.name

fun Transaction.assertTrue(actual: Boolean) = assertTrue(actual, "Failed on $failedOn")
fun Transaction.assertFalse(actual: Boolean) = assertFalse(actual, "Failed on $failedOn")
fun <T> Transaction.assertEquals(exp: T, act: T) = assertEquals(exp, act, "Failed on $failedOn")
fun <T> Transaction.assertEquals(exp: T, act: List<T>) = assertEquals(exp, act.single(), "Failed on $failedOn")

fun Transaction.assertFailAndRollback(message: String, block: () -> Unit) {
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
