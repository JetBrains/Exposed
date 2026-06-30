package com.example.processor

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.core.io.ClassPathResource
import java.util.Properties

internal class EmailEnvironmentPostProcessor : EnvironmentPostProcessor {

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment?,
        application: SpringApplication?
    ) {
        println("postProcessEnvironment...")
        logger.info("EmailEnvironmentPostProcessEnvironment")
        val resource = ClassPathResource("email-config.yml")
        val factory = YamlPropertiesFactoryBean()
        factory.setResources(resource)
        factory.afterPropertiesSet()
        factory.`object`?.let { properties: Properties ->
            logger.info("properties: ${properties.propertyNames().toList()}")
            val propertySource = PropertiesPropertySource("emailConfig", properties)
            environment?.propertySources?.addLast(propertySource)
        }
    }
}
