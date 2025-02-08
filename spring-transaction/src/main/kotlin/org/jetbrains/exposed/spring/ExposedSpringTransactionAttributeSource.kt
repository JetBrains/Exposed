package org.jetbrains.exposed.spring

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.RollbackRuleAttribute
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute
import org.springframework.transaction.interceptor.TransactionAttribute
import org.springframework.transaction.interceptor.TransactionAttributeSource
import java.lang.reflect.Method

class ExposedSpringTransactionAttributeSource(
    private val delegate: TransactionAttributeSource = AnnotationTransactionAttributeSource()
) : TransactionAttributeSource {

    override fun getTransactionAttribute(method: Method, targetClass: Class<*>?): TransactionAttribute? {
        val attr = delegate.getTransactionAttribute(method, targetClass)
        if (attr is RuleBasedTransactionAttribute) {
            val rules = attr.rollbackRules.toMutableList()
            val containsExposed = rules.any { it.exceptionName == ExposedSQLException::class.java.name }
            if (!containsExposed) {
                rules.add(RollbackRuleAttribute(ExposedSQLException::class.java))
                attr.rollbackRules = rules
            }
        }
        return attr
    }
}
