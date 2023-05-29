package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.concat
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.tests.*
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.junit.Test

// Upsert implementation does not support H2 version 1
// https://youtrack.jetbrains.com/issue/EXPOSED-30/Phase-Out-Support-for-H2-Version-1.x
class UpsertTests : DatabaseTestsBase() {
    // these DB require key columns from ON clause to be included in the derived source table (USING clause)
    private val upsertViaMergeDB = listOf(TestDB.SQLSERVER, TestDB.ORACLE) + TestDB.allH2TestDB - TestDB.H2_MYSQL

    private val mySqlLikeDB = listOf(TestDB.MYSQL, TestDB.H2_MYSQL, TestDB.MARIADB, TestDB.H2_MARIADB)

    @Test
    fun testUpsertWithPKConflict() {
        val tester = object : Table("tester") {
            val id = integer("id").autoIncrement()
            val name = varchar("name", 64)

            override val primaryKey = PrimaryKey(id)
        }

        withTables(tester) { testDb ->
            excludingH2Version1(testDb) {
                val id1 = tester.insert {
                    it[name] = "A"
                } get tester.id

                tester.upsert {
                    if (testDb in upsertViaMergeDB) it[id] = 2
                    it[name] = "B"
                }
                tester.upsert {
                    it[id] = id1
                    it[name] = "C"
                }

                assertEquals(2, tester.selectAll().count())
                val updatedResult = tester.select { tester.id eq id1 }.single()
                assertEquals("C", updatedResult[tester.name])
            }
        }
    }

    @Test
    fun testUpsertWithCompositePKConflict() {
        val tester = object : Table("tester") {
            val idA = integer("id_a")
            val idB = integer("id_b")
            val name = varchar("name", 64)

            override val primaryKey = PrimaryKey(idA, idB)
        }

        withTables(tester) { testDb ->
            excludingH2Version1(testDb) {
                val insertStmt = tester.insert {
                    it[idA] = 1
                    it[idB] = 1
                    it[name] = "A"
                }

                tester.upsert {  // insert because only 1 constraint is equal
                    it[idA] = 7
                    it[idB] = insertStmt get tester.idB
                    it[name] = "B"
                }
                tester.upsert {  // insert because both constraints differ
                    it[idA] = 99
                    it[idB] = 99
                    it[name] = "C"
                }
                tester.upsert {  // update because both constraints match
                    it[idA] = insertStmt get tester.idA
                    it[idB] = insertStmt get tester.idB
                    it[name] = "D"
                }

                assertEquals(3, tester.selectAll().count())
                val updatedResult = tester.select { tester.idA eq insertStmt[tester.idA] }.single()
                assertEquals("D", updatedResult[tester.name])
            }
        }
    }

    @Test
    fun testUpsertWithUniqueIndexConflict() {
        val tester = object : Table("tester") {
            val name = varchar("name", 64).uniqueIndex()
            val age = integer("age")
        }

        withTables(tester) { testDb ->
            excludingH2Version1(testDb) {
                val nameA = tester.upsert {
                    it[name] = "A"
                    it[age] = 10
                } get tester.name
                tester.upsert {
                    it[name] = "B"
                    it[age] = 10
                }
                tester.upsert {
                    it[name] = "A"
                    it[age] = 9
                }

                assertEquals(2, tester.selectAll().count())
                val updatedResult = tester.select { tester.name eq nameA }.single()
                assertEquals(9, updatedResult[tester.age])
            }
        }
    }

    @Test
    fun testUpsertWithManualConflictKeys() {
        val tester = object : Table("tester") {
            val idA = integer("id_a").uniqueIndex()
            val idB = integer("id_b").uniqueIndex()
            val name = varchar("name", 64)
        }

        withTables(excludeSettings = mySqlLikeDB, tester) { testDb ->
            excludingH2Version1(testDb) {
                val oldIdA = tester.insert {
                    it[idA] = 1
                    it[idB] = 1
                    it[name] = "A"
                } get tester.idA

                val newIdB = tester.upsert(tester.idA) {
                    it[idA] = oldIdA
                    it[idB] = 2
                    it[name] = "B"
                } get tester.idB
                assertEquals("B", tester.selectAll().single()[tester.name])

                val newIdA = tester.upsert(tester.idB) {
                    it[idA] = 99
                    it[idB] = newIdB
                    it[name] = "C"
                } get tester.idA
                assertEquals("C", tester.selectAll().single()[tester.name])

                if (testDb in upsertViaMergeDB) {
                    // passes since these DB use 'AND' within ON clause (other DB require single uniqueness constraint)
                    tester.upsert(tester.idA, tester.idB) {
                        it[idA] = newIdA
                        it[idB] = newIdB
                        it[name] = "D"
                    }

                    val result = tester.selectAll().single()
                    assertEquals(newIdA, result[tester.idA])
                    assertEquals(newIdB, result[tester.idB])
                    assertEquals("D", result[tester.name])
                }
            }
        }
    }

