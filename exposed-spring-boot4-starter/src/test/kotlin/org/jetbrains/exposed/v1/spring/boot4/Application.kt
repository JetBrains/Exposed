package org.jetbrains.exposed.v1.spring.boot4

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@SpringBootApplication(exclude = [DataSourceTransactionManagerAutoConfiguration::class])
open class Application {

    open fun main(args: Array<String>) {
        SpringApplication.run(Application::class.java, *args)
    }
}
