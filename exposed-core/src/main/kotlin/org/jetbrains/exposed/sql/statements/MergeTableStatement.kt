package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction

/**
 * Represents an SQL MERGE statement. It encapsulates the logic to perform conditional updates, insertions,
 * or deletions.
 *
 * Here is only the part specific for the Table as a source implementation.
 * Look into [MergeBaseStatement] to find the base implementation of that command.
 *
 * @property dest The destination [Table] where records will be merged into.
 * @property source The source [Table] from which records are taken to compare with `dest`.
 * @property on The join condition [Op<Boolean>] that specifies how to match records in both `source` and `dest`.
 */
open class MergeTableStatement(
    dest: Table,
    private val source: Table,
    private val on: Op<Boolean>
) : MergeBaseStatement(dest) {
    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val result = transaction.db.dialect.functionProvider.merge(table, source, transaction, whenBranches, on)
        return result
    }
}
