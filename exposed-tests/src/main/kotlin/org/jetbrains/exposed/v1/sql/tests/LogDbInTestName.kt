package org.jetbrains.exposed.v1.sql.tests

import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
abstract class LogDbInTestName {
    @Parameterized.Parameter(0)
    lateinit var dialect: String

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() = TestDB.enabledDialects().map { arrayOf(it.name) }
    }
}
