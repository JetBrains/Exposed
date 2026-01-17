package org.jetbrains.exposed.v1.dao.exceptions

import org.jetbrains.exposed.v1.core.Column

/**
 * An exception that provides information about a lazy [column] that was accessed
 * outside of a transaction scope.
 *
 * Lazy fields require an active transaction to load their values from the database.
 * To resolve this exception, access the field within a transaction block.
 *
 * @property column The lazy column that was accessed
 */
class LazyColumnAccessOutsideTransactionException(val column: Column<*>) :
    Exception(
        "Lazy field for column ${column.table.tableName}.${column.name} cannot be accessed outside of a transaction. " +
            "Access it while a transaction is active."
    )
