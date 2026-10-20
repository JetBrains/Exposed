package org.jetbrains.exposed.v1.spring.data.query

import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.jetbrains.exposed.v1.spring.data.mapping.ProjectionResolver
import org.springframework.aop.ProxyMethodInvocation

internal class QueryContextInterceptor(
    private val projectionResolver: ProjectionResolver<*, *>,
) : MethodInterceptor {
    override fun invoke(invocation: MethodInvocation): Any? {
        val method = invocation.method
        if (!method.isDefault) return invocation.proceed()
        val proxy = (invocation as ProxyMethodInvocation).proxy
        return ExposedQueryContextHolder.withContext(
            ExposedQueryContext(
                repository = proxy,
                method = method,
                projectionResolver = projectionResolver,
            )
        ) {
            invocation.proceed()
        }
    }
}
