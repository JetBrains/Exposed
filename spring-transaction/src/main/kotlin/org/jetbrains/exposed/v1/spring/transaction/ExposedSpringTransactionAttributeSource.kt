package org.jetbrains.exposed.v1.spring.transaction

import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.RollbackRuleAttribute
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute
import org.springframework.transaction.interceptor.TransactionAttribute
import org.springframework.transaction.interceptor.TransactionAttributeSource
import java.lang.reflect.Method
import java.sql.SQLException

/**
 * A [TransactionAttributeSource] that adds [ExposedSQLException] to the rollback rules of the delegate.
 *
 * The returned [TransactionAttribute] is a defensive copy, so the delegate's cached instance is never
 * mutated. This makes [getTransactionAttribute] safe to invoke concurrently.
 *
 * @property delegate The delegate [TransactionAttributeSource] to use. Defaults to [AnnotationTransactionAttributeSource].
 * If you use a custom [TransactionAttributeSource], you can pass it here.
 */
class ExposedSpringTransactionAttributeSource(
    private val delegate: TransactionAttributeSource = AnnotationTransactionAttributeSource(),
    private val rollbackExceptions: List<Class<out Throwable>> = listOf(SQLException::class.java)
) : TransactionAttributeSource {

    override fun getTransactionAttribute(method: Method, targetClass: Class<*>?): TransactionAttribute? {
        val original = delegate.getTransactionAttribute(method, targetClass) ?: return null
        if (original !is RuleBasedTransactionAttribute) return original

        // The delegate (e.g. AnnotationTransactionAttributeSource) caches the returned attribute,
        // so mutating it here would be a data race across concurrent callers. Make a defensive
        // copy and only modify the copy.
        val copy = RuleBasedTransactionAttribute(original)
        val rules = copy.rollbackRules.toMutableList()
        rollbackExceptions.forEach { exception ->
            val containsException = rules.any {
                it is RollbackRuleAttribute && it.exceptionName == exception.name
            }
            if (!containsException) {
                rules.add(RollbackRuleAttribute(exception))
            }
        }
        copy.rollbackRules = rules
        return copy
    }
}
