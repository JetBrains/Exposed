package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.concat
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
import org.jetbrains.exposed.sql.statements.BatchUpsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.statements.UpsertBuilder
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.*
import kotlin.properties.Delegates
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@Suppress("LargeClass")
class UpsertTests : DatabaseTestsBase() {
    // these DB require key columns from ON clause to be included in the derived source table (USING clause)
    private val upsertViaMergeDB = TestDB.ALL_H2 + TestDB.SQLSERVER + TestDB.ORACLE

    @Test
    fun testUpsertWithPKConflict() {
        withTables(excludeSettings = TestDB.ALL_H2_V1, AutoIncTable) { testDb ->
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
            val updatedResult = AutoIncTable.selectAll().where { AutoIncTable.id eq id1 }.single()
            assertEquals("C", updatedResult[AutoIncTable.name])
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

        withTables(excludeSettings = TestDB.ALL_H2_V1, tester) {
            val insertStmt = tester.insert {
                it[idA] = 1
                it[idB] = 1
                it[name] = "A"
            }

            tester.upsert { // insert because only 1 constraint is equal
                it[idA] = 7
                it[idB] = insertStmt get tester.idB
                it[name] = "B"
            }
            tester.upsert { // insert because both constraints differ
                it[idA] = 99
                it[idB] = 99
                it[name] = "C"
            }
            tester.upsert { // update because both constraints match
                it[idA] = insertStmt get tester.idA
                it[idB] = insertStmt get tester.idB
                it[name] = "D"
            }

            assertEquals(3, tester.selectAll().count())
            val updatedResult = tester.selectAll().where { tester.idA eq insertStmt[tester.idA] }.single()
            assertEquals("D", updatedResult[tester.name])
        }
    }

    @Test
    fun testUpsertWithAllColumnsInPK() {
        val tester = object : Table("tester") {
            val userId = varchar("user_id", 32)
            val keyId = varchar("key_id", 32)
            override val primaryKey = PrimaryKey(userId, keyId)
        }

        fun upsertOnlyKeyColumns(values: Pair<String, String>) {
            tester.upsert {
                it[userId] = values.first
                it[keyId] = values.second
            }
        }

        withTables(excludeSettings = TestDB.ALL_H2_V1, tester) { testDb ->
            val primaryKeyValues = Pair("User A", "Key A")
            // insert new row
            upsertOnlyKeyColumns(primaryKeyValues)
            // 'update' existing row to have identical values
            upsertOnlyKeyColumns(primaryKeyValues)

            val result = tester.selectAll().singleOrNull()
            assertNotNull(result)

            val resultValues = Pair(result[tester.userId], result[tester.keyId])
            assertEquals(primaryKeyValues, resultValues)
        }
    }

    @Test
    fun testUpsertWithUniqueIndexConflict() {
        withTables(excludeSettings = TestDB.ALL_H2_V1, Words) {
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
            val updatedResult = Words.selectAll().where { Words.word eq wordA }.single()
            assertEquals(9, updatedResult[Words.count])
        }
    }

    @Test
    fun testUpsertWithManualConflictKeys() {
        val tester = object : Table("tester") {
            val idA = integer("id_a").uniqueIndex()
            val idB = integer("id_b").uniqueIndex()
            val name = varchar("name", 64)
        }

        withTables(excludeSettings = TestDB.ALL_MYSQL_LIKE + TestDB.ALL_H2_V1, tester) { testDb ->
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

    @Test
    fun testUpsertWithUUIDKeyConflict() {
        val tester = object : Table("tester") {
            val id = uuid("id").autoGenerate()
            val title = text("title")

            override val primaryKey = PrimaryKey(id)
        }

        withTables(excludeSettings = TestDB.ALL_H2_V1, tester) {
            val uuid1 = tester.upsert {
                it[title] = "A"
            } get tester.id
            tester.upsert {
                it[id] = uuid1
                it[title] = "B"
            }

            val result = tester.selectAll().single()
            assertEquals(uuid1, result[tester.id])
            assertEquals("B", result[tester.title])
        }
    }

    @Test
    fun testUpsertWithNoUniqueConstraints() {
        val tester = object : Table("tester") {
            val name = varchar("name", 64)
        }

        val okWithNoUniquenessDB = TestDB.ALL_MYSQL_LIKE + TestDB.SQLITE

        withTables(excludeSettings = TestDB.ALL_H2_V1, tester) { testDb ->
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

    @Test
    fun testUpsertWithManualUpdateAssignment() {
        withTables(excludeSettings = TestDB.ALL_H2_V1, Words) {
            val testWord = "Test"

            repeat(3) {
                Words.upsert(onUpdate = { it[Words.count] = Words.count + 1 }) {
                    it[word] = testWord
                }
            }

            assertEquals(3, Words.selectAll().single()[Words.count])

            val updatedCount = 1000
            Words.upsert(onUpdate = { it[Words.count] = 1000 }) {
                it[word] = testWord
            }
            assertEquals(updatedCount, Words.selectAll().single()[Words.count])
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

        fun UpsertBuilder.adjustGainAndLoss(statement: UpdateStatement) {
            statement[tester.gains] = tester.gains + tester.amount
            statement[tester.losses] = tester.losses - insertValue(tester.amount)
        }

        withTables(excludeSettings = TestDB.ALL_H2_V1, tester) {
            val itemA = tester.upsert {
                it[item] = "Item A"
            } get tester.item

            tester.upsert(onUpdate = { adjustGainAndLoss(it) }) {
                it[item] = "Item B"
                it[gains] = 200
                it[losses] = 0
                // `amount` must be passed explicitly now due to usage of that column inside the custom onUpdate statement
                // There is an option to call `tester.amount.defaultValueFun?.let { it() }!!`,
                // it looks ugly but prevents regression on changes in default value
                it[amount] = 25
            }

            val insertResult = tester.selectAll().where { tester.item neq itemA }.single()
            assertEquals(25, insertResult[tester.amount])
            assertEquals(200, insertResult[tester.gains])
            assertEquals(0, insertResult[tester.losses])

            tester.upsert(onUpdate = { adjustGainAndLoss(it) }) {
                it[item] = itemA
                it[amount] = 10
                it[gains] = 200
                it[losses] = 0
            }

            val updateResult = tester.selectAll().where { tester.item eq itemA }.single()
            assertEquals(125, updateResult[tester.gains])
            assertEquals(90, updateResult[tester.losses])
        }
    }

    @Test
    fun testUpsertWithColumnExpressions() {
        val defaultPhrase = "Phrase"
        val tester = object : Table("tester") {
            val word = varchar("word", 256).uniqueIndex()
            val phrase = varchar("phrase", 256).defaultExpression(stringParam(defaultPhrase))
        }

        withTables(excludeSettings = TestDB.ALL_H2_V1, tester) {
            val testWord = "Test"
            tester.upsert { // default expression in insert
                it[word] = testWord
            }
            assertEquals("Phrase", tester.selectAll().single()[tester.phrase])

            tester.upsert(
                onUpdate = { it[tester.phrase] = concat(" - ", listOf(tester.word, tester.phrase)) }
            ) { // expression in update
                it[word] = testWord
            }
            assertEquals("$testWord - $defaultPhrase", tester.selectAll().single()[tester.phrase])

            val multilinePhrase = """
                This is a phrase with a new line
                and some other difficult strings '

                Indentation should be preserved
            """.trimIndent()
            tester.upsert(
                onUpdate = { it[tester.phrase] = multilinePhrase }
            ) {
                it[word] = testWord
            }
            assertEquals(multilinePhrase, tester.selectAll().single()[tester.phrase])

            tester.upsert { // provided expression in insert
                it[word] = "$testWord 2"
                it[phrase] = concat(stringLiteral("foo"), stringLiteral("bar"))
            }
            assertEquals("foobar", tester.selectAll().where { tester.word eq "$testWord 2" }.single()[tester.phrase])
        }
    }

    @Test
    fun testUpsertWithManualUpdateUsingInsertValues() {
        val tester = object : Table("tester") {
            val id = integer("id").uniqueIndex()
            val word = varchar("name", 64)
            val count = integer("count").default(1)
        }

        withTables(excludeSettings = TestDB.ALL_H2_V1, tester) {
            tester.insert {
                it[id] = 1
                it[word] = "Word A"
            }
            assertEquals(1, tester.selectAll().single()[tester.count])

            // H2_Mysql & H2_Mariadb syntax does not allow VALUES() syntax to come first in complex expression
            // Syntax must be column=(1 + VALUES(column)), not column=(VALUES(column) + 1)
            tester.upsert(
                onUpdate = { it[tester.count] = intLiteral(100) times insertValue(tester.count) }
            ) {
                it[id] = 1
                it[word] = "Word B"
                it[count] = 9
            }
            val result = tester.selectAll().single()
            assertEquals(900, result[tester.count])

            val newWords = listOf(
                Triple(2, "Word B", 2),
                Triple(1, "Word A", 3),
                Triple(3, "Word C", 4)
            )
            tester.batchUpsert(
                newWords,
                onUpdate = {
                    it[tester.word] = concat(tester.word, stringLiteral(" || "), insertValue(tester.count))
                    it[tester.count] = intLiteral(1) plus insertValue(tester.count)
                }
            ) { (id, word, count) ->
                this[tester.id] = id
                this[tester.word] = word
                this[tester.count] = count
            }

            assertEquals(3, tester.selectAll().count())
            val updatedWord = tester.selectAll().where { tester.word like "% || %" }.single()
            assertEquals("Word A || 3", updatedWord[tester.word])
            assertEquals(4, updatedWord[tester.count])
        }
    }

    @Test
    fun testUpsertWithUpdateExcludingColumns() {
        val tester = object : Table("tester") {
            val item = varchar("item", 64).uniqueIndex()
            val code = uuid("code").clientDefault { UUID.randomUUID() }
            val gains = integer("gains")
            val losses = integer("losses")
        }

        withTables(excludeSettings = TestDB.ALL_H2_V1, tester, configure = { useNestedTransactions = true }) {
            val itemA = "Item A"
            tester.upsert {
                it[item] = itemA
                it[gains] = 50
                it[losses] = 50
            }

            val (insertCode, insertGains, insertLosses) = tester.selectAll().single().let {
                Triple(it[tester.code], it[tester.gains], it[tester.losses])
            }

            transaction {
                // all fields get updated by default, including columns with default values
                tester.upsert {
                    it[item] = itemA
                    it[gains] = 200
                    it[losses] = 0
                }

                val (updateCode, updateGains, updateLosses) = tester.selectAll().single().let {
                    Triple(it[tester.code], it[tester.gains], it[tester.losses])
                }
                assertNotEquals(insertCode, updateCode)
                assertNotEquals(insertGains, updateGains)
                assertNotEquals(insertLosses, updateLosses)

                rollback()
            }

            tester.upsert(onUpdateExclude = listOf(tester.code, tester.gains)) {
                it[item] = itemA
                it[gains] = 200
                it[losses] = 0
            }

            val (updateCode, updateGains, updateLosses) = tester.selectAll().single().let {
                Triple(it[tester.code], it[tester.gains], it[tester.losses])
            }
            assertEquals(insertCode, updateCode)
            assertEquals(insertGains, updateGains)
            assertNotEquals(insertLosses, updateLosses)
        }
    }

    @Test
    fun testUpsertWithWhere() {
        val tester = object : IntIdTable("tester") {
            val name = varchar("name", 64).uniqueIndex()
            val address = varchar("address", 256)
            val age = integer("age")
        }

        withTables(excludeSettings = TestDB.ALL_MYSQL_LIKE + upsertViaMergeDB, tester) {
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
            val unchangedResult = tester.selectAll().where { tester.id eq unchanged[tester.id] }.single()
            assertEquals(unchanged[tester.address], unchangedResult[tester.address])
            val updatedResult = tester.selectAll().where { tester.id eq id1 }.single()
            assertEquals(updatedAge, updatedResult[tester.age])
        }
    }

    @Test
    fun testUpsertWithWhereParameterized() {
        val tester = object : IntIdTable("tester") {
            val name = varchar("name", 64).uniqueIndex()
            val age = integer("age")
        }

        withTables(excludeSettings = TestDB.ALL_MYSQL_LIKE + upsertViaMergeDB, tester) {
            val id1 = tester.upsert {
                it[name] = "Anya"
                it[age] = 10
            } get tester.id
            tester.upsert {
                it[name] = "Anna"
                it[age] = 50
            }

            val nameStartsWithA = tester.name like "A%"
            val nameEndsWithA = tester.name like stringLiteral("%a")
            val nameIsNotAnna = tester.name neq stringParam("Anna")
            val updatedAge = 20
            tester.upsert(tester.name, where = { nameStartsWithA and nameEndsWithA and nameIsNotAnna }) {
                it[name] = "Anya"
                it[age] = updatedAge
            }

            assertEquals(2, tester.selectAll().count())
            val updatedResult = tester.selectAll().where { tester.age eq updatedAge }.single()
            assertEquals(id1, updatedResult[tester.id])
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

        withTables(excludeSettings = TestDB.ALL_H2_V1, tester1, tester2) { testDb ->
            val id1 = tester1.insertAndGetId {
                it[name] = "foo"
            }
            val id2 = tester1.insertAndGetId {
                it[name] = "bar"
            }

            val query1 = tester1.select(tester1.name).where { tester1.id eq id1 }
            val id3 = tester2.upsert {
                if (testDb in upsertViaMergeDB) it[id] = 1
                it[name] = query1
            } get tester2.id
            assertEquals("foo", tester2.selectAll().single()[tester2.name])

            val query2 = tester1.select(tester1.name).where { tester1.id eq id2 }
            tester2.upsert {
                it[id] = id3
                it[name] = query2
            }
            assertEquals("bar", tester2.selectAll().single()[tester2.name])
        }
    }

    @Test
    fun testBatchUpsertWithNoConflict() {
        withTables(excludeSettings = TestDB.ALL_H2_V1, Words) {
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

    @Test
    fun testBatchUpsertWithConflict() {
        withTables(excludeSettings = TestDB.ALL_H2_V1, Words) {
            val vowels = listOf("A", "E", "I", "O", "U")
            val alphabet = ('A'..'Z').map { it.toString() }
            val lettersWithDuplicates = alphabet + vowels

            Words.batchUpsert(
                lettersWithDuplicates,
                onUpdate = { it[Words.count] = Words.count + 1 }
            ) { letter ->
                this[Words.word] = letter
            }

            assertEquals(alphabet.size.toLong(), Words.selectAll().count())
            Words.selectAll().forEach {
                val expectedCount = if (it[Words.word] in vowels) 2 else 1
                assertEquals(expectedCount, it[Words.count])
            }
        }
    }

    @Test
    fun testBatchUpsertWithSequence() {
        withTables(excludeSettings = TestDB.ALL_H2_V1, Words) {
            val amountOfWords = 25
            val allWords = List(amountOfWords) { UUID.randomUUID().toString() }.asSequence()
            Words.batchUpsert(allWords) { word -> this[Words.word] = word }

            val batchesSize = Words.selectAll().count()

            assertEquals(amountOfWords.toLong(), batchesSize)
        }
    }

    @Test
    fun testBatchUpsertWithEmptySequence() {
        withTables(excludeSettings = TestDB.ALL_H2_V1, Words) {
            val allWords = emptySequence<String>()
            Words.batchUpsert(allWords) { word -> this[Words.word] = word }

            val batchesSize = Words.selectAll().count()

            assertEquals(0, batchesSize)
        }
    }

    @Test
    fun testBatchUpsertWithWhere() {
        withTables(excludeSettings = TestDB.ALL_MYSQL_LIKE + upsertViaMergeDB, Words) {
            val vowels = listOf("A", "E", "I", "O", "U")
            val alphabet = ('A'..'Z').map { it.toString() }
            val lettersWithDuplicates = alphabet + vowels

            val firstThreeVowels = vowels.take(3)
            Words.batchUpsert(
                lettersWithDuplicates,
                onUpdate = { it[Words.count] = Words.count + 1 },
                // PostgresNG throws IndexOutOfBound if shouldReturnGeneratedValues == true
                // Related issue in pgjdbc-ng repository: https://github.com/impossibl/pgjdbc-ng/issues/545
                shouldReturnGeneratedValues = false,
                where = { Words.word inList firstThreeVowels }
            ) { letter ->
                this[Words.word] = letter
            }

            assertEquals(alphabet.size.toLong(), Words.selectAll().count())
            Words.selectAll().forEach {
                val expectedCount = if (it[Words.word] in firstThreeVowels) 2 else 1
                assertEquals(expectedCount, it[Words.count])
            }
        }
    }

    @Test
    fun testInsertedCountWithBatchUpsert() {
        withTables(excludeSettings = TestDB.ALL_H2_V1, AutoIncTable) { testDb ->
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
            val isH2MysqlMode = testDb == TestDB.H2_V2_MYSQL || testDb == TestDB.H2_V2_MARIADB
            var expected = if (isH2MysqlMode) 0 else newDataSize
            AutoIncTable.batchUpsert(data, shouldReturnGeneratedValues = isNotSqlServer) { (id, name) ->
                statement = this
                this[AutoIncTable.id] = id
                this[AutoIncTable.name] = name
            }
            assertEquals(expected, statement.insertedCount)

            // all existing rows updated & 1 new row inserted
            val updatedData = data.map { it.first to "new${it.second}" } + (4 to "D")
            expected = if (testDb in TestDB.ALL_MYSQL_LIKE) newDataSize * 2 + 1 else newDataSize + 1
            AutoIncTable.batchUpsert(updatedData, shouldReturnGeneratedValues = isNotSqlServer) { (id, name) ->
                statement = this
                this[AutoIncTable.id] = id
                this[AutoIncTable.name] = name
            }
            assertEquals(expected, statement.insertedCount)

            assertEquals(updatedData.size.toLong(), AutoIncTable.selectAll().count())
        }
    }

    @Test
    fun testUpsertWithUUIDPrimaryKey() {
        val tester = object : UUIDTable("upsert_test", "id") {
            val key = integer("test_key").uniqueIndex()
            val value = text("test_value")
        }

        // At present, only Postgres returns the correct UUID directly from the result set.
        // For other databases incorrect ID is returned from the 'upsert' command.
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES.toSet(), tester) {
            val insertId = tester.insertAndGetId {
                it[key] = 1
                it[value] = "one"
            }

            val upsertId = tester.upsert(
                keys = arrayOf(tester.key),
                onUpdateExclude = listOf(tester.id),
            ) {
                it[key] = 1
                it[value] = "two"
            }.resultedValues!!.first()[tester.id]

            assertEquals(insertId, upsertId)
            assertEquals("two", tester.selectAll().where { tester.id eq insertId }.first()[tester.value])
        }
    }

    @Test
    fun testBatchUpsertWithUUIDPrimaryKey() {
        val tester = object : UUIDTable("batch_upsert_test", "id") {
            val key = integer("test_key").uniqueIndex()
            val value = text("test_value")
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES.toSet(), tester) {
            val insertId = tester.insertAndGetId {
                it[key] = 1
                it[value] = "one"
            }

            val upsertId = tester.batchUpsert(
                data = listOf(1 to "two"),
                keys = arrayOf(tester.key),
                onUpdateExclude = listOf(tester.id),
            ) {
                this[tester.key] = it.first
                this[tester.value] = it.second
            }.first()[tester.id]

            assertEquals(insertId, upsertId)
            assertEquals("two", tester.selectAll().where { tester.id eq insertId }.first()[tester.value])
        }
    }

    @Test
    fun testUpsertWhenColumnNameIncludesTableName() {
        val tester = object : Table("my_table") {
            val myTableId = integer("my_table_id")
            val myTableValue = varchar("my_table_value", 100)
            override val primaryKey = PrimaryKey(myTableId)
        }

        withTables(excludeSettings = TestDB.ALL_H2_V1, tester) {
            tester.upsert {
                it[myTableId] = 1
                it[myTableValue] = "Hello"
            }

            assertEquals("Hello", tester.selectAll().single()[tester.myTableValue])
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

    @Test
    fun testDefaultValuesAndNullableColumnsNotInArguments() {
        val tester = object : UUIDTable("test_batch_insert_defaults") {
            val number = integer("number")
            val default = varchar("default", 128).default("default")
            val defaultExpression = varchar("defaultExpression", 128).defaultExpression(stringLiteral("defaultExpression"))
            val nullable = varchar("nullable", 128).nullable()
            val nullableDefaultNull = varchar("nullableDefaultNull", 128).nullable().default(null)
            val nullableDefaultNotNull = varchar("nullableDefaultNotNull", 128).nullable().default("nullableDefaultNotNull")
            val databaseGenerated = integer("databaseGenerated").withDefinition("DEFAULT 1").databaseGenerated()
        }

        val testerWithFakeDefaults = object : UUIDTable("test_batch_insert_defaults") {
            val number = integer("number")
            val default = varchar("default", 128).default("default-fake")
            val defaultExpression = varchar("defaultExpression", 128).defaultExpression(stringLiteral("defaultExpression-fake"))
            val nullable = varchar("nullable", 128).nullable().default("null-fake")
            val nullableDefaultNull = varchar("nullableDefaultNull", 128).nullable().default("null-fake")
            val nullableDefaultNotNull = varchar("nullableDefaultNotNull", 128).nullable().default("nullableDefaultNotNull-fake")
            val databaseGenerated = integer("databaseGenerated").default(-1)
        }

        withTables(excludeSettings = listOf(TestDB.H2_V1), tester) {
            testerWithFakeDefaults.batchUpsert(listOf(1, 2, 3)) {
                this[testerWithFakeDefaults.number] = 10
            }

            testerWithFakeDefaults.selectAll().forEach {
                assertEquals("default", it[testerWithFakeDefaults.default])
                assertEquals("defaultExpression", it[testerWithFakeDefaults.defaultExpression])
                assertEquals(null, it[testerWithFakeDefaults.nullable])
                assertEquals(null, it[testerWithFakeDefaults.nullableDefaultNull])
                assertEquals("nullableDefaultNotNull", it[testerWithFakeDefaults.nullableDefaultNotNull])
                assertEquals(1, it[testerWithFakeDefaults.databaseGenerated])
            }
        }
    }
}
