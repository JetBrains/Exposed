package org.jetbrains.exposed.v1.plugin.core.tables.codegen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.plugin.core.migration.MigrationLogger
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.ColumnDefinition
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.DefaultValue
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.ExposedModule
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.defaultValueFor
import org.jetbrains.exposed.v1.plugin.core.tables.schema.ColumnInfo
import org.jetbrains.exposed.v1.plugin.core.tables.schema.ForeignKeyInfo
import org.jetbrains.exposed.v1.plugin.core.tables.schema.IndexMetadata
import org.jetbrains.exposed.v1.plugin.core.tables.schema.PrimaryKeyInfo
import org.jetbrains.exposed.v1.plugin.core.tables.schema.TableMetadata
import org.jetbrains.exposed.v1.plugin.core.tables.warn

/**
 * Builds the KotlinPoet [TypeSpec] of a [Table] object that mirrors a single [table]. This will either be a plain `Table`,
 * or one of the `IdTable` subclasses when the [GenerationContext] decides so from the primary key.
 *
 * Columns whose type cannot be mapped are omitted from the generated object, logged via [logger],
 * and listed as "TO-DO" entries in the object's KDoc so that they are not silently lost.
 */
@OptIn(InternalApi::class)
internal class TableCodeBuilder(
    private val table: TableMetadata,
    /** Name of the generated object, as decided by [TablePlanner]. */
    private val objectName: String,
    /** The other generated tables, for foreign key references, this table's kind, and the definition of every column. */
    private val context: GenerationContext,
    private val logger: MigrationLogger,
) {
    private val kind: TableKind = context.kindOf(table)

    fun build(): GeneratedTable {
        val builder = TypeSpec.objectBuilder(objectName).declareSupertype()
        val requiredOptIns = linkedSetOf<ClassName>()
        val requiredModules = linkedSetOf<ExposedModule>()
        val requiredImports = linkedSetOf<String>()
        // generated property name of every mapped column, keyed by column name, for references from keys and indices
        val propertyNames = linkedMapOf<String, String>()
        // names of the types nested in the object so far, which must not be redeclared
        val nestedTypeNames = hashSetOf<String>()
        // a self-reference to a column declared later cannot be chained on the property, because the referenced
        // property is not assigned yet when the referencing one initializes; it is declared in the init block instead
        val deferredSelfReferences = table.columns.mapNotNull { column ->
            column.foreignKey?.takeIf { it.referencesLaterColumnOf(column) }
        }

        context.cycleWith(table)?.let { others ->
            val message = "This table and ${others.joinToString { "'${it.name}'" }} reference each other in a cycle. Kotlin objects that " +
                "reference each other while initializing fail at first use, so remove one of the references by hand"
            builder.addKdoc("TODO: %L\n", message)
        }

        table.columns.forEach { column ->
            if (kind is TableKind.ExactId && column.name == kind.keyColumnName) {
                propertyNames[column.name] = "id"
                return@forEach
            }
            if (column.mapping != null && MODIFIER_CALL_PATTERN.containsMatchIn(column.mapping.code)) {
                logger.warn(
                    "The mapping of column '${column.name}' of table '${table.name}' contains a column modifier such as nullable() or " +
                        "index(). Modifiers are derived from the database and appended to the mapped code, so the generated code may apply one twice"
                )
            }
            val definition = context.definitionOf(column)
            if (definition == null) {
                builder.reportSkipped(
                    "Column '${column.name}' of table '${table.name}' has unsupported type '${column.metadata.sqlType}' " +
                        "(JDBC type ${column.metadata.jdbcType}) and was not generated"
                )
                return@forEach
            }
            definition.nestedType?.let { nestedType ->
                val nestedName = nestedType.name!!
                if (!nestedTypeNames.add(nestedName)) {
                    builder.reportSkipped(
                        "Column '${column.name}' of table '${table.name}' needs a nested type '$nestedName' that another " +
                            "column of the table already declares, and was not generated"
                    )
                    return@forEach
                }
                builder.addType(nestedType)
            }
            val propertyName = context.propertyNameOf(column.name, kind)
            if (propertyName in propertyNames.values) {
                builder.reportSkipped(
                    "Column '${column.name}' of table '${table.name}' would be generated as property '$propertyName', which another " +
                        "column already uses, and was not generated"
                )
                return@forEach
            }
            definition.requiredOptIn?.let(requiredOptIns::add)
            definition.requiredModule?.let(requiredModules::add)
            requiredImports.addAll(definition.imports)
            val property = column.toPropertySpec(definition, propertyName, deferReference = column.foreignKey in deferredSelfReferences)
            propertyNames[column.name] = property.name
            builder.addProperty(property)
        }

        if (kind !is TableKind.ExactId) {
            table.primaryKey?.let { primaryKey ->
                primaryKey.toPropertySpec(propertyNames)?.let(builder::addProperty)
            }
        }

        // everything that spans several columns, plus deferred self-references, goes into one init block,
        // but indices should always go first
        val initializerBlock = CodeBlock.builder()
            .add(table.compositeIndices.toInitializerBlock(propertyNames))
            .add((table.compositeForeignKeys + deferredSelfReferences).toForeignKeyBlock(propertyNames, builder))
            .build()
        if (initializerBlock.isNotEmpty()) {
            builder.addInitializerBlock(initializerBlock)
        }

        return GeneratedTable(builder.build(), requiredOptIns, requiredModules, requiredImports)
    }

    private fun TypeSpec.Builder.declareSupertype(): TypeSpec.Builder = when (val tableKind = kind) {
        is TableKind.Plain -> superclass(Table::class).addSuperclassConstructorParameter("%S", table.name)
        is TableKind.ExactId -> superclass(tableKind.className).apply {
            if (tableKind.sequenceName != null) {
                addSuperclassConstructorParameter("%S, %S, %S", table.name, tableKind.keyColumnName, tableKind.sequenceName)
            } else {
                addSuperclassConstructorParameter("%S, %S", table.name, tableKind.keyColumnName)
            }
        }
        is TableKind.GenericId -> superclass(ID_TABLE.parameterizedBy(tableKind.idType))
            .addSuperclassConstructorParameter("%S", table.name)
        is TableKind.CompositeId -> superclass(COMPOSITE_ID_TABLE).addSuperclassConstructorParameter("%S", table.name)
    }

    /**
     * Whether this single-column key points at a column of the same table that is declared after [column]. The key
     * column of an exact `IdTable` subclass is declared by the superclass, before any column of the body.
     */
    private fun ForeignKeyInfo.referencesLaterColumnOf(column: ColumnInfo): Boolean {
        if (targetSchema != table.reference.schema || targetTable != table.reference.tableName) return false
        val targetColumnName = targetColumnNames.single()
        if (kind is TableKind.ExactId && targetColumnName == kind.keyColumnName) return false
        val targetIndex = table.columns.indexOfFirst { it.name == targetColumnName }
        return targetIndex > table.columns.indexOf(column)
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun ColumnInfo.toPropertySpec(definition: ColumnDefinition, propertyName: String, deferReference: Boolean): PropertySpec {
        val initializer = CodeBlock.builder()
        val isEntityId = name in kind.entityIdColumnNames
        val key = foreignKey?.takeUnless { deferReference }
        val resolution = key?.let { resolveTarget(it, definition) }
        val target = resolution?.getOrNull()
        val referenceForm = target?.idTable?.takeUnless { definition.isMapped }
        if (key != null && referenceForm != null) {
            initializer.add("%N(%S, %N", if (metadata.nullable) "optReference" else "reference", name, referenceForm.objectName)
            key.appendReferenceOptions(initializer, nameArgument = "fkName")
        } else {
            initializer.add(definition.factoryCall(name))
            if (key != null && target != null) {
                initializer.add(".%N(%L", "references", target.code)
                key.appendReferenceOptions(initializer, nameArgument = "fkName")
            }
        }
        val unresolvedForeignKey = resolution?.exceptionOrNull()?.message
        when {
            metadata.autoIncrement -> initializer.add(".%N()", "autoIncrement")
            sequenceName == null -> Unit
            definition.isMapped -> logger.warn(
                "Column '$name' of table '${table.name}' takes its default value from sequence '$sequenceName', but its code is mapped " +
                    "by configuration, so the generator cannot tell whether autoIncrement(\"$sequenceName\") applies. Add it to the mapping if it does"
            )
            definition.supportsSequenceAutoIncrement -> initializer.add(".%N(%L)", "autoIncrement", sequenceCode())
            else -> logger.warn(
                "Column '$name' of table '${table.name}' takes its default value from sequence '$sequenceName', " +
                    "but Exposed only supports sequence-backed auto-increment for integer and long columns. The default was not generated"
            )
        }
        indices.forEach { index ->
            val function = if (index.unique) "uniqueIndex" else "index"
            if (index.isDefaultName) {
                initializer.add(".%N()", function)
            } else {
                initializer.add(".%N(%S)", function, index.name)
            }
        }
        val databaseGeneratedByDefault = reportedDefault?.takeIf { sequenceName == null }?.let { reported ->
            appendDefault(initializer, definition, reported)
        } ?: false
        if (generationExpression != null) {
            if (!databaseGeneratedByDefault) initializer.add(".%N()", "databaseGenerated")
            logger.warn(
                "Column '$name' of table '${table.name}' is generated by the database as ($generationExpression). " +
                    "The generated definition marks it with databaseGenerated(), but the expression is not reproduced in DDL. " +
                    "Add withDefinition(\"GENERATED ALWAYS AS ($generationExpression)\") manually if the table is created from this definition"
            )
        }
        if (isEntityId && referenceForm == null) {
            initializer.add(".%N()", "entityId")
        }
        if (metadata.nullable && referenceForm == null) {
            initializer.add(".%N()", "nullable")
        }

        val columnType = when {
            referenceForm != null -> ENTITY_ID.parameterizedBy(referenceForm.idType).asColumnType(metadata.nullable)
            definition.typeName == null -> INFERRED_TYPE_PLACEHOLDER
            isEntityId -> ENTITY_ID.parameterizedBy(definition.typeName).asColumnType(nullable = false)
            else -> definition.typeName.asColumnType(metadata.nullable)
        }
        val property = PropertySpec.builder(propertyName, columnType).initializer(initializer.build())
        if (kind is TableKind.GenericId && isEntityId) {
            property.addModifiers(KModifier.OVERRIDE)
        }
        definition.mappingLog?.let { note ->
            logger.lifecycle("Column '$name' of table '${table.name}': $note")
            property.addKdoc("%L\n", note)
        }
        if (generationExpression != null) {
            property.addKdoc("Generated by the database as: %L\n", generationExpression)
        }
        unresolvedForeignKey?.let { property.addKdoc("TODO: %L\n", it) }
        return property.build()
    }

    private fun TypeName.asColumnType(nullable: Boolean): TypeName = Column::class.asClassName().parameterizedBy(copy(nullable = nullable))

    private fun ColumnInfo.sequenceCode(): CodeBlock {
        val parameters = sequenceParameters ?: return CodeBlock.of("%S", sequenceName)
        val arguments = CodeBlock.builder().add("%S", sequenceName)
        // numbers are rendered as strings, since KotlinPoet groups the digits of a numeric literal (100_000)
        parameters.startWith?.let { arguments.add(", startWith = %L", it.toString()) }
        parameters.incrementBy?.let { arguments.add(", incrementBy = %L", it.toString()) }
        parameters.minValue?.let { arguments.add(", minValue = %L", it.toString()) }
        parameters.maxValue?.let { arguments.add(", maxValue = %L", it.toString()) }
        parameters.cycle?.let { arguments.add(", cycle = %L", it) }
        parameters.cache?.let { arguments.add(", cache = %L", it.toString()) }
        return CodeBlock.of("%T(%L)", SEQUENCE, arguments.build())
    }

    /**
     * Resolves the single target of [key] for the column declared with [definition]. A failure carries the message
     * that was logged, for the "TO-DO" on the property. A mapped column on either side is warned about, since the
     * reference requires both to have the same Kotlin type, which the generator cannot verify.
     */
    private fun ColumnInfo.resolveTarget(key: ForeignKeyInfo, definition: ColumnDefinition): Result<GenerationContext.Target> {
        val target = context.resolveTargets(key, table).map { it.single() }.getOrElse { failure ->
            val message = "Foreign key ${key.customName?.let { "'$it' " }.orEmpty()}of column '$name' of table '${table.name}' " +
                "references ${failure.message}, so the reference was not generated"
            logger.warn(message)
            return Result.failure(GenerationContext.UnresolvedTargetException(message))
        }
        if (definition.isMapped || target.isMapped) {
            logger.warn(
                "Column '$name' of table '${table.name}' references ${key.targetTable}.${key.targetColumnNames.single()} and one of " +
                    "the two columns is mapped by configuration. The reference requires both to have the same Kotlin type, which the " +
                    "generator cannot verify"
            )
        }
        return Result.success(target)
    }

    private fun ForeignKeyInfo.appendReferenceOptions(initializer: CodeBlock.Builder, nameArgument: String) {
        onDelete?.let { initializer.add(", onDelete = %T.%N", REFERENCE_OPTION, it.name) }
        onUpdate?.let { initializer.add(", onUpdate = %T.%N", REFERENCE_OPTION, it.name) }
        customName?.let { initializer.add(", $nameArgument = %S", it) }
        initializer.add(")")
    }

    private fun ColumnInfo.appendDefault(initializer: CodeBlock.Builder, definition: ColumnDefinition, reported: String): Boolean {
        if (definition.isMapped) {
            logger.warn(
                "Column '$name' of table '${table.name}' has the default $reported, but its code is mapped by configuration. " +
                    "Add .default(...) or .defaultExpression(...) to the mapping if Exposed should declare it"
            )
            return false
        }
        return when (val default = definition.defaultValueFor(reported, context.dateTimeColumnType, context.syntax)) {
            is DefaultValue.Literal -> {
                initializer.add(".%N(%L)", "default", default.code)
                false
            }
            is DefaultValue.Expression -> {
                initializer.add(".%N(%L)", "defaultExpression", default.code)
                false
            }
            is DefaultValue.Unsupported -> {
                initializer.add(".%N()", "databaseGenerated")
                logger.warn(
                    "Column '$name' of table '${table.name}' has the default $reported, which the generator cannot express as default() " +
                        "or defaultExpression(). The column is marked databaseGenerated() so that the database applies the default and " +
                        "Exposed never writes it. Replace that call with .defaultExpression(...) or .default(...) by hand if Exposed should " +
                        "set the value"
                )
                true
            }
        }
    }

    private fun PrimaryKeyInfo.toPropertySpec(propertyNames: Map<String, String>): PropertySpec? {
        val keyProperties = columnNames.mapNotNull { propertyNames[it] }
        if (keyProperties.size != columnNames.size) {
            logger.warn("The primary key of table '${table.name}' references a column that was not generated and was skipped")
            return null
        }
        val columnArguments = keyProperties.toPropertyReferences()
        val initializer = customName?.let { name ->
            CodeBlock.of("%N(%L, name = %S)", "PrimaryKey", columnArguments, name)
        } ?: CodeBlock.of("%N(%L)", "PrimaryKey", columnArguments)
        return PropertySpec.builder("primaryKey", Table.PrimaryKey::class.asClassName(), KModifier.OVERRIDE)
            .initializer(initializer)
            .build()
    }

    private fun List<IndexMetadata>.toInitializerBlock(propertyNames: Map<String, String>): CodeBlock {
        val block = CodeBlock.builder()
        forEach { index ->
            val columns = index.columnNames.mapNotNull { propertyNames[it] }
            if (columns.size != index.columnNames.size) {
                logger.warn("Index '${index.name}' of table '${table.name}' references a column that was not generated and was skipped")
                return@forEach
            }
            val columnArguments = columns.toPropertyReferences()
            when {
                index.unique && index.isDefaultName -> block.addStatement("%N(%L)", "uniqueIndex", columnArguments)
                index.unique -> block.addStatement("%N(%S, %L)", "uniqueIndex", index.name, columnArguments)
                index.isDefaultName -> block.addStatement("%N(false, %L)", "index", columnArguments)
                else -> block.addStatement("%N(%S, false, %L)", "index", index.name, columnArguments)
            }
        }
        return block.build()
    }

    /**
     * Renders each composite foreign key as `foreignKey(a to Target.x, b to Target.y, onDelete = ..., onUpdate = ...,
     * name = ...)`, the overload that pairs every referencing column with its referenced column, which is exactly what
     * the database reports. A key that includes a column that was not generated, or whose target cannot be resolved,
     * is skipped with a warning and a "TO-DO" on the object (through [objectBuilder]).
     */
    private fun List<ForeignKeyInfo>.toForeignKeyBlock(propertyNames: Map<String, String>, objectBuilder: TypeSpec.Builder): CodeBlock {
        val block = CodeBlock.builder()
        forEach { key ->
            val label = "Foreign key ${key.customName?.let { "'$it' " }.orEmpty()}of table '${table.name}' on columns ${key.columnNames.joinToString()}"
            val properties = key.columnNames.mapNotNull { propertyNames[it] }
            if (properties.size != key.columnNames.size) {
                objectBuilder.reportSkipped("$label includes a column that was not generated, so the key was not generated")
                return@forEach
            }
            val targets = context.resolveTargets(key, table).getOrElse { failure ->
                objectBuilder.reportSkipped("$label references ${failure.message}, so the key was not generated")
                return@forEach
            }
            val fromMapped = table.columns.any { it.name in key.columnNames && it.mapping != null }
            if (fromMapped || targets.any { it.isMapped }) {
                logger.warn(
                    "$label references ${key.targetTable} (${key.targetColumnNames.joinToString()}) and a column on one side is mapped by " +
                        "configuration. foreignKey() requires each pair to have the same Kotlin type, which the generator cannot verify"
                )
            }
            val pairs = properties.zip(targets) { property, target ->
                CodeBlock.of("%N to %L", property, target.code)
            }.joinToCode()
            val statement = CodeBlock.builder().add("%N(%L", "foreignKey", pairs)
            key.appendReferenceOptions(statement, nameArgument = "name")
            block.addStatement("%L", statement.build())
        }
        return block.build()
    }

    /** Renders these property names as a comma-separated argument list, escaping names that are Kotlin keywords. */
    private fun List<String>.toPropertyReferences(): CodeBlock = map { CodeBlock.of("%N", it) }.joinToCode()

    /** Logs a warning that something was not generated, and leaves the same [message] as a "TO-DO" in the object's KDoc. */
    private fun TypeSpec.Builder.reportSkipped(message: String) {
        logger.warn(message)
        addKdoc("TODO: %L\n", message)
    }

    private companion object {
        /**
         * Matches a call of a column modifier that the generator derives from the database and appends itself.
         * Defaults are never matched so they are never appended to mapped code, and a mapping may declare its own.
         */
        val MODIFIER_CALL_PATTERN: Regex =
            Regex("""\.(nullable|autoIncrement|index|uniqueIndex|databaseGenerated|references|check)\s*\(""")

        val REFERENCE_OPTION: ClassName = ClassName("org.jetbrains.exposed.v1.core", "ReferenceOption")
        val SEQUENCE: ClassName = ClassName("org.jetbrains.exposed.v1.core", "Sequence")
        val ID_TABLE: ClassName = ClassName(DAO_ID_PACKAGE, "IdTable")
        val COMPOSITE_ID_TABLE: ClassName = ClassName(DAO_ID_PACKAGE, "CompositeIdTable")
        val ENTITY_ID: ClassName = ClassName(DAO_ID_PACKAGE, "EntityID")
    }
}

/**
 * The placeholder type given to a property whose column code was configured, since KotlinPoet cannot omit a property
 * type; `TableGenerator` removes it from the written file so that Kotlin infers the type from the configured code.
 */
internal val INFERRED_TYPE_PLACEHOLDER: ClassName = ClassName("", "INFERRED_BY_KOTLIN")

/**
 * The generated `Table` object of a single database table.
 */
internal data class GeneratedTable(
    val typeSpec: TypeSpec,

    /** The opt-in marker annotations that a file holding [typeSpec] must declare. */
    val requiredOptIns: Set<ClassName>,

    /** The Exposed extension modules, other than `exposed-core`, that [typeSpec] depends on. */
    val requiredModules: Set<ExposedModule>,

    /** Fully qualified names that a file holding [typeSpec] must import for its configured column mappings. */
    val requiredImports: Set<String>,
)
