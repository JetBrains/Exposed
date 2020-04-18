package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.GlobalStatementInterceptor
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.AbstractTransaction
import org.jetbrains.exposed.sql.transactions.ITransaction
import org.jetbrains.exposed.sql.vendors.inProperCase
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList

class Key<T>

@Suppress("UNCHECKED_CAST")

abstract class Transaction(db: Database, transactionIsolation: Int, outerTransaction: ITransaction?, currentStatement: PreparedStatementApi?, debug: Boolean) :
		AbstractTransaction(db, transactionIsolation, outerTransaction, currentStatement, debug) {
}

