package org.jetbrains.exposed.v1.spring7.reactive.transaction

import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.transactions.TransactionsHolderProvider
import org.springframework.aop.Advisor
import org.springframework.aop.ClassFilter
import org.springframework.aop.MethodMatcher
import org.springframework.aop.Pointcut
import org.springframework.aop.ProxyMethodInvocation
import org.springframework.aop.support.AbstractPointcutAdvisor
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Role
import org.springframework.core.KotlinDetector
import org.springframework.core.Ordered
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

/**
 * Enables Exposed's annotation-driven transaction management capability, specifically for suspend `@Transactional` methods.
 *
 * To be used on any `@Configuration` class, always along with Spring's `@EnableTransactionManagement`, to configure
 * reactive transaction management using [SpringReactiveTransactionManager] with suspend `@Transactional` methods.
 *
 * ```kotlin
 * @Configuration
 * @EnableTransactionManagement
 * @EnableExposedReactiveTransactionManagement
 * open class AppConfig {
 *
 *     @Bean
 *     open fun fooRepository(): FooRepository = FooRepository()
 *
 *     @Bean
 *     open fun connectionFactory(): ConnectionFactory = ConnectionFactories.get("r2dbc:h2:...")
 *
 *     @Bean
 *     open fun transactionManager(connectionFactory: ConnectionFactory) = SpringReactiveTransactionManager(
 *         connectionFactory,
 *         R2dbcDatabaseConfig { explicitDialect = H2Dialect() }
 *     )
 * }
 * ```
 *
 * Without this annotation, any suspend `@Transactional` method invoked directly from a coroutine, which did not
 * register a [SpringReactiveTransactionContextElement] in its context, will likely fail with "No transaction in
 * context" errors whenever `TransactionManager.current()` is implicitly/explicitly called. Exposed's built-in
 * `suspendTransaction()` always starts a coroutine with this element in its context, so suspend `@Transactional` methods
 * will always be safe when invoked from within its lambda.
 *
 * Programmatic callers that drive Spring reactive transactions themselves, for example through
 * [org.springframework.transaction.reactive.TransactionalOperator], are outside the annotation-driven
 * management overseen by [EnableExposedReactiveTransactionManagement]. Using `TransactionalOperator` directly always
 * requires context being explicitly set by a wrapping [withExposedReactiveContext].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(ExposedReactiveTransactionManagementConfiguration::class)
annotation class EnableExposedReactiveTransactionManagement

/**
 * Infrastructure configuration imported by [EnableExposedReactiveTransactionManagement].
 */
@Configuration
open class ExposedReactiveTransactionManagementConfiguration {
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    open fun exposedReactiveTransactionContextAdvisor(): Advisor = ExposedReactiveTransactionContextAdvisor()
}

/**
 * A [AbstractPointcutAdvisor] that matches Kotlin suspend methods carrying a Spring transaction attribute
 * and augments their `Continuation` argument's [CoroutineContext] with [SpringReactiveTransactionContextElement] before
 * invocation starts. This ensures that transaction coroutine context projection is always present by the time Spring's
 * suspend-invocation reflection path runs the target method.
 */
internal class ExposedReactiveTransactionContextAdvisor :
    AbstractPointcutAdvisor(),
    Ordered {

    override fun getOrder(): Int = HIGHEST_PRECEDENCE

    override fun getPointcut(): Pointcut = TransactionalSuspendFunctionPointcut

    override fun getAdvice(): ExposedReactiveTransactionContextInterceptor = ExposedReactiveTransactionContextInterceptor

    // applies similar attribute source lookup logic as Spring's package-private `TransactionAttributeSourcePointcut`
    // spring-framework/blob/main/spring-tx/src/main/java/org/springframework/transaction/interceptor/TransactionAttributeSourcePointcut
    private object TransactionalSuspendFunctionPointcut : Pointcut {
        private val attributeSource = AnnotationTransactionAttributeSource(false)

        override fun getClassFilter(): ClassFilter = ClassFilter { targetClass ->
            attributeSource.isCandidateClass(targetClass)
        }

        override fun getMethodMatcher(): MethodMatcher = object : MethodMatcher {
            override fun matches(method: Method, targetClass: Class<*>): Boolean = KotlinDetector.isSuspendingFunction(method) &&
                attributeSource.getTransactionAttribute(method, targetClass) != null

            override fun isRuntime(): Boolean = false

            override fun matches(method: Method, targetClass: Class<*>, vararg args: Any?): Boolean = matches(method, targetClass)
        }
    }
}

/**
 * Intercepts suspend methods carrying a Spring transaction attribute to modify the original behavior
 * and ensure that a [SpringReactiveTransactionContextElement] is in the coroutine context prior to invocation.
 */
@OptIn(InternalApi::class)
internal object ExposedReactiveTransactionContextInterceptor : MethodInterceptor {
    private val contextElement = SpringReactiveTransactionContextElement()

    override fun invoke(invocation: MethodInvocation): Any? {
        val args = invocation.arguments
        val continuation = args.lastOrNull() as? Continuation<*> ?: return invocation.proceed()

        val proxyInvocation = invocation as? ProxyMethodInvocation
            ?: error("Expected ProxyMethodInvocation but found ${invocation::class.qualifiedName}")

        val newArgs = args.copyOf()
        newArgs[newArgs.size - 1] = wrapContinuation(continuation)
        @Suppress("SpreadOperator")
        proxyInvocation.setArguments(*newArgs)

        val snapshot = TransactionStackState(TransactionsHolderProvider.holder.snapshot())
        return SpringReactiveTransactionHandoff.withSnapshot(snapshot) {
            proxyInvocation.proceed()
        }
    }

    private fun wrapContinuation(continuation: Continuation<*>): Continuation<*> {
        val augmentedContext = continuation.context + contextElement

        return object : Continuation<Any?> {
            override val context: CoroutineContext = augmentedContext

            @Suppress("UNCHECKED_CAST")
            override fun resumeWith(result: Result<Any?>) = (continuation as Continuation<Any?>).resumeWith(result)
        }
    }
}
