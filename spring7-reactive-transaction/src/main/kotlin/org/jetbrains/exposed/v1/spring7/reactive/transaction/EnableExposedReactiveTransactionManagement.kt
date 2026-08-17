package org.jetbrains.exposed.v1.spring7.reactive.transaction

import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.transactions.captureTransactionsThreadLocalState
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
 * Enables Exposed's coroutine transaction context projection for `@Transactional suspend` methods.
 *
 * Import this annotation alongside Spring's `@EnableTransactionManagement` on any `@Configuration` class
 * that uses [SpringReactiveTransactionManager] with suspending `@Transactional` methods.
 *
 * Spring 7's AOP suspending-method invocation path (`AopUtils.invokeJoinpointUsingReflection`) invokes the
 * target suspend function using the *caller's* coroutine context (minus `Job`), rather than adding
 * Reactor automatic context propagation itself. Without this annotation, a `@Transactional suspend`
 * method invoked directly from a coroutine that never separately installed
 * [SpringReactiveTransactionContextElement] (for example, one started through
 * [withExposedReactiveTransactionContext] or Exposed's own `suspendTransaction`) will not have that
 * element in its context, and `TransactionManager.current()` / `currentTransactionOrNull()` will not
 * resolve the transaction Spring is managing for that call.
 *
 * This annotation registers an infrastructure-role [Advisor] that augments the suspend continuation of any
 * matching method with [SpringReactiveTransactionContextElement] before Spring's own transactional advice
 * runs, guaranteeing that coroutine transaction context projection is present regardless of whether the
 * caller installed it explicitly.
 *
 * Only suspending methods that carry a Spring transaction attribute (that is, `@Transactional` on the
 * method or its declaring class) are matched, so this annotation never forces AOP proxying of beans that
 * were not already proxied for transaction management. Programmatic callers that drive Spring reactive
 * transactions themselves, for example through
 * [org.springframework.transaction.reactive.TransactionalOperator], are outside the annotation-driven
 * surface and must install the context explicitly with [withExposedReactiveTransactionContext].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(ExposedReactiveTransactionManagementConfiguration::class)
annotation class EnableExposedReactiveTransactionManagement

/**
 * Infrastructure configuration imported by [EnableExposedReactiveTransactionManagement].
 * @suppress
 */
@Configuration
open class ExposedReactiveTransactionManagementConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    open fun exposedReactiveTransactionContextAdvisor(): Advisor = ExposedReactiveTransactionContextAdvisor()
}

/**
 * An [Advisor] that matches Kotlin suspending methods carrying a Spring transaction attribute and augments
 * their `Continuation` argument's [CoroutineContext] with [SpringReactiveTransactionContextElement] before
 * invocation proceeds, so that coroutine transaction context projection is present by the time Spring's
 * suspend-invocation reflection path (or any later advice, such as
 * [org.springframework.transaction.interceptor.TransactionInterceptor]) runs the target method.
 *
 * The pointcut deliberately mirrors Spring's own transactional pointcut instead of matching every
 * suspending method: matching all suspending methods would force auto-proxy creation for every bean that
 * merely has a suspend function, which fails context refresh for final Kotlin classes (CGLIB cannot
 * subclass them) and silently switches interface-based beans to JDK proxies. Restricting to methods that
 * already have a transaction attribute keeps the proxying footprint identical to what
 * `@EnableTransactionManagement` alone requires.
 * @suppress
 */
internal class ExposedReactiveTransactionContextAdvisor :
    AbstractPointcutAdvisor(),
    Ordered {

    override fun getOrder(): Int = HIGHEST_PRECEDENCE

    override fun getPointcut(): Pointcut = TransactionalSuspendingFunctionPointcut

    override fun getAdvice(): ExposedReactiveTransactionContextInterceptor = ExposedReactiveTransactionContextInterceptor

    /**
     * Spring's own `TransactionAttributeSourcePointcut` is package-private and final, so it cannot be
     * composed here; this pointcut applies the same `AnnotationTransactionAttributeSource` lookup directly.
     */
    private object TransactionalSuspendingFunctionPointcut : Pointcut {
        // publicMethodsOnly = false so that non-public @Transactional suspend methods are matched too,
        // matching the attribute source Spring's own transaction advice uses for proxy-mode advising.
        private val attributeSource = AnnotationTransactionAttributeSource(false)

        override fun getClassFilter(): ClassFilter = ClassFilter { targetClass ->
            attributeSource.isCandidateClass(targetClass)
        }

        override fun getMethodMatcher(): MethodMatcher = object : MethodMatcher {
            override fun matches(method: Method, targetClass: Class<*>): Boolean =
                KotlinDetector.isSuspendingFunction(method) &&
                    attributeSource.getTransactionAttribute(method, targetClass) != null

            override fun isRuntime(): Boolean = false

            override fun matches(method: Method, targetClass: Class<*>, vararg args: Any?): Boolean =
                matches(method, targetClass)
        }
    }
}

/**
 * @suppress
 */
@OptIn(InternalApi::class)
internal object ExposedReactiveTransactionContextInterceptor : MethodInterceptor {

    private val contextElement = SpringReactiveTransactionContextElement()

    override fun invoke(invocation: MethodInvocation): Any? {
        val args = invocation.arguments
        val continuation = args.lastOrNull() as? Continuation<*>
            ?: return invocation.proceed()

        val proxyInvocation = invocation as? ProxyMethodInvocation
            ?: error("Expected ProxyMethodInvocation but found ${invocation::class.qualifiedName}")

        val newArgs = args.copyOf()
        newArgs[newArgs.size - 1] = wrapContinuation(continuation)
        @Suppress("SpreadOperator")
        proxyInvocation.setArguments(*newArgs)

        // Captured here, synchronously on the calling thread, before Spring's reactive transaction
        // machinery (createTransactionIfNecessary / doGetTransaction / doBegin) builds and subscribes its
        // Mono chain. See SpringReactiveTransactionHandoff for why doBegin cannot instead rely on the
        // coroutine element installed above.
        val snapshot = captureTransactionsThreadLocalState()
        return SpringReactiveTransactionHandoff.withSnapshot(snapshot) {
            proxyInvocation.proceed()
        }
    }

    private fun wrapContinuation(continuation: Continuation<*>): Continuation<*> {
        // The element is stateless, so a single shared instance is reused across all invocations.
        val augmentedContext = continuation.context + contextElement

        return object : Continuation<Any?> {
            override val context: CoroutineContext = augmentedContext

            @Suppress("UNCHECKED_CAST")
            override fun resumeWith(result: Result<Any?>) = (continuation as Continuation<Any?>).resumeWith(result)
        }
    }
}
