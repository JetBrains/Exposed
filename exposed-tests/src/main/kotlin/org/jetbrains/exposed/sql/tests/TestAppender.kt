package org.jetbrains.exposed.sql.tests

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.Layout
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginFactory
import java.io.Serializable

/**
 * Appends log events of a specified level to a list using a layout.
 */
@Plugin(name = "TestAppender", category = "Core", elementType = "appender", printObject = false)
class TestAppender
private constructor(layout: Layout<out Serializable>, val level: Level) : AbstractAppender(
    "TestAppender",
    null,
    layout,
    true,
    Property.EMPTY_ARRAY
) {
    private val log: MutableList<LogEvent> = mutableListOf()

    override fun append(event: LogEvent) {
        if (event.level == level) { // add events of the specified level only
            log.add(event.toImmutable())
        }
    }

    fun getLog(): List<LogEvent> {
        return log
    }

    companion object {
        @PluginFactory
        @JvmStatic
        fun createAppender(
            layout: Layout<out Serializable>,
            level: Level
        ): TestAppender {
            return TestAppender(layout, level)
        }
    }
}
