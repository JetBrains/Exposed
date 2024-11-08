package org.jetbrains.exposed.sql.statements

// uncomment when execute's location outside of Statement is finalized

// import org.jetbrains.exposed.sql.InternalApi
// import org.jetbrains.exposed.sql.Transaction
//
// /**
// * Executes the SQL statement directly in the provided [transaction] and returns the generated result,
// * or `null` if either no result was retrieved or if the transaction blocked statement execution.
// */
// potential BREAKING CHANGE with imports
// @OptIn(InternalApi::class) // this shouldn't be correct, as was fully public before
// suspend fun <T> Statement<T>.execute(transaction: Transaction): T? = transaction.blockOrExecute(this)
