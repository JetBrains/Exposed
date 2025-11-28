package org.jetbrains.exposed.v1.test.mixed

import org.junit.jupiter.params.Parameter
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.MethodSource

@ParameterizedClass(
    name = "container: {0}, jdbcDialect: {1}, r2dbcDialect: {2}, name: {3}",
    // JUnit5 is stricter & at least 1 arg must be set by default, so this allows 0 args to be set as parameters;
    // because SQLite and PostgresNG will create an empty arg, since they are only compatibly with JDBC
    allowZeroInvocations = true
)
@MethodSource("data")
abstract class MixedDatabaseTestsBase {

    companion object {
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

    @Parameter(0)
    lateinit var container: String

    @Parameter(1)
    lateinit var jdbcDialect: org.jetbrains.exposed.v1.tests.TestDB

    @Parameter(2)
    lateinit var r2dbcDialect: org.jetbrains.exposed.v1.r2dbc.tests.TestDB

    @Parameter(3)
    lateinit var testName: String
}
