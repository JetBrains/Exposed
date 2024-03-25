package org.jetbrains.exposed.spring

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.TransactionStore
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.core.io.ClassPathResource

/**
 * Class responsible for registering the contracts needed for reflection and resource loading at runtime.
 * Registering these runtime hints for Exposed classes and resources is required to run a Spring Boot application
 * as a GraalVM native image.
 *
 * To activate hints, this should be applied directly on a Spring configuration class using:
 * `@ImportRuntimeHints(ExposedHints::class)`.
 * Alternatively, these hints can be registered by adding an entry in `META-INF/spring/aot.factories` with a key
 * equal to the fully-qualified name of the `RuntimeHintsRegistrar` interface.
 */
@Suppress("SpreadOperator")
class ExposedHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
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
        )
            .forEach {
                hints
                    .reflection()
                    .registerType(it.java, *MemberCategory.entries.toTypedArray())
            }

        listOf(
            "META-INF/services/org.jetbrains.exposed.dao.id.EntityIDFactory",
            "META-INF/services/org.jetbrains.exposed.sql.DatabaseConnectionAutoRegistration",
            "META-INF/services/org.jetbrains.exposed.sql.statements.GlobalStatementInterceptor"
        )
            .forEach {
                hints
                    .resources()
                    .registerResource(ClassPathResource(it))
            }
    }
}
