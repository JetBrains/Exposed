# How a Spring Data Integration Works — and What an Exposed Store Module Needs

> Explanation only, no design decisions and no code. Goal: understand what is needed so that this works:
>
> ```kotlin
> object Customers : LongIdTable("customers") {
>     val name = varchar("name", 255)
>     val email = varchar("email", 255).uniqueIndex()
>     val createdAt = timestamp("created_at")
> }
>
> data class Customer(
>     val id: Long? = null,
>     val name: String,
>     val email: String,
>     val createdAt: Instant
> )
>
> @ExposedRepository(table = Customers::class) // may be dropped with runtime generation, or kept with KSP
> interface CustomerRepository : ExposedCrudRepository<Customer, Long> {
>     fun findByEmail(email: String): Customer?
>     fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Customer>
>     fun countByCreatedAtAfter(t: Instant): Long
> }
> ```
>
> Custom queries (`@Query`) are out of scope. Background on Spring Data JDBC internals:
> [`SPRING_DATA_JDBC_RESEARCH.md`](SPRING_DATA_JDBC_RESEARCH.md).

---

## 1. Short version

Spring Data has two parts:

- **`spring-data-commons`** is not tied to any store. It turns an interface into a proxy, parses method names like
  `findByEmail`, adapts return types (`Optional`, `List`, `Page`, nullable) and handles Spring wiring. You reuse all of it.
- **A store module**, like Spring Data JDBC, plugs into a few extension points. It supplies a base class for the CRUD
  methods (`save`, `findById`, ...) and a class that runs the parsed queries.

You only write the store module. Compared to the first idea (`T` = the table), `T` is now an **entity** (`Customer`).
That makes `ListCrudRepository` fit, but adds three things the store module must provide:

1. a **link** from `Customer` to `Customers`,
2. a **two-way mapping**: `ResultRow → Customer` for reads, and `Customer → insert/update` for writes,
3. an **"is new?" check** for `save`.

Most of the research doc is still not needed: no mapping context for nested aggregates, no `DbAction`,
no `WritingContext`, no single-query loading. The `Table` already defines the schema, and Exposed already has the SQL
building blocks (`Op`, `Query`) and the dialects. One data class ↔ one table, plus nested objects from other tables
(references and to-many rows, see §6 and §7), which are loaded with separate batched queries instead of joins.

The `exposed-spring-data` module currently only has a `build.gradle.kts`. It already depends on `exposed-jdbc` and
`spring7-transaction`. You still need to add `spring-data-commons` 4.x, which matches Spring 7 / Boot 4.

---

## 2. The two modalities

Each piece in §4 can be built in one of two ways. Both still use the commons proxy and bootstrapping.

| | **Runtime** (reflection) | **KSP** (compile time) |
|---|---|---|
| Table link | Annotation, read by reflection, or inferred some other way (see §4.2). The annotation *can* be dropped. | The annotation is the processor's input, so it is **kept**. |
| `ResultRow ↔ Customer` mapping | Built at startup by matching constructor parameters / properties to column property names. | Generated as plain functions, e.g. `ResultRow.toCustomer()` and `Customer.writeTo(statement)`. |
| Derived queries | Parsed at startup with commons' `PartTree`, run through a query creator. | Either still parsed at runtime (KSP only generates the mapper), or translated to Exposed DSL code at build time. |
| Errors (unknown property, type mismatch) | At application startup. | At compile time. |
| Dependencies | `kotlin-reflect` (commons already uses it for Kotlin). | A KSP processor module, plus the KSP Gradle plugin in user projects. |
| Similar to | Spring Data JDBC / JPA. | Spring Data AOT repositories (generated query methods). |

A common middle ground: **runtime query derivation + KSP-generated mappers**. That avoids reflection on the hot path
(row mapping) without having to reimplement method-name parsing at compile time.

---

## 3. How it works at runtime

