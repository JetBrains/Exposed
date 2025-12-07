package org.jetbrains.exposed.v1.spring.boot.r2dbc

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.r2dbc.autoconfigure.R2dbcTransactionManagerAutoConfiguration
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@SpringBootApplication(exclude = [R2dbcTransactionManagerAutoConfiguration::class])
open class Application {

    open fun main(args: Array<String>) {
        SpringApplication.run(Application::class.java, *args)
    }
}
