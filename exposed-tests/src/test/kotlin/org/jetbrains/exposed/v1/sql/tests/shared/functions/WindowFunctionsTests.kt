package org.jetbrains.exposed.v1.sql.tests.shared.functions
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.cumeDist
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.denseRank
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.firstValue
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.lag
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.lastValue
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.lead
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.minus
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.nthValue
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.ntile
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.percentRank
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.plus
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.rank
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.rowNumber
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.TestDB
import org.jetbrains.exposed.v1.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.sql.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.v1.sql.tests.shared.dml.withSales
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class WindowFunctionsTests : DatabaseTestsBase() {

    private val supportsCountDistinctAsWindowFunction = TestDB.ALL_H2 + TestDB.ORACLE
    private val supportsStatisticsAggregateFunctions = TestDB.ALL - listOf(TestDB.SQLSERVER, TestDB.SQLITE)
    private val supportsNthValueFunction = TestDB.ALL - TestDB.SQLSERVER
    private val supportsExpressionsInWindowFunctionArguments = TestDB.ALL - TestDB.ALL_MYSQL
    private val supportsExpressionsInWindowFrameClause = TestDB.ALL - TestDB.ALL_MYSQL_MARIADB - TestDB.SQLSERVER
    private val supportsDefaultValueInLeadLagFunctions = TestDB.ALL - TestDB.ALL_MARIADB
    private val supportsRangeModeWithOffsetFrameBound = TestDB.ALL - TestDB.SQLSERVER

    @Suppress("LongMethod")
    @Test
    fun testWindowFunctions() {
        withSales(excludeSettings = listOf(TestDB.MYSQL_V5)) { testDb, sales ->
            sales.assertWindowFunctionDefinition(
                rowNumber().over().partitionBy(sales.year, sales.product).orderBy(sales.amount),
                listOf(1, 1, 2, 1, 1, 1, 2)
            )
            sales.assertWindowFunctionDefinition(
                rank().over().partitionBy(sales.year, sales.product).orderBy(sales.amount),
                listOf(1, 1, 2, 1, 1, 1, 2)
            )
            sales.assertWindowFunctionDefinition(
                denseRank().over().partitionBy(sales.year, sales.product).orderBy(sales.amount),
                listOf(1, 1, 2, 1, 1, 1, 2)
            )
            sales.assertWindowFunctionDefinition(
                percentRank().over().partitionBy(sales.year, sales.product).orderBy(sales.amount),
                listOfBigDecimal("0", "0", "1", "0", "0", "0", "1").filterNotNull()
            )
            sales.assertWindowFunctionDefinition(
                cumeDist().over().partitionBy(sales.year, sales.product).orderBy(sales.amount),
                listOfBigDecimal("0.5", "1", "1", "0.5", "1", "1", "1").filterNotNull()
            )
            sales.assertWindowFunctionDefinition(
                ntile(intLiteral(2)).over().partitionBy(sales.year, sales.product).orderBy(sales.amount),
                listOf(1, 1, 2, 1, 1, 1, 2)
            )
            sales.assertWindowFunctionDefinition(
                sales.amount.lag(intLiteral(1)).over().partitionBy(sales.year, sales.product).orderBy(sales.amount),
                listOfBigDecimal(null, null, "550.1", null, null, null, "1620.1")
            )
            sales.assertWindowFunctionDefinition(
                sales.amount.lead(intLiteral(1)).over().partitionBy(sales.year, sales.product).orderBy(sales.amount),
                listOfBigDecimal("900.3", null, null, "1870.9", null, null, null)
            )

            if (testDb in supportsDefaultValueInLeadLagFunctions) {
                sales.assertWindowFunctionDefinition(
                    sales.amount.lag(intLiteral(1), decimalLiteral(BigDecimal("-1.0"))).over()
                        .partitionBy(sales.year, sales.product).orderBy(sales.amount),
                    listOfBigDecimal("-1", "-1", "550.1", "-1", "-1", "-1", "1620.1")
                )
                sales.assertWindowFunctionDefinition(
                    sales.amount.lead(intLiteral(1), decimalLiteral(BigDecimal("-1.0"))).over()
                        .partitionBy(sales.year, sales.product).orderBy(sales.amount),
                    listOfBigDecimal("900.3", "-1", "-1", "1870.9", "-1", "-1", "-1")
                )
            }

            sales.assertWindowFunctionDefinition(
                sales.amount.firstValue().over().partitionBy(sales.year, sales.product).orderBy(sales.amount),
                listOfBigDecimal("550.1", "1500.25", "550.1", "1620.1", "650.7", "10.2", "1620.1").filterNotNull()
            )
            sales.assertWindowFunctionDefinition(
                sales.amount.lastValue().over().partitionBy(sales.year, sales.product).orderBy(sales.amount),
                listOfBigDecimal("550.1", "1500.25", "900.3", "1620.1", "650.7", "10.2", "1870.9").filterNotNull()
            )

            if (testDb in supportsNthValueFunction) {
                sales.assertWindowFunctionDefinition(
                    sales.amount.nthValue(intLiteral(2)).over().partitionBy(sales.year, sales.product)
                        .orderBy(sales.amount),
                    listOfBigDecimal(null, null, "900.3", null, null, null, "1870.9")
                )
            }

            if (testDb in supportsExpressionsInWindowFunctionArguments) {
                sales.assertWindowFunctionDefinition(
                    ntile(intLiteral(1) + intLiteral(1)).over().partitionBy(sales.year, sales.product)
                        .orderBy(sales.amount),
                    listOf(1, 1, 2, 1, 1, 1, 2)
                )
                sales.assertWindowFunctionDefinition(
                    sales.amount.lag(intLiteral(2) - intLiteral(1)).over().partitionBy(sales.year, sales.product)
                        .orderBy(sales.amount),
                    listOfBigDecimal(null, null, "550.1", null, null, null, "1620.1")
                )
                sales.assertWindowFunctionDefinition(
                    sales.amount.lead(intLiteral(2) - intLiteral(1)).over().partitionBy(sales.year, sales.product)
                        .orderBy(sales.amount),
                    listOfBigDecimal("900.3", null, null, "1870.9", null, null, null)
                )

                if (testDb in supportsNthValueFunction) {
                    sales.assertWindowFunctionDefinition(
                        sales.amount.nthValue(intLiteral(1) + intLiteral(1)).over()
                            .partitionBy(sales.year, sales.product).orderBy(sales.amount),
                        listOfBigDecimal(null, null, "900.3", null, null, null, "1870.9")
                    )
                }
            }
        }
    }

    @Suppress("LongMethod")
    @Test
    fun testAggregateFunctionsAsWindowFunctions() {
        withSales(excludeSettings = listOf(TestDB.MYSQL_V5)) { testDb, sales ->
            sales.assertWindowFunctionDefinition(
                sales.amount.min().over().partitionBy(sales.year, sales.product),
                listOfBigDecimal("550.1", "1500.25", "550.1", "1620.1", "650.7", "10.2", "1620.1")
            )
            sales.assertWindowFunctionDefinition(
                sales.amount.max().over().partitionBy(sales.year, sales.product),
                listOfBigDecimal("900.3", "1500.25", "900.3", "1870.9", "650.7", "10.2", "1870.9")
            )
            sales.assertWindowFunctionDefinition(
                sales.amount.avg().over().partitionBy(sales.year, sales.product),
                listOfBigDecimal("725.2", "1500.25", "725.2", "1745.5", "650.7", "10.2", "1745.5")
            )
            sales.assertWindowFunctionDefinition(
                sales.amount.sum().over().partitionBy(sales.year, sales.product),
                listOfBigDecimal("1450.4", "1500.25", "1450.4", "3491", "650.7", "10.2", "3491")
            )
            sales.assertWindowFunctionDefinition(
                sales.amount.count().over().partitionBy(sales.year, sales.product),
                listOf(2, 1, 2, 2, 1, 1, 2)
            )

            if (testDb in supportsStatisticsAggregateFunctions) {
                sales.assertWindowFunctionDefinition(
                    sales.amount.stdDevPop().over().partitionBy(sales.year, sales.product),
                    listOfBigDecimal("175.1", "0", "175.1", "125.4", "0", "0", "125.4")
                )
                sales.assertWindowFunctionDefinition(
                    sales.amount.stdDevSamp().over().partitionBy(sales.year, sales.product),
                    listOfBigDecimal("247.63", null, "247.63", "177.34", null, null, "177.34")
                )
                sales.assertWindowFunctionDefinition(
                    sales.amount.varPop().over().partitionBy(sales.year, sales.product),
                    listOfBigDecimal("30660.01", "0", "30660.01", "15725.16", "0", "0", "15725.16")
                )
                sales.assertWindowFunctionDefinition(
                    sales.amount.varSamp().over().partitionBy(sales.year, sales.product),
                    listOfBigDecimal("61320.02", null, "61320.02", "31450.32", null, null, "31450.32")
                )
            }

            if (testDb in supportsCountDistinctAsWindowFunction) {
                sales.assertWindowFunctionDefinition(
                    sales.amount.countDistinct().over().partitionBy(sales.year, sales.product),
                    listOf(2, 1, 2, 2, 1, 1, 2)
                )
            }
        }
    }

    @Test
    fun testPartitionByClause() {
        withSales(excludeSettings = listOf(TestDB.MYSQL_V5)) { _, sales ->
            sales.assertWindowFunctionDefinition(
                sales.amount.sum().over(),
                listOfBigDecimal("7102.55", "7102.55", "7102.55", "7102.55", "7102.55", "7102.55", "7102.55")
            )
            sales.assertWindowFunctionDefinition(
                sales.amount.sum().over().partitionBy(),
                listOfBigDecimal("7102.55", "7102.55", "7102.55", "7102.55", "7102.55", "7102.55", "7102.55")
            )
            sales.assertWindowFunctionDefinition(
                sales.amount.sum().over().partitionBy(sales.year),
                listOfBigDecimal("2950.65", "2950.65", "2950.65", "4151.9", "4151.9", "4151.9", "4151.9")
            )
            sales.assertWindowFunctionDefinition(
                sales.amount.sum().over().partitionBy(sales.year, sales.product),
                listOfBigDecimal("1450.4", "1500.25", "1450.4", "3491", "650.7", "10.2", "3491")
            )
        }
    }

    @Test
    fun testOrderByClause() {
        withSales(excludeSettings = listOf(TestDB.MYSQL_V5)) { _, sales ->
            sales.assertWindowFunctionDefinition(
                sales.amount.sum().over(),
                listOfBigDecimal("7102.55", "7102.55", "7102.55", "7102.55", "7102.55", "7102.55", "7102.55")
            )
            sales.assertWindowFunctionDefinition(
                sales.amount.sum().over().orderBy(),
                listOfBigDecimal("7102.55", "7102.55", "7102.55", "7102.55", "7102.55", "7102.55", "7102.55")
            )
            sales.assertWindowFunctionDefinition(
                sales.amount.sum().over().orderBy(sales.year),
                listOfBigDecimal("2950.65", "2950.65", "2950.65", "7102.55", "7102.55", "7102.55", "7102.55")
            )
            sales.assertWindowFunctionDefinition(
                sales.amount.sum().over()
                    .orderBy(sales.year to SortOrder.DESC, sales.product to SortOrder.ASC_NULLS_FIRST),
                listOfBigDecimal("7102.55", "5652.15", "7102.55", "3501.2", "4151.9", "10.2", "3501.2")
            )
        }
    }

    @Suppress("LongMethod")
    @Test
    fun testWindowFrameClause() {
        withSales(excludeSettings = listOf(TestDB.MYSQL_V5)) { testDb, sales ->
            sales.assertWindowFunctionDefinition(
                sumAmountPartitionByYearProductOrderByAmount(sales).rows(WindowFrameBound.unboundedPreceding()),
                listOfBigDecimal("550.1", "1500.25", "1450.4", "1620.1", "650.7", "10.2", "3491")
            )
            sales.assertWindowFunctionDefinition(
                sumAmountPartitionByYearProductOrderByAmount(sales).rows(WindowFrameBound.offsetPreceding(1)),
                listOfBigDecimal("550.1", "1500.25", "1450.4", "1620.1", "650.7", "10.2", "3491")
            )
            sales.assertWindowFunctionDefinition(
                sumAmountPartitionByYearProductOrderByAmount(sales).rows(WindowFrameBound.currentRow()),
                listOfBigDecimal("550.1", "1500.25", "900.3", "1620.1", "650.7", "10.2", "1870.9")
            )
            sales.assertWindowFunctionDefinition(
                sumAmountPartitionByYearProductOrderByAmount(sales)
                    .rows(WindowFrameBound.offsetPreceding(1), WindowFrameBound.offsetFollowing(1)),
                listOfBigDecimal("1450.4", "1500.25", "1450.4", "3491", "650.7", "10.2", "3491")
            )
            sales.assertWindowFunctionDefinition(
                sumAmountPartitionByYearProductOrderByAmount(sales)
                    .rows(WindowFrameBound.offsetFollowing(1), WindowFrameBound.offsetFollowing(2)),
                listOfBigDecimal("900.3", null, null, "1870.9", null, null, null)
            )
            sales.assertWindowFunctionDefinition(
                sumAmountPartitionByYearProductOrderByAmount(sales)
                    .rows(WindowFrameBound.offsetPreceding(2), WindowFrameBound.offsetPreceding(1)),
                listOfBigDecimal(null, null, "550.1", null, null, null, "1620.1")
            )
            sales.assertWindowFunctionDefinition(
                sumAmountPartitionByYearProductOrderByAmount(sales)
                    .rows(WindowFrameBound.offsetPreceding(2), WindowFrameBound.currentRow()),
                listOfBigDecimal("550.1", "1500.25", "1450.4", "1620.1", "650.7", "10.2", "3491")
            )
            sales.assertWindowFunctionDefinition(
                sumAmountPartitionByYearProductOrderByAmount(sales)
                    .rows(WindowFrameBound.currentRow(), WindowFrameBound.offsetFollowing(2)),
                listOfBigDecimal("1450.4", "1500.25", "900.3", "3491", "650.7", "10.2", "1870.9")
            )
            sales.assertWindowFunctionDefinition(
                sumAmountPartitionByYearProductOrderByAmount(sales)
                    .rows(WindowFrameBound.currentRow(), WindowFrameBound.currentRow()),
                listOfBigDecimal("550.1", "1500.25", "900.3", "1620.1", "650.7", "10.2", "1870.9")
            )

            if (testDb in supportsExpressionsInWindowFrameClause) {
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales).rows(
                        WindowFrameBound.offsetPreceding(intLiteral(1) + intLiteral(1)),
                        WindowFrameBound.offsetFollowing(intLiteral(1) + intLiteral(1))
                    ),
                    listOfBigDecimal("1450.4", "1500.25", "1450.4", "3491", "650.7", "10.2", "3491")
                )
            }

            sales.assertWindowFunctionDefinition(
                sumAmountPartitionByYearProductOrderByAmount(sales)
                    .rows(WindowFrameBound.currentRow(), WindowFrameBound.unboundedFollowing()),
                listOfBigDecimal("1450.4", "1500.25", "900.3", "3491", "650.7", "10.2", "1870.9")
            )

            sales.assertWindowFunctionDefinition(
                sumAmountPartitionByYearProductOrderByAmount(sales).range(WindowFrameBound.unboundedPreceding()),
                listOfBigDecimal("550.1", "1500.25", "1450.4", "1620.1", "650.7", "10.2", "3491")
            )
            sales.assertWindowFunctionDefinition(
                sumAmountPartitionByYearProductOrderByAmount(sales).range(WindowFrameBound.currentRow()),
                listOfBigDecimal("550.1", "1500.25", "900.3", "1620.1", "650.7", "10.2", "1870.9")
            )

            if (testDb in supportsRangeModeWithOffsetFrameBound) {
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales).range(WindowFrameBound.offsetPreceding(1)),
                    listOfBigDecimal("550.1", "1500.25", "900.3", "1620.1", "650.7", "10.2", "1870.9")
                )
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales)
                        .range(WindowFrameBound.offsetPreceding(1), WindowFrameBound.offsetFollowing(1)),
                    listOfBigDecimal("550.1", "1500.25", "900.3", "1620.1", "650.7", "10.2", "1870.9")
                )
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales)
                        .range(WindowFrameBound.offsetFollowing(1), WindowFrameBound.offsetFollowing(2)),
                    listOfBigDecimal(null, null, null, null, null, null, null)
                )
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales)
                        .range(WindowFrameBound.offsetPreceding(2), WindowFrameBound.offsetPreceding(1)),
                    listOfBigDecimal(null, null, null, null, null, null, null)
                )
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales)
                        .range(WindowFrameBound.offsetPreceding(2), WindowFrameBound.currentRow()),
                    listOfBigDecimal("550.1", "1500.25", "900.3", "1620.1", "650.7", "10.2", "1870.9")
                )
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales)
                        .range(WindowFrameBound.currentRow(), WindowFrameBound.offsetFollowing(2)),
                    listOfBigDecimal("550.1", "1500.25", "900.3", "1620.1", "650.7", "10.2", "1870.9")
                )

                if (testDb in supportsExpressionsInWindowFrameClause) {
                    sales.assertWindowFunctionDefinition(
                        sumAmountPartitionByYearProductOrderByAmount(sales).range(
                            WindowFrameBound.offsetPreceding(intLiteral(1) + intLiteral(1)),
                            WindowFrameBound.offsetFollowing(intLiteral(1) + intLiteral(1))
                        ),
                        listOfBigDecimal("550.1", "1500.25", "900.3", "1620.1", "650.7", "10.2", "1870.9")
                    )
                }
            }
            sales.assertWindowFunctionDefinition(
                sumAmountPartitionByYearProductOrderByAmount(sales)
                    .range(WindowFrameBound.currentRow(), WindowFrameBound.unboundedFollowing()),
                listOfBigDecimal("1450.4", "1500.25", "900.3", "3491", "650.7", "10.2", "1870.9")
            )
            sales.assertWindowFunctionDefinition(
                sumAmountPartitionByYearProductOrderByAmount(sales)
                    .range(WindowFrameBound.currentRow(), WindowFrameBound.currentRow()),
                listOfBigDecimal("550.1", "1500.25", "900.3", "1620.1", "650.7", "10.2", "1870.9")
            )

            if (currentDialect.supportsWindowFrameGroupsMode) {
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales).groups(WindowFrameBound.unboundedPreceding()),
                    listOfBigDecimal("550.1", "1500.25", "1450.4", "1620.1", "650.7", "10.2", "3491")
                )
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales).groups(WindowFrameBound.offsetPreceding(1)),
                    listOfBigDecimal("550.1", "1500.25", "1450.4", "1620.1", "650.7", "10.2", "3491")
                )
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales).groups(WindowFrameBound.currentRow()),
                    listOfBigDecimal("550.1", "1500.25", "900.3", "1620.1", "650.7", "10.2", "1870.9")
                )
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales)
                        .groups(WindowFrameBound.offsetPreceding(1), WindowFrameBound.offsetFollowing(1)),
                    listOfBigDecimal("1450.4", "1500.25", "1450.4", "3491", "650.7", "10.2", "3491")
                )
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales)
                        .groups(WindowFrameBound.offsetFollowing(1), WindowFrameBound.offsetFollowing(2)),
                    listOfBigDecimal("900.3", null, null, "1870.9", null, null, null)
                )
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales)
                        .groups(WindowFrameBound.offsetPreceding(2), WindowFrameBound.offsetPreceding(1)),
                    listOfBigDecimal(null, null, "550.1", null, null, null, "1620.1")
                )
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales)
                        .groups(WindowFrameBound.offsetPreceding(2), WindowFrameBound.currentRow()),
                    listOfBigDecimal("550.1", "1500.25", "1450.4", "1620.1", "650.7", "10.2", "3491")
                )
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales)
                        .groups(WindowFrameBound.currentRow(), WindowFrameBound.offsetFollowing(2)),
                    listOfBigDecimal("1450.4", "1500.25", "900.3", "3491", "650.7", "10.2", "1870.9")
                )
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales)
                        .groups(WindowFrameBound.currentRow(), WindowFrameBound.currentRow()),
                    listOfBigDecimal("550.1", "1500.25", "900.3", "1620.1", "650.7", "10.2", "1870.9")
                )
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales).groups(
                        WindowFrameBound.offsetPreceding(intLiteral(1) + intLiteral(1)),
                        WindowFrameBound.offsetFollowing(intLiteral(1) + intLiteral(1))
                    ),
                    listOfBigDecimal("1450.4", "1500.25", "1450.4", "3491", "650.7", "10.2", "3491")
                )
                sales.assertWindowFunctionDefinition(
                    sumAmountPartitionByYearProductOrderByAmount(sales)
                        .groups(WindowFrameBound.currentRow(), WindowFrameBound.unboundedFollowing()),
                    listOfBigDecimal("1450.4", "1500.25", "900.3", "3491", "650.7", "10.2", "1870.9")
                )
            }
        }
    }

    private fun <T> DMLTestsData.Sales.assertWindowFunctionDefinition(
        definition: WindowFunctionDefinition<T>,
        expectedResult: List<T>
    ) {
        val result = select(definition)
            .orderBy(
                year to SortOrder.ASC,
                month to SortOrder.ASC,
                product to SortOrder.ASC_NULLS_FIRST
            )
            .map { it[definition] }

        assertEqualLists(result, expectedResult)
    }

    private fun sumAmountPartitionByYearProductOrderByAmount(sales: DMLTestsData.Sales) =
        sales.amount.sum().over().partitionBy(sales.year, sales.product).orderBy(sales.amount)

    private fun listOfBigDecimal(vararg numbers: String?): List<BigDecimal?> {
        return numbers.map { it?.let { BigDecimal(it).setScale(2, RoundingMode.HALF_UP) } }
    }
}