```
@EnableExposedRepositories (or Boot auto-config)
 → scans for interfaces extending ExposedCrudRepository
 → registers one table registry: the Table objects in the same base packages (§7.1)
 → registers one ExposedRepositoryFactoryBean per interface
   → ExposedRepositoryFactory (RepositoryFactorySupport) builds a proxy with:
      - QueryExecutorMethodInterceptor           → derived methods (findByEmail, countByCreatedAtAfter)
      - ImplementationMethodExecutionInterceptor → CRUD methods (save, findById) → SimpleExposedRepository
                                                   or a fragment / generated *Impl class (KSP)
      - TransactionInterceptor                   → Exposed transaction via SpringTransactionManager
```

### At startup

Every interface method is checked once:

- If the base class or a custom fragment implements it, calls go there.
- Otherwise your `QueryLookupStrategy` turns it into a `RepositoryQuery` built from a `PartTree`. `PartTree` resolves
  property names against the **domain type**, i.e. `Customer`, not `Customers`. A method name that doesn't match a
  `Customer` property fails here, at startup.

### At call time

`RepositoryQuery.execute(args)` runs, maps rows to `Customer`, and commons converts the result to the declared return
type (`Customer?`, `List<Customer>`, `Page<Customer>`, `Long`).

---

## 4. What you need to implement

Each item says what it is, which Spring Data type it builds on, and how it differs between the two modalities.

### 4.1 `ExposedCrudRepository<T, ID>`

- `@NoRepositoryBean`, extends `ListCrudRepository<T, ID>` and `ListPagingAndSortingRepository<T, ID>`.
  (Since Spring Data 3, the paging interface no longer extends the CRUD one, so you list both.)
- This gives `save`, `saveAll`, `findById`, `existsById`, `findAll`, `findAllById`, `count`, `deleteById`, `delete`,
  `deleteAllById`, `deleteAll`, `findAll(Sort)` and `findAll(Pageable)`.
- `findById` returns `Optional<T>`. Kotlin users can call commons' `findByIdOrNull` extension.
- Commons reads `T` and `ID` from the generics: `Customer` and `Long`.

| Runtime | KSP |
|---|---|
| Identical. | Identical. The processor can also check that `ID` matches the table's id type (`LongIdTable` → `Long`). |

### 4.2 Linking the entity to the table

The repository generics only name `Customer`, so the store module has to find `Customers` some other way.
Possible sources:

- **Annotation on the repository**: `@ExposedRepository(table = Customers::class)`, read in the factory from
  `RepositoryMetadata.repositoryInterface`. Get the instance with `Customers::class.objectInstance`.
- **Third type parameter**: `ExposedCrudRepository<Customer, Long, Customers>`. No annotation needed.
- **Registry / convention**: a bean that maps `Customer::class → Customers`, or a naming rule. Implicit, so harder to
  debug.

| Runtime | KSP |
|---|---|
| Any of the three. This is how "the annotation may be dropped" works. | The annotation (or the third type parameter) is needed, because the processor must see the table symbol. |

Note: the annotation and the old base-interface idea used the same name `ExposedRepository`. Only one of them can keep
it.

### 4.3 Row mapping (`ResultRow ↔ Customer`)

Needed by both the CRUD base class and the derived queries.

- **Read**: build a `Customer` from a `ResultRow`. For each constructor parameter, take the column whose Kotlin property
  has the same name (`email` → `Customers.email`). Unwrap the id: `row[Customers.id].value`.
- **Write**: set each column from the matching entity property in an `insert` / `update` statement. Skip `id` on
  insert so the database generates it.
- **After insert**: return a copy with the generated id. `Customer` is immutable, so this means `copy(id = ...)`.
- **Validate once per table**: every constructor parameter has a column, and their types are compatible.

| Runtime | KSP |
|---|---|
| Use `kotlin-reflect` (`primaryConstructor.callBy`, `copy`), or reuse commons' Kotlin support: `PreferredConstructorDiscoverer`, `KotlinClassGeneratingEntityInstantiator`, `KotlinCopyMethod` (see §2.5 of the research doc). Cache the property-to-column list per table. Mismatches fail at startup. | Generate `fun ResultRow.toCustomer(): Customer`, a function that fills an `UpdateBuilder` from a `Customer`, and the property-to-column list. Mismatches fail at compile time. No reflection on the hot path. |

