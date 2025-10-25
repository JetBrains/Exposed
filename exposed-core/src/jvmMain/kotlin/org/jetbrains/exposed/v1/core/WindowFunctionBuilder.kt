package org.jetbrains.exposed.v1.core

/** Returns the number of the current row within its partition, counting from 1. */
fun rowNumber(): RowNumber = RowNumber()

/** Returns the rank of the current row, with gaps; that is, the row_number of the first row in its peer group. */
fun rank(): Rank = Rank()

/** Returns the rank of the current row, without gaps; this function effectively counts peer groups. */
fun denseRank(): DenseRank = DenseRank()

/**
 * Returns the relative rank of the current row, that is (rank - 1) / (total partition rows - 1).
 * The value thus ranges from 0 to 1 inclusive.
 */
fun percentRank(): PercentRank = PercentRank()

/**
 * Returns the cumulative distribution, that is (number of partition rows preceding or peers with current row) /
 * (total partition rows). The value thus ranges from 1/N to 1.
 */
fun cumeDist(): CumeDist = CumeDist()

/** Returns an integer ranging from 1 to the [numBuckets], dividing the partition as equally as possible. */
fun ntile(numBuckets: ExpressionWithColumnType<Int>): Ntile = Ntile(numBuckets)

/**
 * Returns value evaluated at the row that is [offset] rows before the current row within the partition;
 * if there is no such row, instead returns [defaultValue].
 * Both [offset] and [defaultValue] are evaluated with respect to the current row.
 */
fun <T> ExpressionWithColumnType<T>.lag(
    offset: ExpressionWithColumnType<Int> = intLiteral(1),
    defaultValue: ExpressionWithColumnType<T>? = null
): Lag<T> = Lag(this, offset, defaultValue)

/**
 * Returns value evaluated at the row that is [offset] rows after the current row within the partition;
 * if there is no such row, instead returns [defaultValue].
 * Both [offset] and [defaultValue] are evaluated with respect to the current row.
 */
fun <T> ExpressionWithColumnType<T>.lead(
    offset: ExpressionWithColumnType<Int> = intLiteral(1),
    defaultValue: ExpressionWithColumnType<T>? = null
): Lead<T> = Lead(this, offset, defaultValue)

/**
 * Returns value evaluated at the row that is the first row of the window frame.
 */
fun <T> ExpressionWithColumnType<T>.firstValue(): FirstValue<T> = FirstValue(this)

/**
 * Returns value evaluated at the row that is the last row of the window frame.
 */
fun <T> ExpressionWithColumnType<T>.lastValue(): LastValue<T> = LastValue(this)

/**
 * Returns value evaluated at the row that is the [n]'th row of the window frame
 * (counting from 1); null if no such row.
 */
fun <T> ExpressionWithColumnType<T>.nthValue(n: ExpressionWithColumnType<Int>): NthValue<T> = NthValue(this, n)
