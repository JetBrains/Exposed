package org.jetbrains.exposed.r2dbc.sql.tests.shared.dml

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.sql.Query
import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import java.math.BigDecimal

object DMLTestsData {
    object Cities : Table() {
        val id: Column<Int> = integer("cityId").autoIncrement()
        val name: Column<String> = varchar("name", 50)
        override val primaryKey = PrimaryKey(id)
    }

    object Users : Table() {
        val id: Column<String> = varchar("id", 10)
        val name: Column<String> = varchar("name", length = 50)
        val cityId: Column<Int?> = reference("city_id", Cities.id).nullable()
        val flags: Column<Int> = integer("flags").default(0)
        override val primaryKey = PrimaryKey(id)

        object Flags {
            const val IS_ADMIN = 0b1
            const val HAS_DATA = 0b1000
        }
    }

    object UserData : Table() {
        val user_id: Column<String> = reference("user_id", Users.id)
        val comment: Column<String> = varchar("comment", 30)
        val value: Column<Int> = integer("value")
    }

    object Sales : Table() {
        val year: Column<Int> = integer("year")
        val month: Column<Int> = integer("month")
        val product: Column<String?> = varchar("product", 30).nullable()
        val amount: Column<BigDecimal> = decimal("amount", 8, 2)
    }

    object SomeAmounts : Table() {
        val amount: Column<BigDecimal> = decimal("amount", 8, 2)
    }
}

@Suppress("LongMethod")
fun R2dbcDatabaseTestsBase.withCitiesAndUsers(
    exclude: Collection<TestDB> = emptyList(),
    statement: suspend R2dbcTransaction.(
        cities: DMLTestsData.Cities,
        users: DMLTestsData.Users,
        userData: DMLTestsData.UserData
    ) -> Unit
) {
    val users = DMLTestsData.Users
    val userFlags = DMLTestsData.Users.Flags
    val cities = DMLTestsData.Cities
    val userData = DMLTestsData.UserData

    withTables(exclude, cities, users, userData) {
        val saintPetersburgId = cities.insert {
            it[name] = "St. Petersburg"
        } get cities.id

        val munichId = cities.insert {
            it[name] = "Munich"
        } get cities.id

        cities.insert {
            it[name] = "Prague"
        }

        users.insert {
            it[id] = "andrey"
            it[name] = "Andrey"
            it[cityId] = saintPetersburgId
            it[flags] = userFlags.IS_ADMIN
        }

        users.insert {
            it[id] = "sergey"
            it[name] = "Sergey"
            it[cityId] = munichId
            it[flags] = userFlags.IS_ADMIN or userFlags.HAS_DATA
        }

        users.insert {
            it[id] = "eugene"
            it[name] = "Eugene"
            it[cityId] = munichId
            it[flags] = userFlags.HAS_DATA
        }

        users.insert {
            it[id] = "alex"
            it[name] = "Alex"
            it[cityId] = null
        }

        users.insert {
            it[id] = "smth"
            it[name] = "Something"
            it[cityId] = null
            it[flags] = userFlags.HAS_DATA
        }

        userData.insert {
            it[user_id] = "smth"
            it[comment] = "Something is here"
            it[value] = 10
        }

        userData.insert {
            it[user_id] = "smth"
            it[comment] = "Comment #2"
            it[value] = 20
        }

        userData.insert {
            it[user_id] = "eugene"
            it[comment] = "Comment for Eugene"
            it[value] = 20
        }

        userData.insert {
            it[user_id] = "sergey"
            it[comment] = "Comment for Sergey"
            it[value] = 30
        }

        statement(cities, users, userData)
    }
}

fun R2dbcDatabaseTestsBase.withSales(
    excludeSettings: Collection<TestDB> = emptyList(),
    statement: suspend R2dbcTransaction.(testDb: TestDB, sales: DMLTestsData.Sales) -> Unit
) {
    val sales = DMLTestsData.Sales

    withTables(excludeSettings, sales) {
        insertSale(2018, 11, "tea", "550.10")
        insertSale(2018, 12, "coffee", "1500.25")
        insertSale(2018, 12, "tea", "900.30")
        insertSale(2019, 1, "coffee", "1620.10")
        insertSale(2019, 1, "tea", "650.70")
        insertSale(2019, 2, "coffee", "1870.90")
        insertSale(2019, 2, null, "10.20")

        statement(it, sales)
    }
}

private suspend fun insertSale(year: Int, month: Int, product: String?, amount: String) {
    val sales = DMLTestsData.Sales
    sales.insert {
        it[sales.year] = year
        it[sales.month] = month
        it[sales.product] = product
        it[sales.amount] = BigDecimal(amount)
    }
}

suspend fun R2dbcDatabaseTestsBase.withSomeAmounts(
    statement: suspend R2dbcTransaction.(testDb: TestDB, someAmounts: DMLTestsData.SomeAmounts) -> Unit
) {
    val someAmounts = DMLTestsData.SomeAmounts

    withTables(someAmounts) {
        suspend fun insertAmount(amount: BigDecimal) =
            someAmounts.insert { it[someAmounts.amount] = amount }
        insertAmount(BigDecimal("650.70"))
        insertAmount(BigDecimal("1500.25"))
        insertAmount(BigDecimal(1000))

        statement(it, someAmounts)
    }
}

suspend fun R2dbcDatabaseTestsBase.withSalesAndSomeAmounts(
    statement: suspend R2dbcTransaction.(
        testDb: TestDB,
        sales: DMLTestsData.Sales,
        someAmounts: DMLTestsData.SomeAmounts
    ) -> Unit
) =
    withSales { testDb, sales ->
        withSomeAmounts { _, someAmounts ->
            statement(testDb, sales, someAmounts)
        }
    }

internal suspend fun Query.toCityNameList(): List<String> {
    return this.map { it[DMLTestsData.Cities.name] }.toList()
}
