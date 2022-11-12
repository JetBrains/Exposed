package org.jetbrains.exposed.sql.functions.math

import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.DecimalColumnType
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import java.math.BigDecimal

/**
 * 	Returns the arc cosine of a number
 */
class ACosFunction<T : Number?>(expression: ExpressionWithColumnType<T>) : CustomFunction<BigDecimal?>(
    functionName = "ACOS",
    columnType = DecimalColumnType.INSTANCE,
    expr = arrayOf(expression)
)

/**
 * 	Returns the arc sine of a number
 */
class ASinFunction<T : Number?>(expression: ExpressionWithColumnType<T>) : CustomFunction<BigDecimal?>(
    functionName = "ASIN",
    columnType = DecimalColumnType.INSTANCE,
    expr = arrayOf(expression)
)

/**
 * 	Returns the arc tangent of a number
 */
class ATanFunction<T : Number?>(expression: ExpressionWithColumnType<T>) : CustomFunction<BigDecimal?>(
    functionName = "ATAN",
    columnType = DecimalColumnType.INSTANCE,
    expr = arrayOf(expression)
)

/**
 * 	Returns the cosine of a number
 */
class CosFunction<T : Number?>(expression: ExpressionWithColumnType<T>) : CustomFunction<BigDecimal?>(
    functionName = "COS",
    columnType = DecimalColumnType.INSTANCE,
    expr = arrayOf(expression)
)

/**
 * Returns the cotangent of a number
 */
class CotFunction<T : Number?>(expression: ExpressionWithColumnType<T>) : CustomFunction<BigDecimal?>(
    functionName = "COT",
    columnType = DecimalColumnType.INSTANCE,
    expr = arrayOf(expression)
)

/**
 * Convert a radian value into degrees:
 */
class DegreesFunction<T : Number?>(expression: ExpressionWithColumnType<T>) : CustomFunction<BigDecimal?>(
    functionName = "DEGREES",
    columnType = DecimalColumnType.INSTANCE,
    expr = arrayOf(expression)
)

/**
 * 	Returns the value of PI
 */
object PiFunction : CustomFunction<BigDecimal>(
    functionName = "PI",
    columnType = DecimalColumnType.INSTANCE,
    expr = arrayOf()
)

/**
 * Convert a radian value into degrees:
 */
class RadiansFunction<T : Number?>(expression: ExpressionWithColumnType<T>) : CustomFunction<BigDecimal?>(
    functionName = "RADIANS",
    columnType = DecimalColumnType.INSTANCE,
    expr = arrayOf(expression)
)

/**
 * 	Returns the sine of a number
 */
class SinFunction<T : Number?>(expression: ExpressionWithColumnType<T>) : CustomFunction<BigDecimal?>(
    functionName = "SIN",
    columnType = DecimalColumnType.INSTANCE,
    expr = arrayOf(expression)
)

/**
 * 	Returns the tangent of a number
 */
class TanFunction<T : Number?>(expression: ExpressionWithColumnType<T>) : CustomFunction<BigDecimal?>(
    functionName = "TAN",
    columnType = DecimalColumnType.INSTANCE,
    expr = arrayOf(expression)
)