### 4.4 Entity information

`RepositoryFactorySupport.getEntityInformation(...)` must return an `EntityInformation<Customer, Long>`. In Spring Data 4
the hook takes `RepositoryMetadata`; check the exact signature in the version you add. It provides:

- `getJavaType()` → `Customer`, `getIdType()` → `Long`,
- `getId(entity)` → `entity.id`,
- `isNew(entity)` → `id == null` (same rule as commons' `PersistentEntityIsNewStrategy`, research doc §2.4).

| Runtime | KSP |
|---|---|
| Find the id property by name (`id`) or an `@Id` annotation, read it by reflection. | Generate an `EntityInformation` (or just `getId` / `isNew` functions) for `Customer`. |

### 4.5 `SimpleExposedRepository<T, ID>` (the CRUD base class)

Implements every method from §4.1 with the Exposed DSL, using the table from §4.2 and the mapper from §4.3:

- `save`: if `isNew` → `insertAndGetId`, return `copy(id = ...)`; otherwise `update { where id eq ... }`.
  Decide what an update with 0 affected rows means (Spring Data JDBC throws `IncorrectUpdateSemanticsDataAccessException`).
- `findById` → `selectAll().where { table.id eq id }.singleOrNull()?.toEntity()`.
- `findAll(Sort)` / `findAll(Pageable)` → `orderBy`, `limit` / `offset`, plus a `count()` for the `Page` total.
- `deleteById` / `deleteAll` → `deleteWhere`.
- Mark it `@Transactional(readOnly = true)` and put `@Transactional` on write methods, as `SimpleJdbcRepository` does.

| Runtime | KSP |
|---|---|
| One generic class, created per repository by the factory with the table and a reflective mapper. | Still one generic class, but given the generated mapper. Or generate a `CustomerRepositoryImpl` per repository; commons picks it up as a fragment through the `Impl` postfix naming convention. |

### 4.6 `ExposedRepositoryFactory : RepositoryFactorySupport`

Override:

- `getEntityInformation` (§4.4),
- `getTargetRepository`: resolve the table (§4.2) and the mapper (§4.3), then create the base class,
- `getRepositoryBaseClass` → `SimpleExposedRepository`,
- `getQueryLookupStrategy` (§4.8).

| Runtime | KSP |
|---|---|
| Builds mappers by reflection, caches them per table. | Looks up the generated mapper, e.g. by a naming convention (`CustomerExposedMapper`) or a generated registry. |

### 4.7 `ExposedRepositoryFactoryBean : TransactionalRepositoryFactoryBeanSupport`

Creates the factory and gets the transaction manager injected. This base class adds the transaction interceptor for you.
Same in both modalities.

### 4.8 Derived queries (most of the work)

- **`ExposedQueryLookupStrategy`**: returns a `PartTreeExposedQuery` for each method.
- **`PartTreeExposedQuery : RepositoryQuery`**: holds `PartTree(method.name, Customer::class.java)`.
- **Property → column**: `PartTree` and `Sort` give `Customer` property names (`email`, `name`, `createdAt`). Map them to
  `Customers` columns with the same list as §4.3. You can't use `Column.name`, because that is the SQL name
  (`"created_at"`), not the Kotlin name.
- **`ExposedQueryCreator : AbstractQueryCreator<Query, Op<Boolean>>`**:
  - `create(part)` maps `part.type` to an Exposed operator (see the table below),
  - `and` / `or` combine conditions with Exposed's `and` / `or`,
  - `complete(op, sort)` returns `selectAll().where(op).orderBy(...)`.
- **Parameters**: read them with `ParametersParameterAccessor`. It also gives you the `Pageable` / `Sort` argument.
- **Result**: map every row with §4.3 before returning.

#### `Part.Type` → Exposed operator

| `Part.Type` | Exposed |
|---|---|
| `SIMPLE_PROPERTY` | `eq` |
| `NEGATING_SIMPLE_PROPERTY` | `neq` |
| `GREATER_THAN` / `AFTER` | `greater` |
| `LESS_THAN` / `BEFORE` | `less` |
| `GREATER_THAN_EQUAL` / `LESS_THAN_EQUAL` | `greaterEq` / `lessEq` |
| `LIKE` / `CONTAINING` / `STARTING_WITH` / `ENDING_WITH` | `like`, with `%` added as needed and `%` / `_` in the argument escaped |
| `IN` / `NOT_IN` | `inList` / `notInList` |
| `BETWEEN` | `between` |
| `IS_NULL` / `IS_NOT_NULL` | `isNull()` / `isNotNull()` |
| `TRUE` / `FALSE` | `eq true` / `eq false` |
| `IS_EMPTY` / `IS_NOT_EMPTY` | only on a to-many path (§7.5): `parentKey NOT IN (SELECT childKey ... WHERE childKey IS NOT NULL)` / `parentKey IN (SELECT childKey ...)`. Rejected on other properties. |
| `IgnoreCase` (`part.shouldIgnoreCase()`) | `lowerCase()` on the column, lowercase the argument |

#### Execution by subject (method prefix)

| Method name | Execution |
|---|---|
| `find` / `get` / `read` / `query` | a list, or `singleOrNull()` for a single result, based on `QueryMethod.isCollectionQuery`. More than one row for a single result → `IncorrectResultSizeDataAccessException`. |
| `count` | `.count()` |
| `exists` | `!empty()` |
| `delete` / `remove` | `deleteWhere` on the repository's table only. Referencing rows follow their foreign key's `onDelete` rule (§7.4). |
| `First` / `Top` | `limit` |
| `Distinct` | `withDistinct()` |
| `Sort` / `Pageable` parameter | `orderBy`, `limit` and `offset`, plus a count query when the method returns `Page` (commons' `PageableExecutionUtils.getPage` skips it when possible) |

#### The example methods

| Method | Becomes |
|---|---|
| `findByEmail(email): Customer?` | `selectAll().where { Customers.email eq email }.singleOrNull()?.toCustomer()`; commons returns `null` for "not found" because the Kotlin return type is nullable. |
| `findByNameContainingIgnoreCase(name, pageable): Page<Customer>` | `where { Customers.name.lowerCase() like "%${name.lowercase()}%" }` + `orderBy` from `pageable.sort` + `limit`/`offset`, plus a `count()` with the same `where` for the total. |
| `countByCreatedAtAfter(t): Long` | `selectAll().where { Customers.createdAt greater t }.count()` |

| Runtime | KSP |
|---|---|
| Everything above happens once per method at startup. Uses commons' `PartTree`, which needs the real `Customer` `Class` object. | `PartTree` can't run in a processor, because KSP only sees symbols, not loaded classes. Either keep this part at runtime (only mappers are generated), or write your own method-name parser and generate the DSL code for each method, e.g. into `CustomerRepositoryImpl`. `Pageable` / `Sort` property names are only known at runtime, so the sort-to-column lookup is needed at runtime either way. |

### 4.9 Bootstrapping

- `@EnableExposedRepositories`, which does `@Import(ExposedRepositoriesRegistrar::class)`.
- `ExposedRepositoriesRegistrar : RepositoryBeanDefinitionRegistrarSupport`.
- `ExposedRepositoryConfigExtension : RepositoryConfigurationExtensionSupport`, which provides:
  - the module prefix (`"exposed"`),
  - the factory bean class name,
  - `getIdentifyingTypes()` returning `ExposedCrudRepository`. This matters when several Spring Data modules are on the
    classpath ("strict" mode): `Customer` has no store annotation, so the base interface is what marks it as Exposed's.
  - `registerBeansForRoot`, which registers the shared mapping context and the table registry bean `exposedTableRegistry`
    (§7.1).

Same in both modalities. With KSP, the generated `*Impl` classes must be visible to Spring: the `Impl` postfix lookup
scans the same base packages as the repositories.

### 4.10 Optional Boot auto-config

Follow `exposed-spring-boot4-starter/.../ExposedAutoConfiguration.kt` and its `AutoConfiguration.imports` file. Same in
both modalities.

### 4.11 KSP only: the processor and its build setup

- A separate processor module (`SymbolProcessorProvider` / `SymbolProcessor`) that finds `@ExposedRepository`
  interfaces, reads the table and entity symbols, and writes the mappers (and optionally the `*Impl` classes) with
  KotlinPoet or plain text.
- User projects need the KSP Gradle plugin and a `ksp(...)` dependency on the processor.

---

## 5. Remaining open decisions

- **Runtime, KSP, or the middle ground** (§2).
- **How the table is linked** (§4.2): annotation, third type parameter, or registry.
- **Update semantics** of `save` for a row that doesn't exist (§4.5).

---

## 6. Pitfalls

- **No transaction**: every DSL call needs an active transaction. Without the interceptor you get
  "No transaction in context".
- **Id type**: `Customers.id` is a `Column<EntityID<Long>>`, while `Customer.id` is `Long?`. Unwrap with `.value` on
  read; check that raw `Long` works with `eq`, `inList` and `between`, or wrap it with `EntityID(id, Customers)`.
- **Generated id after insert**: `save` must return the copy with the new id. Callers who ignore the return value keep
  an entity with `id = null`.
- **`Instant` types**: `timestamp()` from `exposed-java-time` uses `java.time.Instant`; `exposed-kotlin-datetime` uses a
  different `Instant`. The entity property must use the same type as the column.
- **Names that don't line up**: a `Customer` property with no column, or a column with no property, must be reported
  at startup (runtime) or at compile time (KSP), not when the first query runs.
- **`like` escaping**: `Containing` with a user input like `"50%"` must escape `%` and `_`.
- **Column names**: a property maps to the column with the same Kotlin name, or to the one named by
  `@ExposedColumn("billingCustomer")`, on entities, DTOs, interface projections and nested types (also plain columns).
- **Nested objects** (`customer: Customer`, `items: List<OrderItem>`):
  - a property of a class type in a foreign key column is a **to-one reference**: it is loaded if the projection
    declares it as an object (`customer: Customer`, `customer: CustomerName`, `customer: CustomerView`), and not loaded
    if it declares the key (`customer: Long`) or leaves it out. Saving cascades: the referenced entity is saved through
    its own repository (inserted if it is new by the usual `isNew` rules, `Persistable` included, otherwise updated).
  - a collection property (`items: List<OrderItem>`) is a **to-many relation**. It is inferred from the foreign keys,
    or set with `@ExposedReferencedBy` (§7). A one-to-many relation is owned by the root: the rows are loaded, and saving
    updates rows matched by their primary key, inserts new ones and deletes the missing ones (no delete and re-insert).
    Tables without a mapped primary key are matched by their values.
  - a **many-to-many** relation goes through a join table: the referenced rows are loaded, but neither they nor the
    join table rows are ever written.
  - deleting the root deletes only the root row. The database deletes or keeps the child rows by the `onDelete` rule (§7.4).
  - Everything is loaded eagerly with one batched query per relation and level (`IN` lists in chunks of 1000), after
    the root query, so `DISTINCT`, count, paging and scrolling are unaffected. There are no proxies.
- **Nested paths** (`findByItemsName`, `findByCustomerName`, `findByItemsProductName`): each segment before the last is
  a hop to another table: to-many for to-many relations (inferred or annotated, §7; for a join table a second hop goes on
  to the referenced table), to-one for foreign key columns. The target
  tables don't need an id. Parts become `parentKey IN (SELECT childKey FROM child WHERE ...)`, so the query stays on the
  repository's table, rows are never duplicated, and count / exists / delete / paging are unchanged. `customer.id` uses the
  foreign key column directly, and `findByCustomer(customer)` compares its key. Invalid paths fail at startup.
  Semantics to keep in mind:
  - `And` parts on the same path share one subquery: `findByItemsNameAndItemsQuantity` matches orders with one item that
    has both. `Or` parts have their own subqueries.
  - negations are per child: `findByItemsNameNot("x")` means "has an item not named x", not "has no item named x".
  - `IsEmpty` on a nested path applies to each row of the prefix: `findByOrdersItemsIsEmpty` means "has an order
    without items", not "has no items in any order".
- **Sorting by nested paths**: only to-one paths (`Sort.by("customer.name")`, `OrderByCustomerNameAsc`), with a
  `LEFT JOIN` under an alias, also for paging and keyset scrolling. To-many paths (`items.name`) are rejected, they are
  ambiguous.
- **Joins to tables the domain model has no property for** are left to `query { }`.
- **Page count query**: `Page` needs a second query. Use `Slice` or `List` when the total isn't needed.

---

## 7. To-many relations

A to-many relation is a collection property whose elements are rows of another table. Most relations need no annotation:
the repository infers them from the foreign keys of the known tables. `@ExposedReferencedBy` only overrides a relation or
resolves an ambiguous one.

[Spring Data Exposed: relations, saving, deleting and derived queries](SPRING_DATA_EXPOSED_SEMANTICS.md) has the
flowcharts, the exact error messages, the captured SQL and one example for each use case. `SemanticsDocumentExamplesTest`
asserts the SQL and messages of that document.

### 7.1 Known tables

The known tables are the `Table` objects in the base packages of `@EnableExposedRepositories` (or of the Boot
auto-configuration), including their sub-packages:

- Top-level, nested and companion objects are found, of any visibility. Table classes that aren't objects are skipped.
- The packages are scanned once per application context. With several `@EnableExposedRepositories`, the registry holds
  the tables of all their base packages.
- A table outside these packages isn't known. Annotate the property that uses it with `@ExposedReferencedBy`.
- Tables are compared by identity, not by name.

The scan uses classpath scanning at startup. The table list isn't generated ahead of time, so native images aren't
covered.

### 7.2 Inference rules

The repository infers a relation for a persistent property when all of these are true:

- The type is a `List`, `Set`, `Collection` or `Iterable` of a class or interface `E` (not a simple type such as
  `String`).
- The table of the owning type has no column for the property (a JSON list column stays a column).
- The property has no `@ExposedReferencedBy`.

The candidates come from the single-column foreign keys of known tables that reference the owning table:

| Candidate | Condition | Result |
|---|---|---|
| Owned (one-to-many) | Table `T` has a foreign key to the owner, and every persistent stored property of `E` maps to a column in `T`. | The rows of `T` are the elements. |
| Join (many-to-many) | Table `T` has a foreign key to the owner and another single-column foreign key. The target table has columns for every persistent stored property of `E`. | The rows referenced by the other foreign key (the `target`) are the elements. |

For a class, only properties with backing fields count as stored. All interface properties count. The matcher skips to-many collections, `Map` properties and `@Transient` properties. Each stored property is matched to a column by its Kotlin property name in the table object, or by the Kotlin property name given in `@ExposedColumn` when present.

Multi-column foreign keys are ignored. Exactly one candidate gives the relation. No candidate or more than one candidate
makes repository creation fail. There are no naming heuristics: a foreign key or table is never chosen by its name. Only
the element property matching above uses names, and anything that still fits more than one way is an error.

A self reference works the same way. `ShopEmployees.manager` references `ShopEmployees`, so
`reports: List<ShopEmployee>` is an owned relation.

### 7.3 Errors and fixes

| Situation | Message (shortened) | Fix |
|---|---|---|
| No candidate | `Shop.orders is not mapped: no known table has a foreign key to shops whose rows, or the rows they reference, have a column for every property of ShopOrder. Annotate 'orders' with @ExposedReferencedBy(<Table>::class), or make sure the table is in a scanned package` | Move the table into a scanned package, or annotate the property. |
| Two foreign keys from one table | `Account.outgoing is ambiguous, these relations to accounts fit: Transfers.from (one-to-many), Transfers.to (one-to-many). Annotate it with @ExposedReferencedBy(Transfers::class, column = "...") to choose one (set the column)` | Set `column`. |
| One join table, two targets | `... is ambiguous ... (many-to-many to tags) ... (set the target)` | Set `target`. |
| Candidates in several tables | `... Annotate it with @ExposedReferencedBy(A::class or B::class) ... (set the table)` | Set the table. |

All relations of an aggregate are resolved when its repository is created. An ambiguous relation deep inside the
aggregate (`Bank → accounts → outgoing`) fails there, and the message names the property (`BankAccount.outgoing`), not the
query path that uses it.

The annotation:

```kotlin
data class Account(
    val id: Long? = null,
    @ExposedReferencedBy(Transfers::class, column = "from") val outgoing: List<Transfer> = emptyList(),
    @ExposedReferencedBy(Transfers::class, column = "to") val incoming: List<Transfer> = emptyList(),
)
```

`column` and `target` are the Kotlin property names of the columns (`"from"`, not `"from_id"`). Without `target` the
relation is owned, with `target` it is a join relation. An annotated property never uses inference.

### 7.4 Saving and deleting

- `save` first saves the to-one references (upward), then writes the root row, then syncs each owned collection:
  insert new rows, update changed rows, delete rows that are no longer in the collection (orphan removal).
- Join relations are read-only. `save` writes neither the join table nor the referenced rows.
- `deleteById`, `delete`, `deleteAllById`, `deleteAll` and derived `deleteBy` delete only rows of the repository's table.
  The application doesn't delete child rows.
- Orphan removal deletes only the removed row. Its own children follow their foreign key rule too.

The database applies the `onDelete` rule of each foreign key that references a deleted row:

| `onDelete` | Result |
|---|---|
| `ReferenceOption.CASCADE` | The child rows are deleted, and their children by their own rules. |
| `ReferenceOption.SET_NULL` | The foreign key of the child rows is set to `NULL`. The column must be nullable. |
| `ReferenceOption.RESTRICT` | The delete fails with a constraint violation, and no row is deleted. |
| No rule | The database default applies. Most databases reject the delete, like `RESTRICT`. |

To delete an aggregate with its owned rows, declare the child foreign keys with `onDelete = ReferenceOption.CASCADE`:

```kotlin
object ShopOrders : LongIdTable("shop_orders") {
    val shop = reference("shop_id", Shops, onDelete = ReferenceOption.CASCADE)
    val number = varchar("number", 255)
}
```

### 7.5 Derived queries

- Each to-many segment of a path uses the same relation as reading and saving, at any depth:
  `findByOrdersItemsArticle` goes `Shop → orders → items`.
- `And` parts with the same prefix share one subquery per hop, so they match the same row.
- `IsEmpty` and `IsNotEmpty` are supported when the path ends on a to-many property (`findByOrdersIsEmpty`,
  `findByOrdersItemsIsNotEmpty`). On any other property, repository creation fails with a message that names `IS_EMPTY`
  or `IS_NOT_EMPTY` and the path.
- `IsEmpty` excludes `NULL` foreign keys from its subquery, so a nullable self reference works.
- Count comparisons ("more than two orders") aren't supported. Use `query { }` with an `Op`.

```sql
-- findByOrdersIsEmpty
SELECT SHOPS.ID, SHOPS."name" FROM SHOPS
WHERE SHOPS.ID NOT IN (SELECT SHOP_ORDERS.SHOP_ID FROM SHOP_ORDERS WHERE SHOP_ORDERS.SHOP_ID IS NOT NULL)

-- findByOrdersItemsArticleAndOrdersItemsQuantity
SELECT SHOPS.ID, SHOPS."name" FROM SHOPS
WHERE SHOPS.ID IN (SELECT SHOP_ORDERS.SHOP_ID FROM SHOP_ORDERS
  WHERE SHOP_ORDERS.ID IN (SELECT SHOP_ORDER_ITEMS.ORDER_ID FROM SHOP_ORDER_ITEMS
    WHERE (SHOP_ORDER_ITEMS.ARTICLE = ?) AND (SHOP_ORDER_ITEMS.QUANTITY = ?)))
```

---

## 8. Out of scope / not needed

- `@Query`
- AOT repositories (KSP covers a similar idea)
- R2DBC / coroutine repositories (Spring Data JDBC doesn't support coroutines either)
- Lazy loading and proxies

---

## 9. See also

- [`SPRING_DATA_JDBC_RESEARCH.md`](SPRING_DATA_JDBC_RESEARCH.md) — how Spring Data JDBC works under the hood.