    @Test
    fun testUpsertWithNoUniqueConstraints() {
        val tester = object : Table("tester") {
            val name = varchar("name", 64)
        }

        val okWithNoUniquenessDB = mySqlLikeDB + listOf(TestDB.SQLITE)

        withTables(tester) { testDb ->
            excludingH2Version1(testDb) {
                if (testDb in okWithNoUniquenessDB) {
                    tester.upsert {
                        it[name] = "A"
                    }
                    assertEquals(1, tester.selectAll().count())
                } else {
                    expectException<UnsupportedByDialectException> {
                        tester.upsert {
                            it[name] = "A"
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testUpsertWithManualUpdateAssignment() {
        val tester = object : Table("tester") {
            val word = varchar("word", 256).uniqueIndex()
            val count = integer("count").default(1)
        }

        withTables(tester) { testDb ->
            excludingH2Version1(testDb) {
                val testWord = "Test"
                val incrementCount = listOf(tester.count to tester.count.plus(1))

                repeat(3) {
                    tester.upsert(onUpdate = incrementCount) {
                        it[word] = testWord
                    }
                }

                assertEquals(3, tester.selectAll().single()[tester.count])
            }
        }
    }

    @Test
    fun testUpsertWithMultipleManualUpdates() {
        val tester = object : Table("tester") {
            val item = varchar("item", 64).uniqueIndex()
            val amount = integer("amount").default(25)
            val gains = integer("gains").default(100)
            val losses = integer("losses").default(100)
        }

        withTables(tester) { testDb ->
            excludingH2Version1(testDb) {
                val itemA = tester.upsert {
                    it[item] = "Item A"
                } get tester.item

                val adjustGainAndLoss = listOf(
                    tester.gains to tester.gains.plus(tester.amount),
                    tester.losses to tester.losses.minus(tester.amount)
                )
                tester.upsert(onUpdate = adjustGainAndLoss) {
                    it[item] = "Item B"
                    it[gains] = 200
                    it[losses] = 0
                }

                val insertResult = tester.select { tester.item neq itemA }.single()
                assertEquals(200, insertResult[tester.gains])
                assertEquals(0, insertResult[tester.losses])

                tester.upsert(onUpdate = adjustGainAndLoss) {
                    it[item] = itemA
                    it[gains] = 200
                    it[losses] = 0
                }

                val updateResult = tester.select { tester.item eq itemA }.single()
                assertEquals(125, updateResult[tester.gains])
                assertEquals(75, updateResult[tester.losses])
            }
        }
    }

    @Test
    fun testUpsertWithColumnExpressions() {
        val defaultPhrase = "Phrase"
        val tester = object : Table("tester") {
            val word = varchar("word", 256).uniqueIndex()
            val phrase = varchar("phrase", 256).defaultExpression(stringParam(defaultPhrase))
        }

        withTables(tester) { testDb ->
            excludingH2Version1(testDb) {
                val testWord = "Test"
                tester.upsert {  // default expression in insert
                    it[word] = testWord
                }
                assertEquals("Phrase", tester.selectAll().single()[tester.phrase])

                val phraseConcat = concat(" - ", listOf(tester.word, tester.phrase))
                tester.upsert(onUpdate = listOf(tester.phrase to phraseConcat)) {  // expression in update
                    it[word] = testWord
                }
                assertEquals("$testWord - $defaultPhrase", tester.selectAll().single()[tester.phrase])

                tester.upsert {  // provided expression in insert
                    it[word] = "$testWord 2"
                    it[phrase] = concat(stringLiteral("foo"), stringLiteral("bar"))
                }
                assertEquals("foobar", tester.select { tester.word eq "$testWord 2" }.single()[tester.phrase])
            }
        }
    }

    @Test
    fun testUpsertWithWhere() {
        val tester = object : IntIdTable("tester") {
            val name = varchar("name", 64).uniqueIndex()
            val address = varchar("address", 256)
            val age = integer("age")
        }

        withTables(excludeSettings = mySqlLikeDB + upsertViaMergeDB, tester) {
            val id1 = tester.insertAndGetId {
                it[name] = "A"
                it[address] = "Place A"
                it[age] = 10
            }
            val unchanged = tester.insert {
                it[name] = "B"
                it[address] = "Place B"
                it[age] = 50
            }

            val ageTooLow = tester.age less intLiteral(15)
            val updatedAge = tester.upsert(tester.name, where = { ageTooLow }) {
                it[name] = "A"
                it[address] = "Address A"
                it[age] = 20
            } get tester.age

            tester.upsert(tester.name, where = { ageTooLow }) {
                it[name] = "B"
                it[address] = "Address B"
                it[age] = 20
            }

            assertEquals(2, tester.selectAll().count())
            val unchangedResult = tester.select { tester.id eq unchanged[tester.id] }.single()
            assertEquals(unchanged[tester.address], unchangedResult[tester.address])
            val updatedResult = tester.select { tester.id eq id1 }.single()
            assertEquals(updatedAge, updatedResult[tester.age])
        }
    }

    @Test
    fun testUpsertWithSubQuery() {
        val tester1 = object : IntIdTable("tester_1") {
            val name = varchar("name", 32)
        }
        val tester2 = object : IntIdTable("tester_2") {
            val name = varchar("name", 32)
        }

        withTables(tester1, tester2) { testDb ->
            excludingH2Version1(testDb) {
                val id1 = tester1.insertAndGetId {
                    it[name] = "foo"
                }
                val id2 = tester1.insertAndGetId {
                    it[name] = "bar"
                }

                val query1 = tester1.slice(tester1.name).select { tester1.id eq id1 }
                val id3 = tester2.upsert {
                    if (testDb in upsertViaMergeDB) it[id] = 1
                    it[name] = query1
                } get tester2.id
                assertEquals("foo", tester2.selectAll().single()[tester2.name])

                val query2 = tester1.slice(tester1.name).select { tester1.id eq id2 }
                tester2.upsert {
                    it[id] = id3
                    it[name] = query2
                }
                assertEquals("bar", tester2.selectAll().single()[tester2.name])
            }
        }
    }
}
