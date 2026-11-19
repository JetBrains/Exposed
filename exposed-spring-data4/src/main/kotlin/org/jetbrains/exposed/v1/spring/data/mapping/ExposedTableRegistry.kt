package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.ForeignKeyConstraint
import org.jetbrains.exposed.v1.core.Table
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.util.ClassUtils
import org.springframework.util.ReflectionUtils
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.*

/**
 * All [tables] known to one application context (or one repository factory), used to find the to-many relations of a table:
 * [incoming] lists the foreign keys that reference it. This is the reverse direction of the foreign keys declared on a table.
 *
 * Tables are equal if their names are, so they are compared by identity here.
 */
internal class ExposedTableRegistry(tables: Collection<Table>) {
    /** The registered tables, without duplicates (by identity), in registration order. */
    val tables: List<Table> = tables.distinctByIdentity()

    private val incoming: Map<Table, List<ForeignKeyConstraint>> by lazy {
        val index = IdentityHashMap<Table, MutableList<ForeignKeyConstraint>>()
        this.tables.forEach { table ->
            table.foreignKeys
                .filter { it.references.size == 1 }
                .forEach { index.getOrPut(it.targetTable) { mutableListOf() } += it }
        }
        index
    }

    /** The single-column foreign keys of the registered tables that reference [table], compared by identity. */
    fun incoming(table: Table): List<ForeignKeyConstraint> = incoming[table].orEmpty()

    override fun toString(): String = "ExposedTableRegistry(${tables.joinToString { it.tableName }})"

    companion object {
        /** A registry without tables, used when none is given, for example by a repository factory created without Spring. */
        val EMPTY = ExposedTableRegistry(emptyList())

        /**
         * Finds the [Table] objects (top-level, nested or companion, of any visibility) in [basePackages] and their sub-packages.
         * Table classes that are not objects are skipped.
         *
         * @throws IllegalStateException if the initializer of a table object fails
         */
        fun scan(basePackages: Collection<String>, classLoader: ClassLoader?): ExposedTableRegistry {
            val scanner = TableScanner()
            classLoader?.let { scanner.resourceLoader = DefaultResourceLoader(it) }
            val tables = basePackages.distinct()
                .flatMap { scanner.findCandidateComponents(it) }
                .mapNotNull { it.beanClassName }
                .distinct()
                .mapNotNull { tableObject(it, classLoader) }
            return ExposedTableRegistry(tables)
        }

        /** The object instance of the class [className], or `null` if the class is not a Kotlin object. */
        private fun tableObject(className: String, classLoader: ClassLoader?): Table? {
            val type = ClassUtils.forName(className, classLoader)
            val field = objectField(type) ?: return null
            ReflectionUtils.makeAccessible(field)
            return try {
                field.get(null) as? Table
            } catch (e: ExceptionInInitializerError) {
                throw IllegalStateException("The initializer of the table object $className failed: ${e.cause ?: e}", e.cause ?: e)
            }
        }

        /** The static field that holds the instance of the object [type]: `INSTANCE`, or the field of a companion object in its outer class. */
        private fun objectField(type: Class<*>): Field? {
            fun Field.holds(type: Class<*>) = Modifier.isStatic(modifiers) && this.type == type
            return type.declaredFields.singleOrNull { it.name == "INSTANCE" && it.holds(type) }
                ?: type.declaringClass?.declaredFields?.singleOrNull { it.name == type.simpleName && it.holds(type) }
        }

        private fun Collection<Table>.distinctByIdentity(): List<Table> {
            val seen = Collections.newSetFromMap(IdentityHashMap<Table, Boolean>())
            return filter { seen.add(it) }
        }
    }

    /** Accepts every class assignable to [Table], including nested ones; the default only accepts independent concrete classes. */
    private class TableScanner : ClassPathScanningCandidateComponentProvider(false) {
        init {
            addIncludeFilter(AssignableTypeFilter(Table::class.java))
        }

        override fun isCandidateComponent(beanDefinition: AnnotatedBeanDefinition): Boolean {
            val metadata = beanDefinition.metadata
            return metadata.isConcrete && !metadata.isAnnotation
        }
    }
}
