package com.example

import com.example.bean.EmailUser
import com.example.hints.ReflectHints
import com.example.hints.ResourceHints
import kotlinx.coroutines.coroutineScope
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ImportRuntimeHints

@ImportRuntimeHints(value = [ResourceHints::class, ReflectHints::class])
@SpringBootApplication
@EnableConfigurationProperties(value = [EmailUser::class])
private class Application

//  detekt --build-upon-default-config --config ..\..\detekt\detekt-config.yml --plugins ..\..\detekt\detekt-formatting-1.23.8.jar  --input .
internal suspend fun main(args: Array<String>): Unit = coroutineScope {
    @Suppress("SpreadOperator")
    runApplication<Application>(*args)
}
