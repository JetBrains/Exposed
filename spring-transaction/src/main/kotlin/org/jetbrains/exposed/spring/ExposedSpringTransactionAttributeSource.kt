package org.jetbrains.exposed.spring

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.RollbackRuleAttribute
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute
import org.springframework.transaction.interceptor.TransactionAttribute
import org.springframework.transaction.interceptor.TransactionAttributeSource
import java.lang.reflect.Method

/**
 * A [TransactionAttributeSource] that adds [ExposedSQLException] to the rollback rules of the delegate.
 *
 * @property delegate The delegate [TransactionAttributeSource] to use. Defaults to [AnnotationTransactionAttributeSource].
 * If you use a custom [TransactionAttributeSource], you can pass it here.
 */
class ExposedSpringTransactionAttributeSource(
    private val delegate: TransactionAttributeSource = AnnotationTransactionAttributeSource()
) : TransactionAttributeSource {

    private val rollbackExceptions = listOf(ExposedSQLException::class.java)

    override fun getTransactionAttribute(method: Method, targetClass: Class<*>?): TransactionAttribute? {
        val attr = delegate.getTransactionAttribute(method, targetClass)
        if (attr is RuleBasedTransactionAttribute) {
            val rules = attr.rollbackRules.toMutableList()
            val containsExposed = rules.any { it is RollbackRuleAttribute && it.exceptionName in rollbackExceptions.map(Class<*>::getName) }
            if (!containsExposed) {
                rules.add(RollbackRuleAttribute(ExposedSQLException::class.java))
                attr.rollbackRules = rules
            }
        }
        return attr
    }
}
