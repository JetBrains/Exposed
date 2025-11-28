package org.jetbrains.exposed.v1.tests

import org.junit.jupiter.params.Parameter
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.MethodSource

@ParameterizedClass(name = "{0}")
@MethodSource("data")
abstract class LogDbInTestName {
    @Parameter(0)
    lateinit var dialect: String

    companion object {
        @JvmStatic
        fun data() = TestDB.enabledDialects().map { arrayOf(it.name) }
    }
}
