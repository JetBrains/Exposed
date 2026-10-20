package org.jetbrains.exposed.samples.springdata.nativeimage

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.spring.data.auditing.EnableExposedAuditing
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.Optional

/**
 * The data source is Spring Boot's embedded H2 database; Exposed uses the transaction manager auto-configured by the starter.
 *
 * [CustomerAge] is only used as a dynamic `Class<T>` projection (see [CustomerRepository.findByEmail]), so it can't be
 * discovered at build time and is registered with [RegisterReflectionForBinding].
 */
@SpringBootApplication(exclude = [DataSourceTransactionManagerAutoConfiguration::class])
@EnableExposedAuditing(auditorAwareRef = "sampleAuditor", dateTimeProviderRef = "sampleClock")
@RegisterReflectionForBinding(CustomerAge::class)
class NativeSampleApplication {
    @Bean
    fun sampleAuditor(): SampleAuditor = SampleAuditor()

    /** A second [AuditorAware]: auditing only uses [sampleAuditor] if the bean name survives the AOT processing. */
    @Bean
    fun otherAuditor(): AuditorAware<String> = AuditorAware { Optional.of("other") }

    @Bean
    fun sampleClock(): SampleClock = SampleClock()

    /** A second [DateTimeProvider], see [otherAuditor]. */
    @Bean
    fun otherClock(): DateTimeProvider = DateTimeProvider { Optional.of(Instant.EPOCH) }

    @Bean
    fun emailNormalizingCallback(): EmailNormalizingCallback = EmailNormalizingCallback()

    @Bean
    fun schemaInitializer(transactionManager: PlatformTransactionManager): ApplicationRunner = ApplicationRunner {
        TransactionTemplate(transactionManager).executeWithoutResult { SchemaUtils.create(Customers, Notes, Articles) }
    }
}

fun main(args: Array<String>) {
    @Suppress("SpreadOperator")
    runApplication<NativeSampleApplication>(*args)
}
