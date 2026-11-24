package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedTableRegistry
import org.jetbrains.exposed.v1.spring.data.multisource.first.FirstSourceConfiguration
import org.jetbrains.exposed.v1.spring.data.multisource.first.MultiSourceParents
import org.jetbrains.exposed.v1.spring.data.multisource.second.MultiSourceChildren
import org.jetbrains.exposed.v1.spring.data.multisource.second.SecondSourceConfiguration
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryConfigExtension
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactory
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactoryBean
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

class ExposedTableRegistryTest {
    private val scanned = ExposedTableRegistry.scan(listOf("org.jetbrains.exposed.v1.spring.data"), javaClass.classLoader)

    @Test
    fun testScanFindsTopLevelAndNestedTables() {
        listOf(Purchases, PurchaseLines, Customers, NestedParents, NestedChildren, SameNameA, SameNameB).forEach { table ->
            assertTrue(scanned.tables.any { it === table }) { "${table.javaClass.name} not found in ${scanned.tables.map { it.javaClass.name }}" }
        }
    }

    @Test
    fun testScanOnlyFindsTablesOfTheBasePackages() {
        val registry = ExposedTableRegistry.scan(listOf("org.jetbrains.exposed.v1.spring.data.nothing.here"), javaClass.classLoader)
        assertTrue(registry.tables.isEmpty()) { registry.tables.toString() }
    }

    @Test
    fun testIncomingListsTheReferencingForeignKeys() {
        val incoming = scanned.incoming(Purchases)
        assertEquals(3, incoming.size) { incoming.toString() }
        assertSame(PurchaseLines.purchase, incoming.single { it.fromTable === PurchaseLines }.from.single())
        assertSame(PurchaseNotes.purchase, incoming.single { it.fromTable === PurchaseNotes }.from.single())
        assertSame(PurchaseLabels.purchase, incoming.single { it.fromTable === PurchaseLabels }.from.single())
    }

    @Test
    fun testIncomingFindsNestedTables() {
        val incoming = scanned.incoming(NestedParents)
        assertSame(NestedChildren.parent, incoming.single().from.single())
    }

    @Test
    fun testIncomingComparesTablesByIdentity() {
        val registry = ExposedTableRegistry(listOf(SameNameA, SameNameB, SameNameAChildren, SameNameBChildren))
        assertSame(SameNameAChildren.parent, registry.incoming(SameNameA).single().from.single())
        assertSame(SameNameBChildren.parent, registry.incoming(SameNameB).single().from.single())
    }

    @Test
    fun testIncomingIgnoresMultiColumnForeignKeys() {
        val registry = ExposedTableRegistry(listOf(CompositeParents, CompositeChildren))
        assertTrue(registry.incoming(CompositeParents).isEmpty()) { registry.incoming(CompositeParents).toString() }
    }

    @Test
    fun testScanFindsPrivateTableObjects() {
        assertTrue(scanned.tables.any { it === PrivateTables }) { scanned.tables.map { it.javaClass.name }.toString() }
    }

    @Test
    fun testScanReportsFailingTableInitializers() {
        val error = assertThrows<IllegalStateException> {
            ExposedTableRegistry.scan(listOf("org.jetbrains.exposed.v1.spring.registryfixture.failing"), javaClass.classLoader)
        }
        assertTrue(error.message!!.contains("org.jetbrains.exposed.v1.spring.registryfixture.failing.FailingTables")) { error.message }
    }

    @Test
    fun testIncomingOfUnknownTableIsEmpty() {
        assertTrue(ExposedTableRegistry.EMPTY.incoming(Purchases).isEmpty())
        assertTrue(ExposedTableRegistry(listOf(PurchaseLines)).incoming(Customers).isEmpty())
    }

    @Test
    fun testFactoryDefaultsToEmptyRegistry() {
        assertSame(ExposedTableRegistry.EMPTY, ExposedRepositoryFactory().tables)
        val registry = ExposedTableRegistry(listOf(Purchases))
        assertSame(registry, ExposedRepositoryFactory(tables = registry).tables)
    }

    private object PrivateTables : LongIdTable("registry_private")

    object NestedParents : LongIdTable("registry_nested_parents")

    object NestedChildren : LongIdTable("registry_nested_children") {
        val parent = reference("parent_id", NestedParents)
    }

    object SameNameA : LongIdTable("registry_same_name")

    object SameNameB : LongIdTable("registry_same_name")

