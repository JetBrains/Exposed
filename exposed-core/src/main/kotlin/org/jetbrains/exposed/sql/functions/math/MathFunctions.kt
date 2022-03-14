package org.jetbrains.exposed.sql.functions.math

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialectIfAvailable
import java.math.BigDecimal
import java.math.MathContext

/**
 * Returns the absolute value of a number
 */
class AbsFunction<T : Number?>(expression: ExpressionWithColumnType<T>) : CustomFunction<T>(
    functionName = "ABS",
    _columnType = expression.columnType,
    expr = arrayOf(expression)
)

/**
 * Returns the smallest integer value that is >= a number
 */
class CeilingFunction<T : Number?>(expression: ExpressionWithColumnType<T>) : CustomFunction<Long?>(
    functionName = if (currentDialectIfAvailable is SQLiteDialect || currentDialectIfAvailable is OracleDialect) "CEIL" else "CEILING",
    _columnType = LongColumnType(),
    expr = arrayOf(expression)
)

/**
 * 	Returns e raised to the power of a specified number
 */
class ExpFunction<T : Number?>(expression: ExpressionWithColumnType<T>) : CustomFunction<BigDecimal?>(
    functionName = "EXP",
    _columnType = DecimalColumnType.INSTANCE,
    expr = arrayOf(expression)
)

/**
 * 	Returns the largest integer value that is <= to a number
 */
class FloorFunction<T : Number?>(expression: ExpressionWithColumnType<T>) : CustomFunction<Long?>(
    functionName = "FLOOR",
    _columnType = LongColumnType(),
    expr = arrayOf(expression)
)

/**
 * 	Returns the value of a number raised to the power of another number
 */
class PowerFunction<B : Number?, E : Number?>(
    base: ExpressionWithColumnType<B>,
    exponent: Expression<E>,
    precision: Int = MathContext.DECIMAL64.precision,
    /** Count of decimal digits in the fractional part. */
    scale: Int = 10
) : CustomFunction<BigDecimal?>(
    functionName = "POWER",
    _columnType = DecimalColumnType(precision, scale),
    expr = arrayOf(base, exponent)
)

/**
 * 	Rounds a number to a specified number of decimal places
 */
class RoundFunction<T : Number?>(expression: ExpressionWithColumnType<T>, scale: Int) : CustomFunction<BigDecimal?>(
    functionName = "ROUND",
    _columnType = DecimalColumnType(MathContext.DECIMAL64.precision, scale).apply { nullable = true },
    expr = arrayOf(expression, intLiteral(scale))
)

/**
 * Returns the sign of a number:
 *  -1 - negative number
 *  0 - number is 0
 *  1 - positive number
 */
class SignFunction<T : Number?>(expression: ExpressionWithColumnType<T>) : CustomFunction<Int?>(
    functionName = "SIGN",
    _columnType = IntegerColumnType(),
    expr = arrayOf(expression)
)

/**
 * 	Returns the square root of a number
 */
class SqrtFunction<T : Number?>(expression: ExpressionWithColumnType<T>) : CustomFunction<BigDecimal?>(
    functionName = "SQRT",
    _columnType = DecimalColumnType.INSTANCE,
    expr = arrayOf(expression)
)
