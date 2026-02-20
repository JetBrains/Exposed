package com.example.hints

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

internal class ReflectHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        // Java Method
        // hints.reflection().registerType(Result.Companion::class.java){ typeHintBuilder: TypeHint.Builder ->
        //     typeHintBuilder.withMembers(MemberCategory.INVOKE_DECLARED_METHODS)
        // }
        // Kotlin Extension Method
        // no use...

        // hints.reflection().registerType<ResolverConfigurationImpl> { typeHintBuilder: TypeHint.Builder ->
        //     typeHintBuilder.withMembers( MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
        // }
    }
}
