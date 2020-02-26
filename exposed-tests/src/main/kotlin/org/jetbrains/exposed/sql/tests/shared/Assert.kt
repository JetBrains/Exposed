package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.tests.currentDialectIfAvailableTest
import org.jetbrains.exposed.sql.tests.currentDialectTest
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.test.fail

private fun<T> assertEqualCollectionsImpl(collection : Collection<T>, expected : Collection<T>) {
    assertEquals (expected.size, collection.size, "Count mismatch on ${currentDialectTest.name}")
    for (p in collection) {
        assertTrue(expected.any {p == it}, "Unexpected element in collection pair $p on ${currentDialectTest.name}")
    }
}

fun<T> assertEqualCollections (collection : Collection<T>, expected : Collection<T>) {
    assertEqualCollectionsImpl(collection, expected)
}

fun<T> assertEqualCollections (collection : Collection<T>, vararg expected : T) {
    assertEqualCollectionsImpl(collection, expected.toList())
}

fun<T> assertEqualCollections (collection : Iterable<T>, vararg expected : T) {
    assertEqualCollectionsImpl(collection.toList(), expected.toList())
}

fun<T> assertEqualCollections (collection : Iterable<T>, expected : Collection<T>) {
    assertEqualCollectionsImpl(collection.toList(), expected)
}

fun<T> assertEqualLists (l1: List<T>, l2: List<T>) {
    assertEquals(l1.size, l2.size, "Count mismatch on ${currentDialectIfAvailableTest?.name.orEmpty()}")
    for (i in 0 until l1.size)
        assertEquals(l1[i], l2[i], "Error at pos $i on ${currentDialectIfAvailableTest?.name.orEmpty()}:")
}

fun<T> assertEqualLists (l1: List<T>, vararg expected : T) {
    assertEqualLists(l1, expected.toList())
}

fun Transaction.assertTrue(actual: Boolean) = assertTrue(actual, "Failed on ${currentDialectTest.name}")
fun Transaction.assertFalse(actual: Boolean) = kotlin.test.assertFalse(actual, "Failed on ${currentDialectTest.name}")
fun <T> Transaction.assertEquals(exp: T, act: T) = assertEquals(exp, act, "Failed on ${currentDialectTest.name}")
fun <T> Transaction.assertEquals(exp: T, act: List<T>) = assertEquals(exp, act.single(), "Failed on ${currentDialectTest.name}")

fun Transaction.assertFailAndRollback(message: kotlin.String, block: () -> Unit) {
    commit()
    assertFails("Failed on ${currentDialectTest.name}. $message") {
        block()
        commit()
    }

    rollback()
}

inline fun <reified T:Exception> expectException(body: () -> Unit) {
    try {
        body()
        fail("${T::class.simpleName} expected.")
    } catch (e: Exception) {
        if (e !is T) fail("Expected ${T::class.simpleName} but ${e::class.simpleName} thrown.")
    }
}