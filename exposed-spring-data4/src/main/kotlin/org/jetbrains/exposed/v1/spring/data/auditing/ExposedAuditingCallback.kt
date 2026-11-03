package org.jetbrains.exposed.v1.spring.data.auditing

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.spring.data.event.BeforeConvertCallback
import org.springframework.beans.factory.ObjectFactory
import org.springframework.core.Ordered
import org.springframework.data.auditing.IsNewAwareAuditingHandler

/**
 * Sets the audit properties of every entity before it is saved: the created and modified values for new entities,
 * the modified values for existing ones. Immutable entities are replaced by an audited copy.
 *
 * The handler is looked up lazily, so the callback can be created before the mapping context.
 */
internal class ExposedAuditingCallback(
    private val handler: ObjectFactory<IsNewAwareAuditingHandler>,
) : BeforeConvertCallback<Any>, Ordered {
    override fun onBeforeConvert(entity: Any, table: Table): Any = handler.getObject().markAudited(entity)

    override fun getOrder(): Int = AUDITING_ORDER

    private companion object {
        // same as Spring Data Relational: user callbacks with a lower order run before, others after auditing
        const val AUDITING_ORDER = 100
    }
}
