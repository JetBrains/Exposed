package org.jetbrains.exposed.sql.vendors

import io.r2dbc.spi.IsolationLevel

/** Base class responsible for providing values for all supported metadata properties in a database. */
abstract class PropertyProvider {
    /** Returns the string used to quote SQL identifiers. */
    open val identifierQuoteString: String
        get() = "\""

    /** Returns whether unquoted SQL identifiers are treated as case-insensitive and stored in upper case. */
    open val storesUpperCaseIdentifiers: Boolean
        get() = false

    /** Returns whether quoted SQL identifiers are treated as case-insensitive and stored in upper case. */
    open val storesUpperCaseQuotedIdentifiers: Boolean
        get() = false

    /** Returns whether unquoted SQL identifiers are treated as case-insensitive and stored in lower case. */
    open val storesLowerCaseIdentifiers: Boolean
        get() = false

    /** Returns whether quoted SQL identifiers are treated as case-insensitive and stored in lower case. */
    open val storesLowerCaseQuotedIdentifiers: Boolean
        get() = false

    /** Returns whether unquoted SQL identifiers are treated as case-sensitive and stored in mixed case. */
    open val supportsMixedCaseIdentifiers: Boolean
        get() = false

    /** Returns whether quoted SQL identifiers are treated as case-sensitive and stored in mixed case. */
    open val supportsMixedCaseQuotedIdentifiers: Boolean
        get() = true

    /** Returns all additional characters that can be used in unquoted identifier names. */
    open val extraNameCharacters: String
        get() = ""

    /** Returns whether the syntax ALTER TABLE with ADD COLUMN is supported. */
    open val supportsAlterTableWithAddColumn: Boolean
        get() = true

    /** Returns whether getting multiple ResultSet objects is supported from a single execution. */
    open val supportsMultipleResultSets: Boolean
        get() = true

    /** Returns whether the statement SELECT FOR UPDATE is supported. */
    open val supportsSelectForUpdate: Boolean
        get() = true

    /** Returns the default transaction [IsolationLevel]. */
    open val defaultTransactionIsolation: IsolationLevel
        get() = IsolationLevel.READ_COMMITTED

    /** Returns the maximum number of characters allowed for a column identifier name. */
    open val maxColumnNameLength: Int
        get() = 0

    /** Returns a comma-separated list of all SQL keywords additional to the standard SQL:2003 list. */
    abstract fun sqlKeywords(): String
}
