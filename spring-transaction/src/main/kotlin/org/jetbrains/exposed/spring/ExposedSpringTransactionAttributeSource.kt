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
    private val delegate: TransactionAttributeSource = AnnotationTransactionAttributeSource(),
    private val rollbackExceptions: List<Class<ExposedSQLException>> = listOf(ExposedSQLException::class.java)
) : TransactionAttributeSource {

    override fun getTransactionAttribute(method: Method, targetClass: Class<*>?): TransactionAttribute? {
        val attr = delegate.getTransactionAttribute(method, targetClass)
        if (attr is RuleBasedTransactionAttribute) {
            val rules = attr.rollbackRules.toMutableList()

            rollbackExceptions.forEach { exception ->
                val exceptionName = exception.name
                val containsException = rules.any {
                    it is RollbackRuleAttribute && it.exceptionName == exceptionName
                }

                if (!containsException) {
                    rules.add(RollbackRuleAttribute(exception))
                }
            }

            attr.rollbackRules = rules
        }
        return attr
    }
}
