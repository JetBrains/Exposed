package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.concat
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.statements.BatchUpsertStatement
import org.jetbrains.exposed.sql.tests.*
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.junit.Test
import java.util.*
import kotlin.properties.Delegates

// Upsert implementation does not support H2 version 1
// https://youtrack.jetbrains.com/issue/EXPOSED-30/Phase-Out-Support-for-H2-Version-1.x
class UpsertTests : DatabaseTestsBase() {
    // these DB require key columns from ON clause to be included in the derived source table (USING clause)
    private val upsertViaMergeDB = listOf(TestDB.SQLSERVER, TestDB.ORACLE) + TestDB.allH2TestDB - TestDB.H2_MYSQL

    @Test
    fun testUpsertWithPKConflict() {
        withTables(AutoIncTable) { testDb ->
            excludingH2Version1(testDb) {
                val id1 = AutoIncTable.insert {
                    it[name] = "A"
                } get AutoIncTable.id

                AutoIncTable.upsert {
                    if (testDb in upsertViaMergeDB) it[id] = 2
                    it[name] = "B"
                }
                AutoIncTable.upsert {
                    it[id] = id1
                    it[name] = "C"
                }

                assertEquals(2, AutoIncTable.selectAll().count())
                val updatedResult = AutoIncTable.select { AutoIncTable.id eq id1 }.single()
                assertEquals("C", updatedResult[AutoIncTable.name])
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
        withTables(Words) { testDb ->
            excludingH2Version1(testDb) {
                val wordA = Words.upsert {
                    it[word] = "A"
                    it[count] = 10
                } get Words.word
                Words.upsert {
                    it[word] = "B"
                    it[count] = 10
                }
                Words.upsert {
                    it[word] = wordA
                    it[count] = 9
                }

                assertEquals(2, Words.selectAll().count())
                val updatedResult = Words.select { Words.word eq wordA }.single()
                assertEquals(9, updatedResult[Words.count])
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

        withTables(excludeSettings = TestDB.mySqlRelatedDB, tester) { testDb ->
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

        val okWithNoUniquenessDB = TestDB.mySqlRelatedDB + TestDB.SQLITE

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
        withTables(Words) { testDb ->
            excludingH2Version1(testDb) {
                val testWord = "Test"
                val incrementCount = listOf(Words.count to Words.count.plus(1))

                repeat(3) {
                    Words.upsert(onUpdate = incrementCount) {
                        it[word] = testWord
                    }
                }

                assertEquals(3, Words.selectAll().single()[Words.count])
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

        withTables(excludeSettings = TestDB.mySqlRelatedDB + upsertViaMergeDB, tester) {
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

    @Test
    fun testBatchUpsertWithNoConflict() {
        withTables(Words) { testDb ->
            excludingH2Version1(testDb) {
                val amountOfWords = 10
                val allWords = List(amountOfWords) { i -> "Word ${'A' + i}" to amountOfWords * i + amountOfWords }

                val generatedIds = Words.batchUpsert(allWords) { (word, count) ->
                    this[Words.word] = word
                    this[Words.count] = count
                }

                assertEquals(amountOfWords, generatedIds.size)
                assertEquals(amountOfWords.toLong(), Words.selectAll().count())
            }
        }
    }

    @Test
    fun testBatchUpsertWithConflict() {
        withTables(Words) { testDb ->
            excludingH2Version1(testDb) {
                val vowels = listOf("A", "E", "I", "O", "U")
                val alphabet = ('A'..'Z').map { it.toString() }
                val lettersWithDuplicates = alphabet + vowels
                val incrementCount = listOf(Words.count to Words.count.plus(1))

                Words.batchUpsert(lettersWithDuplicates, onUpdate = incrementCount) { letter ->
                    this[Words.word] = letter
                }

                assertEquals(alphabet.size.toLong(), Words.selectAll().count())
                Words.selectAll().forEach {
                    val expectedCount = if (it[Words.word] in vowels) 2 else 1
                    assertEquals(expectedCount, it[Words.count])
                }
            }
        }
    }

    @Test
    fun testBatchUpsertWithSequence() {
        withTables(Words) { testDb ->
            excludingH2Version1(testDb) {
                val amountOfWords = 25
                val allWords = List(amountOfWords) { UUID.randomUUID().toString() }.asSequence()
                Words.batchUpsert(allWords) { word -> this[Words.word] = word }

                val batchesSize = Words.selectAll().count()

                assertEquals(amountOfWords.toLong(), batchesSize)
            }
        }
    }

    @Test
    fun testBatchUpsertWithEmptySequence() {
        withTables(Words) { testDb ->
            excludingH2Version1(testDb) {
                val allWords = emptySequence<String>()
                Words.batchUpsert(allWords) { word -> this[Words.word] = word }

                val batchesSize = Words.selectAll().count()

                assertEquals(0, batchesSize)
            }
        }
    }

    @Test
    fun testInsertedCountWithBatchUpsert() {
        withTables(AutoIncTable) { testDb ->
            excludingH2Version1(testDb) {
                // SQL Server requires statements to be executed before results can be obtained
                val isNotSqlServer = testDb != TestDB.SQLSERVER
                val data = listOf(1 to "A", 2 to "B", 3 to "C")
                val newDataSize = data.size
                var statement: BatchUpsertStatement by Delegates.notNull()

                // all new rows inserted
                AutoIncTable.batchUpsert(data, shouldReturnGeneratedValues = isNotSqlServer) { (id, name) ->
                    statement = this
                    this[AutoIncTable.id] = id
                    this[AutoIncTable.name] = name
                }
                assertEquals(newDataSize, statement.insertedCount)

                // all existing rows set to their current values
                val isH2MysqlMode = testDb == TestDB.H2_MYSQL || testDb == TestDB.H2_MARIADB
                var expected = if (isH2MysqlMode) 0 else newDataSize
                AutoIncTable.batchUpsert(data, shouldReturnGeneratedValues = isNotSqlServer) { (id, name) ->
                    statement = this
                    this[AutoIncTable.id] = id
                    this[AutoIncTable.name] = name
                }
                assertEquals(expected, statement.insertedCount)

                // all existing rows updated & 1 new row inserted
                val updatedData = data.map { it.first to "new${it.second}" } + (4 to "D")
                expected = if (testDb in TestDB.mySqlRelatedDB) newDataSize * 2 + 1 else newDataSize + 1
                AutoIncTable.batchUpsert(updatedData, shouldReturnGeneratedValues = isNotSqlServer) { (id, name) ->
                    statement = this
                    this[AutoIncTable.id] = id
                    this[AutoIncTable.name] = name
                }
                assertEquals(expected, statement.insertedCount)

                assertEquals(updatedData.size.toLong(), AutoIncTable.selectAll().count())
            }
        }
    }

    private object AutoIncTable : Table("auto_inc_table") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 64)

        override val primaryKey = PrimaryKey(id)
    }

    private object Words : Table("words") {
        val word = varchar("name", 64).uniqueIndex()
        val count = integer("count").default(1)
    }
}
