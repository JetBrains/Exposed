package org.jetbrains.exposed.sql.tests.h2

import org.junit.Test
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.*
import kotlin.test.assertEquals

object ViaTestData {
    object NumbersTable: IdTable() {
        val number = integer("number")
    }

    object StringsTable: IdTable() {
        val text = varchar("text", 10)
    }

    object ConnectionTable: Table() {
        val numId = reference("numId", NumbersTable, ReferenceOption.CASCADE)
        val stringId = reference("stringId", StringsTable, ReferenceOption.CASCADE)

        init {
            index(true, numId, stringId)
        }
    }

    val allTables: Array<Table> = arrayOf(NumbersTable, StringsTable, ConnectionTable)
}

class VNumber(id: EntityID): Entity(id) {
    var number by ViaTestData.NumbersTable.number
    var connectedStrings: SizedIterable<VString> by VString via ViaTestData.ConnectionTable

    companion object : EntityClass<VNumber>(ViaTestData.NumbersTable)
}

class VString(id: EntityID): Entity(id) {
    var text by ViaTestData.StringsTable.text
    companion object : EntityClass<VString>(ViaTestData.StringsTable)
}


class ViaTests : DatabaseTestsBase() {
    @Test fun testConnection01() {
        withTables(*ViaTestData.allTables) {
            val n = VNumber.new { number = 10 }
            val s = VString.new { text = "aaa" }
            n.connectedStrings = SizedCollection<VString>(listOf(s))

            val row = ViaTestData.ConnectionTable.selectAll().single()
            assertEquals (n.id, row[ViaTestData.ConnectionTable.numId])
            assertEquals (s.id, row[ViaTestData.ConnectionTable.stringId])
        }
    }

    @Test fun testConnection02() {
        withTables(*ViaTestData.allTables) {
            val n1 = VNumber.new { number = 1 }
            val n2 = VNumber.new { number = 2 }
            val s1 = VString.new { text = "aaa" }
            val s2 = VString.new { text = "bbb" }

            n1.connectedStrings = SizedCollection<VString>(listOf(s1, s2))

            val row = ViaTestData.ConnectionTable.selectAll().toList()
            assertEquals(2, row.count())
            assertEquals (n1.id, row[0][ViaTestData.ConnectionTable.numId])
            assertEquals (n1.id, row[1][ViaTestData.ConnectionTable.numId])
            assertEqualCollections (listOf(s1.id, s2.id), row.map { it[ViaTestData.ConnectionTable.stringId] })
        }
    }

    @Test fun testConnection03() {
        withTables(*ViaTestData.allTables) {
            val n1 = VNumber.new { number = 1 }
            val n2 = VNumber.new { number = 2 }
            val s1 = VString.new { text = "aaa" }
            val s2 = VString.new { text = "bbb" }

            n1.connectedStrings = SizedCollection<VString>(listOf(s1, s2))
            n2.connectedStrings = SizedCollection<VString>(listOf(s1, s2))

            run {
                val row = ViaTestData.ConnectionTable.selectAll().toList()
                assertEquals(4, row.count())
                assertEqualCollections(n1.connectedStrings, listOf(s1, s2))
                assertEqualCollections(n2.connectedStrings, listOf(s1, s2))
            }

            n1.connectedStrings = SizedCollection<VString>(emptyList())

            run {
                val row = ViaTestData.ConnectionTable.selectAll().toList()
                assertEquals(2, row.count())
                assertEquals (n2.id, row[0][ViaTestData.ConnectionTable.numId])
                assertEquals (n2.id, row[1][ViaTestData.ConnectionTable.numId])
                assertEqualCollections(n1.connectedStrings, emptyList())
                assertEqualCollections(n2.connectedStrings, listOf(s1, s2))
            }

        }
    }

    @Test fun testConnection04() {
        withTables(*ViaTestData.allTables) {
            val n1 = VNumber.new { number = 1 }
            val n2 = VNumber.new { number = 2 }
            val s1 = VString.new { text = "aaa" }
            val s2 = VString.new { text = "bbb" }

            n1.connectedStrings = SizedCollection<VString>(listOf(s1, s2))
            n2.connectedStrings = SizedCollection<VString>(listOf(s1, s2))

            run {
                val row = ViaTestData.ConnectionTable.selectAll().toList()
                assertEquals(4, row.count())
                assertEqualCollections(n1.connectedStrings, listOf(s1, s2))
                assertEqualCollections(n2.connectedStrings, listOf(s1, s2))
            }

            n1.connectedStrings = SizedCollection<VString>(listOf(s1))

            run {
                val row = ViaTestData.ConnectionTable.selectAll().toList()
                assertEquals(3, row.count())
                assertEqualCollections(n1.connectedStrings, listOf(s1))
                assertEqualCollections(n2.connectedStrings, listOf(s1, s2))
            }
        }
    }
}
