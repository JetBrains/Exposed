package org.jetbrains.exposed.v1.spring.boot4.r2dbc

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.statements.Statement
import org.jetbrains.exposed.v1.core.statements.StatementBuilder
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.jetbrains.exposed.v1.core.statements.api.RowApi
import org.jetbrains.exposed.v1.core.transactions.TransactionStore
import org.jetbrains.exposed.v1.dao.r2dbc.Entity
import org.jetbrains.exposed.v1.dao.r2dbc.EntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.ExperimentalR2dbcDaoApi
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SizedIterable
import org.jetbrains.exposed.v1.r2dbc.mappers.TypeMapper
import org.jetbrains.exposed.v1.r2dbc.statements.SuspendExecutable
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring7.reactive.transaction.SpringReactiveTransactionManager
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
            @OptIn(ExperimentalR2dbcDaoApi::class)
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
            "META-INF/services/org.jetbrains.exposed.v1.core.statements.GlobalStatementInterceptor",
            "META-INF/services/org.jetbrains.exposed.v1.r2dbc.statements.GlobalSuspendStatementInterceptor",
            "META-INF/services/org.jetbrains.exposed.v1.r2dbc.mappers.TypeMapper",
        ).forEach { resource ->
            resources().registerResource(ClassPathResource(resource))
        }
    }

    @OptIn(ExperimentalR2dbcDaoApi::class)
    private fun RuntimeHints.registerReflectionHints(vararg memberCategories: MemberCategory) {
        listOf(
            DatabaseApi::class,
            R2dbcDatabase::class,
            DatabaseConfig::class,
            R2dbcDatabaseConfig::class,
            TransactionManager::class,
            SpringReactiveTransactionManager::class,
            Transaction::class,
            R2dbcTransaction::class,
            TransactionStore::class,
            Table::class,
            DdlAware::class,
            Column::class,
            IColumnType::class,
            IDateColumnType::class,
            JsonColumnMarker::class,
            IntegerColumnType::class,
            LongColumnType::class,
            FloatColumnType::class,
            DoubleColumnType::class,
            StringColumnType::class,
            EnumerationColumnType::class,
            EnumerationNameColumnType::class,
            CustomEnumerationColumnType::class,
            EntityIDColumnType::class,
            Expression::class,
            ExpressionWithColumnType::class,
            IExpressionAlias::class,
            ExpressionWithColumnTypeAlias::class,
            Op::class,
            Op.Companion::class,
            ForeignKeyConstraint::class,
            CheckConstraint::class,
            Index::class,
            PreparedStatementApi::class,
            StatementInterceptor::class,
            Statement::class,
            StatementBuilder::class,
            SuspendExecutable::class,
            QueryBuilder::class,
            Query::class,
            SizedIterable::class,
            ResultRow::class,
            RowApi::class,
            Entity::class,
            EntityClass::class,
            EntityID::class,
            TypeMapper::class,
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
