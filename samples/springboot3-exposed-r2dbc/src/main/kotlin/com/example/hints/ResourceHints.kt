package com.example.hints

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.core.io.ClassPathResource

internal class ResourceHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val resource = ClassPathResource("email-config.yml")
        hints.resources().registerResource(resource)
    }
}