    object SameNameAChildren : LongIdTable("registry_same_name_a_children") {
        val parent = reference("parent_id", SameNameA)
    }

    object SameNameBChildren : LongIdTable("registry_same_name_b_children") {
        val parent = reference("parent_id", SameNameB)
    }

    object CompositeParents : Table("registry_composite_parents") {
        val a = integer("a")
        val b = integer("b")
        override val primaryKey = PrimaryKey(a, b)
    }

    object CompositeChildren : Table("registry_composite_children") {
        val a = integer("a")
        val b = integer("b")

        init {
            foreignKey(a to CompositeParents.a, b to CompositeParents.b)
        }
    }
}

/** The registry bean of the shared test context, scanned from the `@EnableExposedRepositories` base packages. */
@SpringJUnitConfig(SpringDataExposedTestConfiguration::class)
class ExposedTableRegistryBeanTest {
    @Autowired
    lateinit var context: ApplicationContext

    @Test
    fun testRegistryBeanIsScannedFromBasePackages() {
        val registry = context.getBean(ExposedRepositoryConfigExtension.TABLE_REGISTRY_BEAN_NAME, ExposedTableRegistry::class.java)
        assertEquals(3, registry.incoming(Purchases).size)
    }

    @Test
    fun testFactoryBeanUsesRegistryBean() {
        val registry = context.getBean(ExposedTableRegistry::class.java)
        val factoryBean = context.getBean("&staffRepository") as ExposedRepositoryFactoryBean<*, *, *>
        assertSame(registry, factoryBean.tables)
    }
}

/** Two `@EnableExposedRepositories` sources in one context share one registry that holds the tables of both base packages. */
@SpringJUnitConfig(ExposedTableRegistryMultiSourceTest.Beans::class, FirstSourceConfiguration::class, SecondSourceConfiguration::class)
class ExposedTableRegistryMultiSourceTest {
    @Autowired
    lateinit var context: ApplicationContext

    @Test
    fun testRegistryHoldsTablesOfAllSources() {
        val registry = context.getBean(ExposedTableRegistry::class.java)
        assertTrue(registry.tables.any { it === MultiSourceParents }) { registry.toString() }
        assertTrue(registry.tables.any { it === MultiSourceChildren }) { registry.toString() }
        assertSame(MultiSourceChildren.parent, registry.incoming(MultiSourceParents).single().from.single())
    }

    @Test
    fun testAllFactoryBeansShareTheRegistry() {
        val registry = context.getBean(ExposedTableRegistry::class.java)
        val factoryBeans = context.getBeansOfType(ExposedRepositoryFactoryBean::class.java).values
        assertEquals(2, factoryBeans.size) { factoryBeans.toString() }
        factoryBeans.forEach { assertSame(registry, it.tables) }
    }

    @Configuration
    open class Beans {
        @Bean
        open fun dataSource(): DataSource = EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build()

        @Bean
        open fun transactionManager(dataSource: DataSource): PlatformTransactionManager = SpringTransactionManager(dataSource)
    }
}

/** A registry bean defined by the application is kept and used by the repositories. */
@SpringJUnitConfig(ExposedTableRegistryCustomBeanTest.CustomRegistryConfiguration::class)
class ExposedTableRegistryCustomBeanTest {
    @Autowired
    lateinit var context: ApplicationContext

    @Test
    fun testCustomRegistryBeanIsKept() {
        val registry = context.getBean(ExposedRepositoryConfigExtension.TABLE_REGISTRY_BEAN_NAME, ExposedTableRegistry::class.java)
        assertSame(CUSTOM, registry)
        val factoryBean = context.getBeansOfType(ExposedRepositoryFactoryBean::class.java).values.single()
        assertSame(CUSTOM, factoryBean.tables)
    }

    @Configuration
    @EnableExposedRepositories(
        basePackageClasses = [ExposedTableRegistryCustomBeanTest::class],
        considerNestedRepositories = true,
        includeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [CustomRegistryRepository::class])],
    )
    open class CustomRegistryConfiguration : ExposedTableRegistryMultiSourceTest.Beans() {
        @Bean(ExposedRepositoryConfigExtension.TABLE_REGISTRY_BEAN_NAME)
        internal open fun exposedTableRegistry(): ExposedTableRegistry = CUSTOM
    }

    @ExposedRepository(table = Customers::class)
    interface CustomRegistryRepository : ExposedCrudRepository<Customer, Long>

    companion object {
        internal val CUSTOM = ExposedTableRegistry(listOf(Customers))
    }
}
