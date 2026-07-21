package org.jetbrains.exposed.v1.spring7.transaction

import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.RollbackRuleAttribute
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute
import java.sql.SQLException
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpringTransactionAttributeSourceConcurrencyTest {

    @Test
    fun `delegate's cached attribute is not mutated by source`() {
        val delegate = AnnotationTransactionAttributeSource()
        val source = ExposedSpringTransactionAttributeSource(delegate)
        val method = Sample::class.java.getMethod("tx")
        val targetClass = Sample::class.java

        val before = (delegate.getTransactionAttribute(method, targetClass) as RuleBasedTransactionAttribute)
            .rollbackRules.toList()

        repeat(50) { source.getTransactionAttribute(method, targetClass) }

        val after = (delegate.getTransactionAttribute(method, targetClass) as RuleBasedTransactionAttribute)
            .rollbackRules.toList()

        assertEquals(
            before,
            after,
            "Delegate's cached rollbackRules must not be mutated by ExposedSpringTransactionAttributeSource"
        )
    }

    @Test
    fun `concurrent getTransactionAttribute calls are thread-safe`() {
        val source = ExposedSpringTransactionAttributeSource()
        val method = Sample::class.java.getMethod("tx")
        val targetClass = Sample::class.java
        val sqlExceptionName = SQLException::class.java.name

        val threads = 16
        val iterations = 1_000
        val pool = Executors.newFixedThreadPool(threads)
        val errors = CopyOnWriteArrayList<Throwable>()

        try {
            val tasks = (1..threads).map {
                Callable {
                    repeat(iterations) {
                        runCatching {
                            val attr = source.getTransactionAttribute(method, targetClass)
                                as RuleBasedTransactionAttribute
                            check(
                                attr.rollbackRules.any {
                                    it is RollbackRuleAttribute && it.exceptionName == sqlExceptionName
                                }
                            ) { "SQLException rollback rule is missing from the returned attribute" }
                        }.onFailure { errors.add(it) }
                    }
                }
            }
            pool.invokeAll(tasks)
        } finally {
            pool.shutdown()
            pool.awaitTermination(60, TimeUnit.SECONDS)
        }

        assertTrue(
            errors.isEmpty(),
            "Expected no errors under concurrent invocation, got ${errors.size}: ${errors.firstOrNull()}"
        )
    }

    internal class Sample {
        @Transactional
        open fun tx() = Unit
    }
}
