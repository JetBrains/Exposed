package org.jetbrains.exposed.dao.r2dbc.tests

import org.jetbrains.exposed.v1.core.transactions.nullableTransactionScope
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB

internal var currentTestDB by nullableTransactionScope<TestDB>()
