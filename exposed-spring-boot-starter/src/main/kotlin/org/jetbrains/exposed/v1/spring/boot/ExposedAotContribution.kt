package org.jetbrains.exposed.v1.spring.boot

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.statements.Statement
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.jetbrains.exposed.v1.core.transactions.TransactionStore
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SizedIterable
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeReference
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.io.ClassPathResource
import org.springframework.core.type.filter.AssignableTypeFilter

/**
 * Class responsible for contributing, at compile time, the contracts needed for any reflection and
 * resource loading at runtime. Registering these runtime hints for Exposed classes and resources
 * ahead of time is required to run a Spring Boot application as a GraalVM native image.
 */
@Suppress("SpreadOperator")
class ExposedAotContribution : BeanFactoryInitializationAotProcessor {
    override fun processAheadOfTime(
        beanFactory: ConfigurableListableBeanFactory
    ): BeanFactoryInitializationAotContribution {
        return BeanFactoryInitializationAotContribution { generationContext, _ ->
            val hints = generationContext.runtimeHints
            val memberCategories = MemberCategory.entries.toTypedArray()

            hints.registerResourceHints()
            hints.registerReflectionHints(memberCategories = memberCategories)

            // User-defined EntityClass instances access the primary constructor of their associated Entity instance
            // via lazy reflection, which results in KotlinReflectionInternalErrors unless the Entity class is
            // registered as a hint. This iterates over the application's autoconfiguration base packages and
            // registers any detected Entity subclasses.
            AutoConfigurationPackages
                .get(beanFactory)
                .forEach { packageName ->
                    findSubClassesInPackage(Entity::class.java, packageName).forEach { subClass ->
                        hints.reflection().registerType(subClass, *memberCategories)
                    }
                }
        }
    }

    private fun RuntimeHints.registerResourceHints() {
        listOf(
            "META-INF/services/org.jetbrains.exposed.v1.dao.id.EntityIDFactory",
            "META-INF/services/org.jetbrains.exposed.v1.sql.DatabaseConnectionAutoRegistration",
            "META-INF/services/org.jetbrains.exposed.v1.core.statements.GlobalStatementInterceptor"
        ).forEach { resource ->
            resources().registerResource(ClassPathResource(resource))
        }
    }

    private fun RuntimeHints.registerReflectionHints(vararg memberCategories: MemberCategory) {
        listOf(
            Database::class,
            DatabaseConfig::class,
            TransactionManager::class,
            SpringTransactionManager::class,
            Transaction::class,
            TransactionStore::class,
            Table::class,
            DdlAware::class,
            Column::class,
            IColumnType::class,
            IDateColumnType::class,
            JsonColumnMarker::class,
            IntegerColumnType::class,
            EnumerationColumnType::class,
            EnumerationNameColumnType::class,
            CustomEnumerationColumnType::class,
            Expression::class,
            ExpressionWithColumnType::class,
            Op::class,
            Op.Companion::class,
            ForeignKeyConstraint::class,
            CheckConstraint::class,
            Index::class,
            PreparedStatementApi::class,
            Statement::class,
            QueryBuilder::class,
            SizedIterable::class,
            Entity::class,
            EntityClass::class,
            EntityID::class,
            java.util.Collections::class,
            kotlin.jvm.functions.Function0::class,
            kotlin.jvm.functions.Function1::class,
            kotlin.jvm.functions.Function2::class,
            kotlin.jvm.functions.Function3::class,
            kotlin.jvm.functions.Function4::class,
            kotlin.jvm.functions.Function5::class,
            kotlin.jvm.functions.Function6::class,
            kotlin.jvm.functions.Function7::class,
            kotlin.jvm.functions.Function8::class,
            kotlin.jvm.functions.Function9::class,
            kotlin.jvm.functions.Function10::class,
            kotlin.jvm.functions.Function11::class,
            kotlin.jvm.functions.Function12::class,
            kotlin.jvm.functions.Function13::class,
            kotlin.jvm.functions.Function14::class,
            kotlin.jvm.functions.Function15::class,
            kotlin.jvm.functions.Function16::class,
            kotlin.jvm.functions.Function17::class,
            kotlin.jvm.functions.Function18::class,
            kotlin.jvm.functions.Function19::class,
            kotlin.jvm.functions.Function20::class,
            kotlin.jvm.functions.Function21::class,
            kotlin.jvm.functions.Function22::class,
            kotlin.jvm.functions.FunctionN::class
        ).forEach { typeClass ->
            reflection().registerType(typeClass.java, *memberCategories)
        }
    }

    /**
     * Searches the provided package for component bean definitions that inherit from the specified [baseClass].
     *
     * @return A set of detected classes referenced by type abstraction as [TypeReference].
     */
    private fun findSubClassesInPackage(baseClass: Class<*>, packageName: String): Set<TypeReference> {
        val typeFilter = AssignableTypeFilter(baseClass)
        val classPathScanner = ClassPathScanningCandidateComponentProvider(false).apply { addIncludeFilter(typeFilter) }
        return classPathScanner
            .findCandidateComponents(packageName)
            .mapNotNull { component -> component.beanClassName?.let { TypeReference.of(it) } }
            .toSet()
    }
}
