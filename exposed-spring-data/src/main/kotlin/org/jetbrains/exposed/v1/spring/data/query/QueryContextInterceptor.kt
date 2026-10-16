package org.jetbrains.exposed.v1.spring.data.query

import org.aopalliance.intercept.MethodInvocation
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedEntityMapper
import org.springframework.aop.ProxyMethodInvocation

internal class QueryContextInterceptor(
    private val table: IdTable<*>,
    private val mapper: ExposedEntityMapper<Any>,
) : org.aopalliance.intercept.MethodInterceptor {
    override fun invoke(invocation: MethodInvocation): Any? {
        val method = invocation.method
        if (!method.isDefault) return invocation.proceed()
        val proxy = (invocation as ProxyMethodInvocation).proxy
        return ExposedQueryContextHolder.withContext(
            ExposedQueryContext(
                repository = proxy,
                mapper = mapper,
                table = table,
                method = method,
            )
        ) {
            invocation.proceed()
        }
    }
}
