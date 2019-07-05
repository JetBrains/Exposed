package org.jetbrains.exposed.spring

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
open class Application {

    open fun main(args: Array<String>) {
        SpringApplication.run(Application::class.java, *args)
    }
}