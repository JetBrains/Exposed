package org.jetbrains.exposed.v1.test.mixed

import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
abstract class MixedDatabaseTestsBase {

    companion object {
        @Parameters(name = "container: {0}, jdbcDialect: {1}, r2dbcDialect: {2}, name: {3}")
        @JvmStatic
        fun data(): Collection<Array<Any>> {
            val name = System.getProperty("exposed.test.name")
            val container = System.getProperty("exposed.test.container")
            val abc: Collection<Array<Any>> = org.jetbrains.exposed.v1.r2dbc.tests.TestDB.enabledDialects()
                .mapNotNull { r2dbcDb ->
                    org.jetbrains.exposed.v1.tests.TestDB.enabledDialects()
                        .find { r2dbcDb.name == it.name }
                        ?.let { jdbcDb ->
                            arrayOf(container, jdbcDb, r2dbcDb, name)
                        }
                }
            return abc
        }
    }

    @Parameterized.Parameter(0)
    lateinit var container: String

    @Parameterized.Parameter(1)
    lateinit var jdbcDialect: org.jetbrains.exposed.v1.tests.TestDB

    @Parameterized.Parameter(2)
    lateinit var r2dbcDialect: org.jetbrains.exposed.v1.r2dbc.tests.TestDB

    @Parameterized.Parameter(3)
    lateinit var testName: String
}
