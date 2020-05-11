package org.jetbrains.exposed.sql.tests

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@Target(AnnotationTarget.FUNCTION)
annotation class RepeatableTest(val times: Int)

class RepeatableStatement(val times: Int, val statement: Statement) : Statement() {
    override fun evaluate() {
        require(times > 0)
        repeat(times) {
            statement.evaluate()
        }
    }
}

class RepeatableTestRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        val repeatAnnotation = description.annotations.filterIsInstance<RepeatableTest>().singleOrNull()
        return if (repeatAnnotation != null) {
            RepeatableStatement(repeatAnnotation.times, base)
        } else
            base
    }

}