package org.jetbrains.exposed.dao.tests

import org.jetbrains.exposed.dao.DaoTransactionManager
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.DEFAULT_ISOLATION_LEVEL
import org.jetbrains.exposed.sql.transactions.DEFAULT_REPETITION_ATTEMPTS

abstract class DaoDatabaseTestsBase(): DatabaseTestsBase() {

	override fun connectWithManager(dbSettings: TestDB): Database {
		return dbSettings.connect(manager = { DaoTransactionManager(it, DEFAULT_ISOLATION_LEVEL, DEFAULT_REPETITION_ATTEMPTS) })
	}
}