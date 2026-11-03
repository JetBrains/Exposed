package org.jetbrains.exposed.v1.spring.data.auditing

import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryConfigExtension
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.data.auditing.IsNewAwareAuditingHandler
import org.springframework.data.auditing.config.AuditingBeanDefinitionRegistrarSupport
import org.springframework.data.auditing.config.AuditingConfiguration
import org.springframework.data.config.ParsingUtils

/**
 * Registers, for [EnableExposedAuditing], an [IsNewAwareAuditingHandler] based on the Exposed mapping context and the
 * [ExposedAuditingCallback] that applies it before an entity is saved.
 */
internal class ExposedAuditingRegistrar : AuditingBeanDefinitionRegistrarSupport() {
    override fun getAnnotation(): Class<out Annotation> = EnableExposedAuditing::class.java

    override fun getAuditingHandlerBeanName(): String = AUDITING_HANDLER_BEAN_NAME

    override fun getAuditHandlerBeanDefinitionBuilder(configuration: AuditingConfiguration): BeanDefinitionBuilder {
        val builder = BeanDefinitionBuilder.rootBeanDefinition(IsNewAwareAuditingHandler::class.java, "from")
            .addConstructorArgReference(ExposedRepositoryConfigExtension.MAPPING_CONTEXT_BEAN_NAME)
        return configureDefaultAuditHandlerAttributes(configuration, builder)
    }

    override fun registerAuditListenerBeanDefinition(auditingHandlerDefinition: BeanDefinition, registry: BeanDefinitionRegistry) {
        val builder = BeanDefinitionBuilder.rootBeanDefinition(ExposedAuditingCallback::class.java)
            .addConstructorArgValue(ParsingUtils.getObjectFactoryBeanDefinition(auditingHandlerBeanName, registry))
        registerInfrastructureBeanWithId(builder.rawBeanDefinition, ExposedAuditingCallback::class.java.name, registry)
    }

    companion object {
        const val AUDITING_HANDLER_BEAN_NAME = "exposedAuditingHandler"
    }
}
