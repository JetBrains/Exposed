package org.jetbrains.exposed.v1.spring7.reactive.transaction

import io.r2dbc.spi.R2dbcException
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.RollbackRuleAttribute
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute
import org.springframework.transaction.interceptor.TransactionAttribute
import org.springframework.transaction.interceptor.TransactionAttributeSource
import java.lang.reflect.Method

/**
 * A [TransactionAttributeSource] that adds `ExposedR2dbcException` to the rollback rules of the delegate.
 *
 * @property delegate The delegate [TransactionAttributeSource] to use. Defaults to [AnnotationTransactionAttributeSource].
 * If you use a custom [TransactionAttributeSource], you can pass it here.
 */
class ExposedSpringReactiveTransactionAttributeSource(
    private val delegate: TransactionAttributeSource = AnnotationTransactionAttributeSource(),
    private val rollbackExceptions: List<Class<out Throwable>> = listOf(R2dbcException::class.java)
) : TransactionAttributeSource {

    override fun getTransactionAttribute(method: Method, targetClass: Class<*>?): TransactionAttribute? {
        val attr = delegate.getTransactionAttribute(method, targetClass)
        if (attr is RuleBasedTransactionAttribute) {
            val rules = attr.rollbackRules.toMutableList()
            rollbackExceptions.forEach { exception ->
                val containsException = rules.any {
                    it.exceptionName == exception.name
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
