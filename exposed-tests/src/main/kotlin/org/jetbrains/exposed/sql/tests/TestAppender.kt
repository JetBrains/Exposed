package org.jetbrains.exposed.sql.tests

import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.Layout
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginAttribute
import org.apache.logging.log4j.core.config.plugins.PluginElement
import org.apache.logging.log4j.core.config.plugins.PluginFactory
import java.io.Serializable

@Plugin(name = "TestAppender", category = "Core", elementType = "appender", printObject = false)
class TestAppender
private constructor(name: String, filter: Filter?, layout: Layout<out Serializable>) :
    AbstractAppender(name, filter, layout, true, Property.EMPTY_ARRAY) {
    private val log: MutableList<LogEvent> = mutableListOf()

    override fun append(event: LogEvent) {
        log.add(event.toImmutable())
    }

    fun getLog(): List<LogEvent> {
        return log
    }

    companion object {
        @PluginFactory
        @JvmStatic
        fun createAppender(
            @PluginAttribute("name") name: String,
            @PluginElement("Layout") layout: Layout<out Serializable>,
            @PluginElement("Filter") filter: Filter?
        ): TestAppender {
            return TestAppender(name, filter, layout)
        }
    }
}
