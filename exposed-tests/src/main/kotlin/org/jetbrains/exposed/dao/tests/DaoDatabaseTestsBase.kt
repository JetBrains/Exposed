package org.jetbrains.exposed.dao.tests

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.tests.TestDB

//enum class DaoTestDB(connection: () -> String, driver: String, user: String = "root", pass: String = "",
//                  beforeConnection: () -> Unit = {}, afterTestFinished: () -> Unit = {}, db: Database? = null):
//		TestDB(connection, driver, user, pass, beforeConnection, afterTestFinished, db) {
//
//}