package org.jetbrains.exposed.v1.tests.shared.functions
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.coalesce
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.concat
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.core.vendors.h2Mode
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.currentDialectTest
import org.jetbrains.exposed.v1.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.v1.tests.shared.dml.withCitiesAndUsers
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FunctionsTests : DatabaseTestsBase() {

    @Test
    fun testCalc01() {
        withCitiesAndUsers { cities, _, _ ->
            val r = cities.select(cities.id.sum()).toList()
            assertEquals(1, r.size)
            assertEquals(6, r[0][cities.id.sum()])
        }
    }

    @Test
    fun testCalc02() {
        withCitiesAndUsers { cities, users, userData ->
            val sum = Expression.build {
                Sum(cities.id + userData.value, IntegerColumnType())
            }
            val r = (users innerJoin userData innerJoin cities).select(users.id, sum)
                .groupBy(users.id).orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals(22, r[0][sum])
            assertEquals("sergey", r[1][users.id])
            assertEquals(32, r[1][sum])
        }
    }

    @Test
    fun testCalc03() {
        withCitiesAndUsers(exclude = listOf(TestDB.H2_V2_ORACLE)) { cities, users, userData ->
            val sum = Expression.build { Sum(cities.id * 100 + userData.value / 10, IntegerColumnType()) }
            val mod1 = Expression.build { sum % 100 }
            val mod2 = Expression.build { sum mod 100 }
            val r = (users innerJoin userData innerJoin cities).select(users.id, sum, mod1, mod1)
                .groupBy(users.id).orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals(202, r[0][sum])
            assertEquals(2, r[0][mod1])
            assertEquals(2, r[0][mod2])
            assertEquals("sergey", r[1][users.id])
            assertEquals(203, r[1][sum])
            assertEquals(3, r[1][mod1])
            assertEquals(3, r[1][mod2])
        }
    }

    @Test
    fun `rem on numeric PK should work`() {
        // Create a new table here, since the other tables don't define PK
        val table = object : IntIdTable("test_mod_on_pk") {
            val otherColumn = short("other")
        }
        withTables(table) {
            repeat(5) {
                table.insert {
                    it[otherColumn] = 4
                }
            }

            val modOnPK1 = Expression.build { table.id % 3 }.alias("shard1")
            val modOnPK2 = Expression.build { table.id % intLiteral(3) }.alias("shard2")
            val modOnPK3 = Expression.build { table.id % table.otherColumn }.alias("shard3")
            val modOnPK4 = Expression.build { table.otherColumn % table.id }.alias("shard4")

            val r = table.select(table.id, modOnPK1, modOnPK2, modOnPK3, modOnPK4).last()

            assertEquals(2, r[modOnPK1])
            assertEquals(2, r[modOnPK2])
            assertEquals(1, r[modOnPK3])
            assertEquals(4, r[modOnPK4])
        }
    }

    @Test
    fun `mod on numeric PK should work`() {
        // Create a new table here, since the other tables don't define PK
        val table = object : IntIdTable("test_mod_on_pk") {
            val otherColumn = short("other")
        }
        withTables(table) {
            repeat(5) {
                table.insert {
                    it[otherColumn] = 4
                }
            }

            val modOnPK1 = Expression.build { table.id mod 3 }.alias("shard1")
            val modOnPK2 = Expression.build { table.id mod intLiteral(3) }.alias("shard2")
            val modOnPK3 = Expression.build { table.id mod table.otherColumn }.alias("shard3")
            val modOnPK4 = Expression.build { table.otherColumn mod table.id }.alias("shard4")

            val r = table.select(table.id, modOnPK1, modOnPK2, modOnPK3, modOnPK4).last()

            assertEquals(2, r[modOnPK1])
            assertEquals(2, r[modOnPK2])
            assertEquals(1, r[modOnPK3])
            assertEquals(4, r[modOnPK4])
        }
    }

    @Test
    fun testBitwiseAnd1() {
        withCitiesAndUsers { _, users, _ ->
            // SQLServer and Oracle don't support = on bit values
            val doesntSupportBitwiseEQ =
                currentDialectTest is SQLServerDialect || currentDialectTest is OracleDialect || currentDialectTest.h2Mode == H2Dialect.H2CompatibilityMode.Oracle
            val adminFlag = DMLTestsData.Users.Flags.IS_ADMIN
            val adminAndFlagsExpr = Expression.build { (users.flags bitwiseAnd adminFlag) }
            val adminEq = Expression.build { adminAndFlagsExpr eq adminFlag }
            val toSlice = listOfNotNull(adminAndFlagsExpr, adminEq.takeIf { !doesntSupportBitwiseEQ })
            val r = users.select(toSlice).orderBy(users.id).toList()
            assertEquals(5, r.size)
            assertEquals(0, r[0][adminAndFlagsExpr])
            assertEquals(1, r[1][adminAndFlagsExpr])
            assertEquals(0, r[2][adminAndFlagsExpr])
            assertEquals(1, r[3][adminAndFlagsExpr])
            assertEquals(0, r[4][adminAndFlagsExpr])
            if (!doesntSupportBitwiseEQ) {
                assertEquals(false, r[0][adminEq])
                assertEquals(true, r[1][adminEq])
                assertEquals(false, r[2][adminEq])
                assertEquals(true, r[3][adminEq])
                assertEquals(false, r[4][adminEq])
            }
        }
    }

    @Test
    fun testBitwiseAnd2() {
        withCitiesAndUsers { _, users, _ ->
            // SQLServer and Oracle don't support = on bit values
            val doesntSupportBitwiseEQ = currentDialectTest is SQLServerDialect || currentDialectTest is OracleDialect
            val adminFlag = DMLTestsData.Users.Flags.IS_ADMIN
            val adminAndFlagsExpr = Expression.build { (users.flags bitwiseAnd intLiteral(adminFlag)) }
            val adminEq = Expression.build { adminAndFlagsExpr eq adminFlag }
            val toSlice = listOfNotNull(adminAndFlagsExpr, adminEq.takeIf { !doesntSupportBitwiseEQ })
            val r = users.select(toSlice).orderBy(users.id).toList()
            assertEquals(5, r.size)
            assertEquals(0, r[0][adminAndFlagsExpr])
            assertEquals(1, r[1][adminAndFlagsExpr])
            assertEquals(0, r[2][adminAndFlagsExpr])
            assertEquals(1, r[3][adminAndFlagsExpr])
            assertEquals(0, r[4][adminAndFlagsExpr])
            if (!doesntSupportBitwiseEQ) {
                assertEquals(false, r[0][adminEq])
                assertEquals(true, r[1][adminEq])
                assertEquals(false, r[2][adminEq])
                assertEquals(true, r[3][adminEq])
                assertEquals(false, r[4][adminEq])
            }
        }
    }

    @Test
    fun testBitwiseOr1() {
        withCitiesAndUsers { _, users, _ ->
            val extra = 0b10
            val flagsWithExtra = Expression.build { users.flags bitwiseOr extra }
            val r = users.select(flagsWithExtra).orderBy(users.id).toList()
            assertEquals(5, r.size)
            assertEquals(0b0010, r[0][flagsWithExtra])
            assertEquals(0b0011, r[1][flagsWithExtra])
            assertEquals(0b1010, r[2][flagsWithExtra])
            assertEquals(0b1011, r[3][flagsWithExtra])
            assertEquals(0b1010, r[4][flagsWithExtra])
        }
    }

    @Test
    fun testBitwiseOr2() {
        withCitiesAndUsers { _, users, _ ->
            val extra = 0b10
            val flagsWithExtra = Expression.build { users.flags bitwiseOr intLiteral(extra) }
            val r = users.select(users.id, flagsWithExtra).orderBy(users.id).toList()
            assertEquals(5, r.size)
            assertEquals(0b0010, r[0][flagsWithExtra])
            assertEquals(0b0011, r[1][flagsWithExtra])
            assertEquals(0b1010, r[2][flagsWithExtra])
            assertEquals(0b1011, r[3][flagsWithExtra])
            assertEquals(0b1010, r[4][flagsWithExtra])
        }
    }

    @Test
    fun testBitwiseXor01() {
        withCitiesAndUsers { _, users, _ ->
            val flagsWithXor = Expression.build { users.flags bitwiseXor 0b111 }
            val r = users.select(users.id, flagsWithXor).orderBy(users.id).toList()
            assertEquals(5, r.size)
            assertEquals(0b0111, r[0][flagsWithXor])
            assertEquals(0b0110, r[1][flagsWithXor])
            assertEquals(0b1111, r[2][flagsWithXor])
            assertEquals(0b1110, r[3][flagsWithXor])
            assertEquals(0b1111, r[4][flagsWithXor])
        }
    }

    @Test
    fun testBitwiseXor02() {
        withCitiesAndUsers { _, users, _ ->
            val flagsWithXor = Expression.build { users.flags bitwiseXor intLiteral(0b111) }
            val r = users.select(users.id, flagsWithXor).orderBy(users.id).toList()
            assertEquals(5, r.size)
            assertEquals(0b0111, r[0][flagsWithXor])
            assertEquals(0b0110, r[1][flagsWithXor])
            assertEquals(0b1111, r[2][flagsWithXor])
            assertEquals(0b1110, r[3][flagsWithXor])
            assertEquals(0b1111, r[4][flagsWithXor])
        }
    }

    @Test
    fun testFlag01() {
        withCitiesAndUsers { _, users, _ ->
            val adminFlag = DMLTestsData.Users.Flags.IS_ADMIN
            val r = users.select(users.id).where { users.flags hasFlag adminFlag }.orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("andrey", r[0][users.id])
            assertEquals("sergey", r[1][users.id])
        }
    }

    @Test
    fun testSubstring01() {
        withCitiesAndUsers { _, users, _ ->
            val substring = users.name.substring(1, 2)
            val r = (users).select(users.id, substring)
                .orderBy(users.id).toList()
            assertEquals(5, r.size)
            assertEquals("Al", r[0][substring])
            assertEquals("An", r[1][substring])
            assertEquals("Eu", r[2][substring])
            assertEquals("Se", r[3][substring])
            assertEquals("So", r[4][substring])
        }
    }

    @Test
    fun testCharLengthWithSum() {
        withCitiesAndUsers { cities, _, _ ->
            val sumOfLength = CharLength(cities.name).sum()
            val expectedValue = cities.selectAll().sumOf { it[cities.name].length }

            val results = cities.select(sumOfLength).toList()
            assertEquals(1, results.size)
            assertEquals(expectedValue, results.single()[sumOfLength])
        }
    }

    @Test
    fun testCharLengthWithEdgeCaseStrings() {
        val testTable = object : Table("test_table") {
            val nullString = varchar("null_string", 32).nullable()
            val emptyString = varchar("empty_string", 32).nullable()
        }

        withTables(testTable) {
            testTable.insert {
                it[nullString] = null
                it[emptyString] = ""
            }
            val helloWorld = "こんにちは世界" // each character is a 3-byte character

            val nullLength = testTable.nullString.charLength()
            val emptyLength = testTable.emptyString.charLength()
            val multiByteLength = CharLength(stringLiteral(helloWorld))

            // Oracle treats empty strings as null
            val isOracleDialect = currentDialectTest is OracleDialect ||
                currentDialectTest.h2Mode == H2Dialect.H2CompatibilityMode.Oracle
            val expectedEmpty = if (isOracleDialect) null else 0
            // char_length should return single-character count, not total byte count
            val expectedMultibyte = helloWorld.length

            val result = testTable.select(nullLength, emptyLength, multiByteLength).single()
            assertNull(result[nullLength])
            assertEquals(expectedEmpty, result[emptyLength])
            assertEquals(expectedMultibyte, result[multiByteLength])
        }
    }

    @Test
    fun testSelectCase01() {
        withCitiesAndUsers { _, users, _ ->
            val field = Expression.build { this.case().When(users.id eq "alex", stringLiteral("11")).Else(stringLiteral("22")) }
            val r = users.select(users.id, field).orderBy(users.id).limit(2).toList()
            assertEquals(2, r.size)
            assertEquals("11", r[0][field])
            assertEquals("alex", r[0][users.id])
            assertEquals("22", r[1][field])
            assertEquals("andrey", r[1][users.id])
        }
    }

    @Test
    fun testStringFunctions() {
        withCitiesAndUsers { cities, _, _ ->

            val lcase = DMLTestsData.Cities.name.lowerCase()
            assert(cities.select(lcase).any { it[lcase] == "prague" })

            val ucase = DMLTestsData.Cities.name.upperCase()
            assert(cities.select(ucase).any { it[ucase] == "PRAGUE" })
        }
    }

    @Test
    fun testLocate() {
        withCitiesAndUsers { cities, _, _ ->
            val locate = cities.name.locate("e")
            val results = cities.select(locate).toList()

            assertEquals(6, results[0][locate]) // St. Petersburg
            assertEquals(0, results[1][locate]) // Munich
            assertEquals(6, results[2][locate]) // Prague
        }
    }

    @Test
    fun testLocate02() {
        withCitiesAndUsers { cities, _, _ ->
            val locate = cities.name.locate("Peter")
            val results = cities.select(locate).toList()

            assertEquals(5, results[0][locate]) // St. Petersburg
            assertEquals(0, results[1][locate]) // Munich
            assertEquals(0, results[2][locate]) // Prague
        }
    }

    @Test
    fun testLocate03() {
        withCitiesAndUsers { cities, _, _ ->
            val isNotCaseSensitiveDialect = currentDialectTest is MysqlDialect || currentDialectTest is SQLServerDialect

            val locate = cities.name.locate("p")
            val results = cities.select(locate).toList()

            assertEquals(if (isNotCaseSensitiveDialect) 5 else 0, results[0][locate]) // St. Petersburg
            assertEquals(0, results[1][locate]) // Munich
            assertEquals(if (isNotCaseSensitiveDialect) 1 else 0, results[2][locate]) // Prague
        }
    }

    @Test
    fun testRandomFunction01() {
        val t = DMLTestsData.Cities
        withTables(t) {
            if (t.selectAll().count() == 0L) {
                t.insert { it[t.name] = "city-1" }
            }

            val rand = Random()
            val resultRow = t.select(rand).limit(1).single()
            assertNotNull(resultRow[rand])
        }
    }

    @Test
    fun testRegexp01() {
        withCitiesAndUsers(TestDB.ALL_SQLSERVER_LIKE + TestDB.SQLITE) { _, users, _ ->
            assertEquals(2L, users.selectAll().where { users.id regexp "a.+" }.count())
            assertEquals(1L, users.selectAll().where { users.id regexp "an.+" }.count())
            assertEquals(users.selectAll().count(), users.selectAll().where { users.id regexp ".*" }.count())
            assertEquals(2L, users.selectAll().where { users.id regexp ".+y" }.count())
        }
    }

    @Test
    fun testRegexp02() {
        withCitiesAndUsers(TestDB.ALL_SQLSERVER_LIKE + TestDB.SQLITE) { _, users, _ ->
            assertEquals(2L, users.selectAll().where { users.id.regexp(stringLiteral("a.+")) }.count())
            assertEquals(1L, users.selectAll().where { users.id.regexp(stringLiteral("an.+")) }.count())
            assertEquals(users.selectAll().count(), users.selectAll().where { users.id.regexp(stringLiteral(".*")) }.count())
            assertEquals(2L, users.selectAll().where { users.id.regexp(stringLiteral(".+y")) }.count())
        }
    }

    @Test
    fun testConcat01() {
        withCitiesAndUsers { cities, _, _ ->
            val concatField = concat(stringLiteral("Foo"), stringLiteral("Bar"))
            val result = cities.select(concatField).limit(1).single()
            assertEquals("FooBar", result[concatField])

            val concatField2 = concat("!", listOf(stringLiteral("Foo"), stringLiteral("Bar")))
            val result2 = cities.select(concatField2).limit(1).single()
            assertEquals("Foo!Bar", result2[concatField2])
        }
    }

    @Test
    fun testConcat02() {
        withCitiesAndUsers { _, users, _ ->
            val concatField = concat(users.id, stringLiteral(" - "), users.name)
            val result = users.select(concatField).where { users.id eq "andrey" }.single()
            assertEquals("andrey - Andrey", result[concatField])

            val concatField2 = concat("!", listOf(users.id, users.name))
            val result2 = users.select(concatField2).where { users.id eq "andrey" }.single()
            assertEquals("andrey!Andrey", result2[concatField2])
        }
    }

    @Test
    fun testConcatWithNumbers() {
        withCitiesAndUsers { _, _, data ->
            val concatField = concat(data.user_id, stringLiteral(" - "), data.comment, stringLiteral(" - "), data.value)
            val result = data.select(concatField).where { data.user_id eq "sergey" }.single()
            assertEquals("sergey - Comment for Sergey - 30", result[concatField])

            val concatField2 = concat("!", listOf(data.user_id, data.comment, data.value))
            val result2 = data.select(concatField2).where { data.user_id eq "sergey" }.single()
            assertEquals("sergey!Comment for Sergey!30", result2[concatField2])
        }
    }

    @Test
    fun testCustomStringFunctions01() {
        withCitiesAndUsers { cities, _, _ ->
            val customLower = DMLTestsData.Cities.name.function("lower")
            assert(cities.select(customLower).any { it[customLower] == "prague" })

            val customUpper = DMLTestsData.Cities.name.function("UPPER")
            assert(cities.select(customUpper).any { it[customUpper] == "PRAGUE" })
        }
    }

    @Test
    fun testCustomStringFunctions02() {
        withCitiesAndUsers { cities, _, _ ->
            val replace = CustomStringFunction("REPLACE", cities.name, stringParam("gue"), stringParam("foo"))
            val result = cities.select(replace).where { cities.name eq "Prague" }.singleOrNull()
            assertEquals("Prafoo", result?.get(replace))
        }
    }

    @Test
    fun testCustomIntegerFunctions01() {
        withCitiesAndUsers { cities, _, _ ->
            val ids = cities.selectAll().map { it[DMLTestsData.Cities.id] }.toList()
            assertEqualCollections(listOf(1, 2, 3), ids)

            val sqrt = DMLTestsData.Cities.id.function("SQRT")
            val sqrtIds = cities.select(sqrt).map { it[sqrt] }.toList()
            assertEqualCollections(listOf(1, 1, 1), sqrtIds)
        }
    }

    @Test
    fun testCustomIntegerFunctions02() {
        withCitiesAndUsers { cities, _, _ ->
            val power = CustomLongFunction("POWER", cities.id, intParam(2))
            val ids = cities.select(power).map { it[power] }
            assertEqualCollections(listOf(1L, 4L, 9L), ids)
        }
    }

    @Test
    fun testAndOperatorDoesntMutate() {
        withDb {
            val initialOp = Op.build { DMLTestsData.Cities.name eq "foo" }

            val secondOp = Op.build { DMLTestsData.Cities.name.isNotNull() }
            assertEquals("($initialOp) AND ($secondOp)", (initialOp and secondOp).toString())

            val thirdOp = exists(DMLTestsData.Cities.selectAll())
            assertEquals("($initialOp) AND $thirdOp", (initialOp and thirdOp).toString())

            assertEquals(
                "($initialOp) AND ($secondOp) AND $thirdOp",
                (initialOp and secondOp and thirdOp).toString()
            )
        }
    }

    @Test
    fun testOrOperatorDoesntMutate() {
        withDb {
            val initialOp = Op.build { DMLTestsData.Cities.name eq "foo" }

            val secondOp = Op.build { DMLTestsData.Cities.name.isNotNull() }
            assertEquals("($initialOp) OR ($secondOp)", (initialOp or secondOp).toString())

            val thirdOp = exists(DMLTestsData.Cities.selectAll())
            assertEquals("($initialOp) OR $thirdOp", (initialOp or thirdOp).toString())

            assertEquals(
                "($initialOp) OR ($secondOp) OR $thirdOp",
                (initialOp or secondOp or thirdOp).toString()
            )
        }
    }

    @Test
    fun testAndOrCombinations() {
        withDb {
            val initialOp = Op.build { DMLTestsData.Cities.name eq "foo" }
            val secondOp = exists(DMLTestsData.Cities.selectAll())
            assertEquals("(($initialOp) OR ($initialOp)) AND ($initialOp)", (initialOp or initialOp and initialOp).toString())
            assertEquals("(($initialOp) OR ($initialOp)) AND $secondOp", (initialOp or initialOp and secondOp).toString())
            assertEquals("(($initialOp) AND ($initialOp)) OR ($initialOp)", (initialOp and initialOp or initialOp).toString())
            assertEquals("(($initialOp) AND $secondOp) OR ($initialOp)", (initialOp and secondOp or initialOp).toString())
            assertEquals("($initialOp) AND (($initialOp) OR ($initialOp))", (initialOp and (initialOp or initialOp)).toString())
            assertEquals(
                "(($initialOp) OR ($initialOp)) AND (($initialOp) OR ($initialOp))",
                ((initialOp or initialOp) and (initialOp or initialOp)).toString()
            )
            assertEquals(
                "((($initialOp) OR ($initialOp)) AND ($initialOp)) OR ($initialOp)",
                (initialOp or initialOp and initialOp or initialOp).toString()
            )
            assertEquals(
                "($initialOp) OR ($initialOp) OR ($initialOp) OR ($initialOp)",
                (initialOp or initialOp or initialOp or initialOp).toString()
            )
            assertEquals("$secondOp OR $secondOp OR $secondOp OR $secondOp", (secondOp or secondOp or secondOp or secondOp).toString())
            assertEquals(
                "($initialOp) OR ($initialOp) OR ($initialOp) OR ($initialOp)",
                (initialOp or (initialOp or initialOp) or initialOp).toString()
            )
            assertEquals("($initialOp) OR ($secondOp AND $secondOp) OR ($initialOp)", (initialOp or (secondOp and secondOp) or initialOp).toString())
            assertEquals("$initialOp", (initialOp orIfNotNull (null as Expression<Boolean>?)).toString())
            assertEquals("$initialOp", (initialOp andIfNotNull (null as Op<Boolean>?)).toString())
            assertEquals("($initialOp) AND ($initialOp)", (initialOp andIfNotNull (initialOp andIfNotNull (null as Op<Boolean>?))).toString())
            assertEquals("($initialOp) AND ($initialOp)", (initialOp andIfNotNull (null as Op<Boolean>?) andIfNotNull initialOp).toString())
            assertEquals("($initialOp) AND $secondOp", (initialOp andIfNotNull (secondOp andIfNotNull (null as Op<Boolean>?))).toString())
            assertEquals(
                "(($initialOp) AND $secondOp) OR $secondOp",
                (initialOp andIfNotNull (secondOp andIfNotNull (null as Expression<Boolean>?)) orIfNotNull secondOp).toString()
            )
            assertEquals("($initialOp) AND ($initialOp)", (initialOp.andIfNotNull { initialOp }).toString())
        }
    }

    @Test
    fun testCustomOperator() {
        // implement a + operator using CustomOperator
        infix fun Expression<*>.plus(operand: Int) =
            CustomOperator("+", IntegerColumnType(), this, intParam(operand))

        withCitiesAndUsers { _, _, userData ->
            userData
                .selectAll()
                .where { (userData.value plus 15).eq(35) }
                .forEach {
                    assertEquals(it[userData.value], 20)
                }
        }
    }

    @Test
    fun testCoalesceFunction() {
        withCitiesAndUsers { _, users, _ ->
            val coalesceExp1 = coalesce(users.cityId, intLiteral(1000))

            users.select(users.cityId, coalesceExp1).forEach {
                val cityId = it[users.cityId]
                val actual: Int = it[coalesceExp1]
                if (cityId != null) {
                    assertEquals(cityId, actual)
                } else {
                    assertEquals(1000, actual)
                }
            }

            val coalesceExp2 = Coalesce(users.cityId, Op.nullOp(), intLiteral(1000))

            users.select(users.cityId, coalesceExp2).forEach {
                val cityId = it[users.cityId]
                val actual: Int = it[coalesceExp2]
                if (cityId != null) {
                    assertEquals(cityId, actual)
                } else {
                    assertEquals(1000, actual)
                }
            }
        }
    }

    @Test
    fun testConcatUsingPlusOperator() {
        withCitiesAndUsers { _, users, _ ->
            val concatField = SqlExpressionBuilder.run { users.id + " - " + users.name }
            val result = users.select(concatField).where { users.id eq "andrey" }.single()
            assertEquals("andrey - Andrey", result[concatField])

            val concatField2 = SqlExpressionBuilder.run { users.id + users.name }
            val result2 = users.select(concatField2).where { users.id eq "andrey" }.single()
            assertEquals("andreyAndrey", result2[concatField2])

            val concatField3 = SqlExpressionBuilder.run { "Hi " plus users.name + "!" }
            val result3 = users.select(concatField3).where { users.id eq "andrey" }.single()
            assertEquals("Hi Andrey!", result3[concatField3])
        }
    }
}
