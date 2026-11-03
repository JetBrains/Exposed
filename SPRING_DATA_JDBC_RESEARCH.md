# How Spring Data JDBC Works Under the Hood — Research Notes for an Exposed Equivalent

> Research only, no design. Sources: shallow clones of `spring-data-relational` and `spring-data-commons`
> (current `main`, ~4.x / 2025.1 release train), the Spring Data JDBC reference docs, the Spring blog
> (Single Query Loading, AOT repositories), Jens Schauder's talks and community articles.

---

## 1. The big picture: four layers

Spring Data JDBC is four stacked layers. Only the bottom one touches JDBC.

| Layer | Module / key classes | Job |
|---|---|---|
| **Repository infrastructure** | `spring-data-commons`: `RepositoryFactorySupport`, `QueryExecutorMethodInterceptor`, `PartTree`; JDBC: `JdbcRepositoryFactory`, `SimpleJdbcRepository`, `JdbcQueryLookupStrategy` | Turns an interface into a proxy. Sends each method to a CRUD base class, a custom fragment, or a generated `RepositoryQuery`. |
| **Aggregate template** | `JdbcAggregateTemplate` | Implements save, insert, update, upsert, delete and find for whole aggregates. Publishes events and runs callbacks. |
| **Conversion / change model** | `spring-data-relational`: `RelationalMappingContext`, `WritingContext`, `DbAction`, `AggregateChange`, `MappingRelationalConverter` | Store-independent. Splits an object tree into a list of row operations, and folds rows back into objects. |
| **Data access** | `DataAccessStrategy` / `DefaultDataAccessStrategy`, `SqlGenerator`, `InsertStrategy`, `NamedParameterJdbcOperations` | Creates SQL text from metadata (through an SQL AST plus a `Dialect`) and runs it with Spring's `JdbcTemplate`. |

### Design rules (DDD, enforced on purpose)

From the docs and Schauder's talks:

- **One repository per aggregate root.** Everything reachable from the root belongs to the aggregate. It is loaded and saved as one unit.
- **No lazy loading, no caching, no dirty tracking, no session / first-level cache.** `findById` returns a plain, fully loaded object graph.
- **Only one-to-one and one-to-many relations inside an aggregate.** Many-to-one and many-to-many cross aggregate borders, so they must be modeled with ids (`AggregateReference<T, ID>`).
- **Updates of child entities are delete-all-then-reinsert,** because there is no tracking that could tell what changed.

---

## 2. Mapping metadata (the base for everything else)

### 2.1 Lazy introspection

**`RelationalMappingContext`** (extends commons' `AbstractMappingContext`) inspects entity classes on first use and caches a
`RelationalPersistentEntity` per type. Each property becomes a `BasicRelationalPersistentProperty`.
`JdbcMappingContext` is the JDBC subclass.

### 2.2 Relationship classification

A property is a *relation* if its type is not a "simple type". Simple types are JDBC types (`JdbcSimpleTypes`) plus types
registered in `JdbcCustomConversions` (`RelationalPredicates::isRelation`). Each relation property is classified as:

- single entity (one-to-one),
- `Set` / collection (unordered one-to-many),
- `List` or `Map`, called **"qualified"** (one-to-many with an index or key column),
- `@Embedded` (flattened into the parent table, optional column prefix).

### 2.3 Paths and naming

`PersistentPropertyPath` and the relational wrapper **`AggregatePath`** (`DefaultAggregatePath`) describe the route from
root to leaf, e.g. `Order.items.product`. Almost every name is derived from the path:

- **Table name:** `NamingStrategy.getTableName` — `CamelCase` → `snake_case`, or `@Table`.
- **Column name:** `NamingStrategy.getColumnName`, or `@Column`.
- **Back-reference column** in the child table pointing to the parent: `getReverseColumnName`. Default is the **parent's table name** (e.g. `order`). Override with `@MappedCollection(idColumn=...)`.
- **Key/index column** for `List` / `Map`: `getKeyColumn`. Default `<parentTable>_key`. Override with `@MappedCollection(keyColumn=...)`.
- **Id-defining parent:** `AggregatePath.getIdDefiningParentPath()` finds the nearest ancestor with an `@Id`. A child without its own id is identified by the parent id plus its key columns.

```java
default String getReverseColumnName(RelationalPersistentProperty property) {
    return property.getOwner().getTableName().getReference();
}
default String getKeyColumn(RelationalPersistentProperty property) {
    return getReverseColumnName(property) + "_key";
}
```

Other annotations: `@Id`, `@Version`, `@InsertOnlyProperty`, `@ReadOnlyProperty`, `@Embedded(onEmpty=..., prefix=...)`.

### 2.4 Is the entity new?

`PersistentEntityIsNewStrategy` (commons, `mapping/model`):

- If the entity has `@Version`, the version is checked; otherwise the id.
- `null` → new. Non-null wrapper type → not new. Primitive number → `0` means new.
- If the entity implements `Persistable`, its own `isNew()` wins.

```java
if (value == null) return true;
if (valueType != null && !valueType.isPrimitive()) return false;
if (value instanceof Number n) return n.longValue() == 0;
throw new IllegalArgumentException("Could not determine whether ... is new; Unsupported identifier or version property");
```

### 2.5 Creating and mutating instances (commons)

- `PreferredConstructorDiscoverer` picks the constructor (Kotlin-aware: primary constructor, default params).
- `EntityInstantiator` creates instances; `ClassGeneratingEntityInstantiator` generates bytecode for speed.
  `KotlinClassGeneratingEntityInstantiator` handles Kotlin default parameter values by calling the synthetic
  `$default` constructor with a bitmask.
- `PersistentPropertyAccessor` sets values; `ClassGeneratingPropertyAccessorFactory` generates accessor bytecode.
- Immutable objects: `InstantiationAwarePropertyAccessor` uses withers, Kotlin `copy(...)` (`KotlinCopyMethod`), or
  re-invokes the constructor, and **returns a new object**.

This matters for the write path (§3.5): every "set id" can produce a new instance, which must be passed up the tree.

### 2.6 Cross-aggregate references

`AggregateReference<T, ID>` holds only the id of another aggregate root. Stored as a plain column, never loaded/cascaded.

### 2.7 Conversions

`JdbcCustomConversions` (reading/writing converters) and `JdbcSimpleTypes` decide what counts as a column value.

### 2.8 The `Dialect` abstraction (`core/dialect`)

- identifier quoting and case,
- LIMIT/OFFSET rendering,
- lock clauses (`SELECT ... FOR UPDATE` variants),
- array support,
- id generation (`IdGeneration`: generated-keys support, sequence-select SQL, key column names),
- upsert SQL,
- whether Single Query Loading is supported.

---

## 3. The write path: `repository.save(x)` → SQL

### 3.1 Call chain

```
proxy → SimpleJdbcRepository.save → JdbcAggregateTemplate.save
  → changeCreatorSelectorForSave: entity.isNew(x) ? createInsertChange(prepareVersionForInsert)
                                                  : createUpdateChange(prepareVersionForUpdate)
  → beforeExecute:  BeforeConvertEvent/Callback → build RootAggregateChange (WritingContext) → BeforeSaveEvent/Callback
  → AggregateChangeExecutor.executeSave → JdbcAggregateChangeExecutionContext.execute*(DbAction)
  → DataAccessStrategy.insert/update/delete → SqlGenerator SQL → NamedParameterJdbcOperations
  → populateIdsIfNecessary (rebuild immutable tree)
  → AfterSaveEvent/Callback → returned (possibly new) instance
```

- **Version prep:** `prepareVersionForInsert` sets version to 1 (0 for primitives). `prepareVersionForUpdate`
  remembers the *previous* version and writes `previous + 1` into a (possibly copied) entity.
- **Events and callbacks** (both run; callbacks may return a replacement entity):
  1. `BeforeConvert`
  2. (the `DbAction`s are built)
  3. `BeforeSave` — receives the `AggregateChange`
  4. SQL runs
  5. `AfterSave`

  Reads fire `AfterConvert`. Deletes fire `BeforeDelete` and `AfterDelete`.
  Sequence-based ids are filled in by `IdGeneratingEntityCallback` (a `BeforeConvertCallback`) via
  `SequenceEntityCallbackDelegate` and the dialect's sequence SQL.

### 3.2 Turning the object tree into `DbAction`s (`WritingContext`)

`WritingContext` is the core of the write path. On construction it computes **all relation paths** of the root type:

```java
this.paths = context.findPersistentPropertyPaths(entityType, RelationalPredicates::isRelation)
        .filter(ppp -> context.getAggregatePath(ppp).isWritable()).toList();
```

Then:

```java
void save() {
    if (isNew(root)) {
        setRootAction(new DbAction.InsertRoot<>(root, rootIdValueSource));
        insertReferenced().forEach(aggregateChange::addAction);
    } else {
        setRootAction(new DbAction.UpdateRoot<>(root, previousVersion));
        deleteReferenced().forEach(aggregateChange::addAction);   // DELETE all children, per path
        insertReferenced().forEach(aggregateChange::addAction);   // re-INSERT all children
    }
}
```

`insert()` and `update()` are the same without the `isNew` check; `upsert()` emits `UpsertRoot` + delete/insert children.

- **`deleteReferenced()`** creates one `DbAction.Delete(rootId, path)` per relation path, each added at index 0 →
  deletes run **deepest path first**, keeping foreign keys valid.
- **`insertReferenced()`** walks paths **parent before child**. For each path, `from(path)` collects actual values
  into `PathNode`s and caches them per path in `nodesCache`, so a child path's nodes are found from the parent
  path's nodes. `createNodes` handles: embedded, qualified `Map` (`Pair(key, value)`), qualified `List`
  (`Pair(index, value)`), plain collections/arrays, single entity values. Each node becomes
  `DbAction.Insert(instance, path, parentAction, qualifiers, idValueSource)`.
  - **`dependingOn = parentAction`** builds a DAG of actions — how a child insert later finds the parent's
    *generated* id.
  - **Qualifiers:** list index / map key become the key-column value. If the parent has no `@Id`, the parent's
    qualifiers are inherited → composite back-references for nested lists in lists.
- **`IdValueSource`** per entity:
  - `GENERATED` – id is null, DB creates it,
  - `PROVIDED` – id already set,
  - `NONE` – no id property (many child entities).

**Example:** `Order` with three `items` and one `shippingInfo`, update case. SQL in order:

1. `UPDATE order ... [WHERE version = ?]`
2. `DELETE FROM shipping_info WHERE order = ?`
3. `DELETE FROM order_item WHERE order = ?`
4. three × `INSERT INTO order_item (order, order_key, ...)`
5. one `INSERT INTO shipping_info`

(Thorben Janssen's article shows real logs of exactly this pattern.)

### 3.3 Executing the actions (`AggregateChangeExecutor` / `JdbcAggregateChangeExecutionContext`)

- `AggregateChangeExecutor.execute` is a large `instanceof` dispatch over the `DbAction` types:
  `InsertRoot`, `BatchInsertRoot`, `Insert`, `BatchInsert`, `UpsertRoot`, `UpdateRoot`, `Delete`, `BatchDelete`,
  `DeleteAll`, `DeleteRoot`, `BatchDeleteRoot`, `DeleteAllRoot`, `DeleteRootByQuery`, `DeleteByQuery`,
  `AcquireLockRoot`, `AcquireLockAllRoot`, `AcquireLockAllRootByQuery`.
- Each insert stores a `DbActionExecutionResult(action, generatedId)` in an ordered `LinkedHashMap`.
- **Back-references for a child insert** (`getParentKeys`):
  1. Follow `dependingOn` up to the action that owns the id (`getIdOwningAction`, matched against
     `getIdDefiningParentPath`).
  2. Take the **generated id from that action's result** if its id source was `GENERATED`; else read the id from
     the entity (`getPotentialGeneratedIdFrom` / `getIdFrom`).
  3. Build an `Identifier` (name→value map of extra columns) with
     `JdbcIdentifierBuilder.forBackReferences(...).withQualifier(...)`.

  The `Identifier` is passed to `DataAccessStrategy.insert(entity, type, identifier, idValueSource)` and the extra
  columns become part of the `INSERT`. Composite / embedded ids are split into several back-reference columns
  through `getIdMapper`.

```java
private Identifier getParentKeys(DbAction.WithDependingOn<?> action, JdbcConverter converter) {
    Object id = getParentId(action);
    AggregatePath aggregatePath = context.getAggregatePath(action.propertyPath());
    JdbcIdentifierBuilder identifier = JdbcIdentifierBuilder
            .forBackReferences(converter, aggregatePath, getIdMapper(id, aggregatePath, converter));
    for (var qualifier : action.qualifiers().entrySet()) {
        identifier = identifier.withQualifier(context.getAggregatePath(qualifier.getKey()), qualifier.getValue());
    }
    return identifier.build();
}
```

- **Optimistic locking:** `updateWithVersion` uses `SqlGenerator.getUpdateWithVersion()`:
  `UPDATE ... SET version = :new ... WHERE id = :id AND version = :___oldOptimisticLockingVersion`.
  0 affected rows → `OptimisticLockingFailureException` (`OptimisticLockingUtils.updateFailed`).
  `deleteWithVersion` works the same way.
- **Id generation** — chosen by `InsertStrategyFactory`:
  - `GENERATED` → `IdGeneratingInsertStrategy` (uses `KeyHolder` / `getGeneratedKeys` and dialect-specific key
    column names),
  - otherwise a plain insert.
  - Batch inserts: `BatchInsertStrategy` or `IdGeneratingBatchInsertStrategy`.
- `SqlParametersFactory` builds the `SqlParameterSource` for insert/update (converting values, adding identifier
  columns, respecting `@InsertOnlyProperty`).

### 3.4 Batching (`saveAll` / `insertAll`)

`SaveBatchingAggregateChange` merges many `RootAggregateChange`s:

- Consecutive `InsertRoot`s with the **same** `IdValueSource` → one `BatchInsertRoot`. An `UpdateRoot` or
  `UpsertRoot` breaks the batch.
- All `Insert`s and `Delete`s are grouped by path in `BatchedActions` → `BatchInsert` / `BatchDelete`.
- Output order: root actions → deletes (leaves first) → inserts (root to leaves).

```java
rootActions.forEach(consumer);
if (insertRootBatchCandidates.size() > 1) consumer.accept(new DbAction.BatchInsertRoot<>(insertRootBatchCandidates));
else insertRootBatchCandidates.forEach(consumer);
deleteActions.forEach(consumer);
insertActions.forEach(consumer);
```

### 3.5 Immutable entities: rebuilding the tree (`populateIdsIfNecessary`)

After all SQL has run, results are walked **in reverse** (leaves first):

1. `setIdAndCascadingProperties` writes the generated id through a `PersistentPropertyAccessor` — may create a
   *new* instance via `copy` / wither.
2. If the new instance differs from the original, `StagedValues.stage(parentAction, path, qualifier, newEntity)`
   records it (collection children that didn't change are `gather`ed so collections can be rebuilt fully).
3. When the parent is processed, all staged children are written into it; lists and maps are rebuilt using the
   qualifiers. This creates a new parent, and so on up to the root.
4. The new root(s) are returned (`InsertRoot` / `UpdateRoot` / `UpsertRoot` results).

This is why `save()` returns a value you **must** use with Kotlin `val` data classes.

### 3.6 Upsert (4.1+)

- `WritingContext.upsert()` emits `DbAction.UpsertRoot`, then the same delete-children / insert-children pattern.
- `DefaultDataAccessStrategy.upsert` builds parameters like an insert, then runs dialect-specific upsert SQL
  (`MERGE` / `ON CONFLICT` / `ON DUPLICATE KEY`). Supported: PostgreSQL, H2, MySQL, MariaDB, SQL Server, Oracle,
  DB2, HSQLDB.
- The id must be set. The source still carries `// TODO: how does that really go together with save?`.

### 3.7 Delete

`RelationalEntityDeleteWriter` emits deletes for all relation paths **deepest first**, then `DeleteRoot` (with a
version check if `@Version` exists). Before that it may emit `AcquireLockRoot` (`SELECT ... FOR UPDATE`) to avoid
deadlocks. `deleteAll` creates `DeleteAll(path)` / `DeleteAllRoot` without loading anything.

---

## 4. The read path: rows → aggregate

### 4.1 Classic loading (default): root query + one query per collection

- `DefaultDataAccessStrategy.findById` / `findAll` run `SqlGenerator.getFindOne()` / `getFindAll()`. The root
  `SELECT` contains:
  - simple columns,
  - flattened `@Embedded` columns,
  - **LEFT OUTER JOINs for single-valued (one-to-one) entity relations.**

  To-many relations are **never joined**.
- `EntityRowMapper` turns each row into a `RowDocument` (`RowDocumentResultSetExtractor.toRowDocument`), then calls
  `MappingJdbcConverter.readAndResolve`. (`MapEntityRowMapper` is used for map-valued relations, `ResultSetAccessor`
  wraps the JDBC `ResultSet`.)
- For each collection/map property, the converter's `ResolvingRelationalPropertyValueProvider` builds an
  `Identifier` (parent id + inherited qualifiers) and calls `RelationResolver.findAllByPath(identifier, path)`. That
  runs `SqlGenerator.getFindAllByProperty(...)`:
  `SELECT ... FROM child WHERE <back-ref col> = ? [AND ...] [ORDER BY key col]`.
  `buildConditionForBackReference` adds an equality predicate per identifier component, combined with `AND`.
  Key columns are selected so lists can be rebuilt in order and maps keyed.
- This recurses for nested collections → a real **N+1** pattern: one root query + one query per collection path
  per loaded parent.

### 4.2 Single Query Loading (since 3.2, opt-in)

- Enabled with `RelationalMappingContext.setSingleQueryLoadingEnabled(true)`. The dialect must allow it (default
  true; H2 and HSQLDB disabled in current source).
- Covers only *direct, non-embedded* collections and maps. Single references, embedded relations, nested paths,
  sorted or limited queries **fall back** to the classic path (`SingleQueryFallbackDataAccessStrategy`).
- SQL built by `SingleQuerySqlGenerator` (spring blog explanation):
  1. **One inline subselect per table.** Each selects its columns plus back-references and keys, plus
     `row_number() over (partition by <backref>) x_rn` and `count(*) over (partition by <backref>) x_cnt`.
     The root uses a fixed `1 rn`.
  2. **LEFT JOIN** all subselects on the id, and **align sibling collections by row number** (`h_rn = t_rn` or
     "the shorter list is exhausted"). Without this the joins would multiply into a Cartesian product.
  3. Project `case when x_rn = rn then col end` so each child value appears only once. Order by root id and `rn`.
  4. `AliasFactory` creates unique aliases per path, column, key, back-reference and window column.
- **Folding:** `RowDocumentResultSetExtractor.iterate` groups contiguous rows by root id.
  `RowDocumentExtractorSupport`, `PathToColumnMapping`, `AggregateContext` and `RowDocumentSink` / `ContainerSink`
  fold the flat rows into nested `RowDocument`s (lists by index, maps by key). `AggregateReader` passes each
  document to `MappingRelationalConverter.read` — no extra queries, relations are already in the document.
- This document-folding step is also what Spring sees as the missing piece for relationship support in **R2DBC**.
  Spring Data R2DBC still has **no** relationship support, because resolving relations during mapping would block.

### 4.3 Row / document → object

`MappingRelationalConverter.read` (`readAggregate` resolves the entity first):

1. Resolve `InstanceCreatorMetadata` (constructor or factory method).
2. Fill constructor parameters from a `ParameterValueProvider` that converts values and recurses into embedded and
   relation values.
3. Call the `EntityInstantiator`.
4. `populateProperties` sets the id first, then all non-constructor properties via a converting
   `PersistentPropertyAccessor`.

Classic JDBC uses `MappingJdbcConverter.readAndResolve` (fetches relations); single-query reads use `read`.
For Kotlin, `KotlinClassGeneratingEntityInstantiator` handles default parameter values via the `$default`
constructor + bitmask.

### 4.4 Query / Criteria API and the SQL AST

- `org.springframework.data.relational.core.query.Query` holds a `CriteriaDefinition`, a `Sort`, limit/offset and a
  column list. `Criteria.where("name").is(x).and(...)` builds a predicate tree.
- `QueryMapper.getMappedObject` resolves property paths to columns through mapping metadata, converts values to JDBC
  values, adds bind parameters to a `MapSqlParameterSource`, and returns AST `Condition`s (comparisons, `IN`,
  `BETWEEN`, null checks, nested `AND`/`OR`).
- `core/sql` is an internal **SQL AST** (`Table`, `Column`, `Select`, `Join`, `Condition`, `InlineQuery`,
  `AnalyticFunction`, …) with staged builders. `SqlRenderer` turns it into a string using visitors plus the
  dialect's `RenderContext` (quoting, LIMIT, bind markers).
- `SqlGenerator` (one per entity type, cached in `SqlGeneratorSource`) builds all CRUD statements with this AST and
  caches the resulting strings.

---

## 5. Repository infrastructure (mostly `spring-data-commons`, reusable by any store)

### 5.1 Bootstrapping

1. `@EnableJdbcRepositories` imports `JdbcRepositoriesRegistrar` (a `RepositoryBeanDefinitionRegistrarSupport`).
2. `RepositoryConfigurationDelegate` scans the classpath for interfaces extending `Repository` that are not
   `@NoRepositoryBean`. `JdbcRepositoryConfigExtension` (a `RepositoryConfigurationExtensionSupport`) adds
   store-specific attributes and the "identifying types/annotations" used in multi-store setups.
3. One bean definition per interface, of type `JdbcRepositoryFactoryBean`. Implementations of custom fragments
   (e.g. `FooRepositoryImpl`, `Impl` suffix configurable) are found and attached as `RepositoryFragments`.
4. Infrastructure beans from `AbstractJdbcConfiguration`: `JdbcMappingContext`, `JdbcConverter`,
   `JdbcCustomConversions`, `Dialect`, `DataAccessStrategy`, `JdbcAggregateTemplate`.
5. Spring Boot: `DataJdbcRepositoriesAutoConfiguration` does all this automatically when a `DataSource` /
   `NamedParameterJdbcOperations` is present.

### 5.2 Building the proxy (`RepositoryFactorySupport.getRepository`)

`JdbcRepositoryFactory` mostly supplies:

- `getTargetRepository` — creates `SimpleJdbcRepository(template, persistentEntity, converter)` by reflection
  (`getTargetRepositoryViaReflection`); `repositoryBaseClass` can replace it,
- `getQueryLookupStrategy` — creates `JdbcQueryLookupStrategy` with a `CachingValueExpressionDelegate` and a
  `RowMapperFactory`.

Commons then builds a Spring AOP `ProxyFactory`:

```java
ProxyFactory result = new ProxyFactory();
result.setTarget(target);
result.setInterfaces(repositoryInterface, Repository.class, TransactionalProxy.class);
// + MethodInvocationValidator (nullability), ExposeMetadataInterceptor, RepositoryProxyPostProcessors
//   (transactions, exception translation), DefaultMethodInvokingMethodInterceptor (Java default / Kotlin interface methods)
result.addAdvice(new QueryExecutorMethodInterceptor(information, projectionFactory, queryLookupStrategy, namedQueries, ...));
result.addAdvice(new ImplementationMethodExecutionInterceptor(information, compositionToUse, ...));
```

- **`QueryExecutorMethodInterceptor`**: at creation time every *query method* (not implemented by any fragment or the
  base class) is resolved once into a `RepositoryQuery` via the `QueryLookupStrategy` → invalid method names fail at
  startup. At call time it runs `RepositoryQuery.execute(args)` and adapts the result type
  (`QueryExecutionResultHandler`: `Optional`, collections, `Stream`, reactive/coroutine types).
- **`ImplementationMethodExecutionInterceptor`**: sends everything else to the `RepositoryComposition` — custom
  fragments first, base class (`SimpleJdbcRepository` → template) last.
- **Transactions:** `TransactionalRepositoryProxyPostProcessor` adds a `TransactionInterceptor`.
  `SimpleJdbcRepository` is `@Transactional(readOnly = true)` with `@Transactional` on its write methods.

### 5.3 Query lookup and derived queries

- `JdbcQueryLookupStrategy` strategies: `CREATE`, `USE_DECLARED_QUERY`, `CREATE_IF_NOT_FOUND` (default):
  - **declared** (`@Query` or a named query from `META-INF/jdbc-named-queries.properties`) → `StringBasedJdbcQuery`
    (SpEL / value expressions, named parameters, `@Modifying`),
  - otherwise **derived** → `PartTreeJdbcQuery`.
- **`PartTree`** (commons) parses the method name with regexes:
  - subject: `find|read|get|query|search|stream|count|exists|delete…By`, plus `Distinct`, `First10` / `Top`,
  - predicate: split on `Or`, then `And` → `OrPart` → `Part`s,
  - each `Part` = property path (validated against the domain type) + `Part.Type` (`SIMPLE_PROPERTY`,
    `GREATER_THAN`, `LIKE`, `IN`, `BETWEEN`, `IS_NULL`, …) + `IgnoreCase`,
  - `OrderBy…Asc/Desc` at the end.
- **`AbstractQueryCreator`** walks the tree with `create(part)` / `and(part, base)` / `or(base, criteria)` /
  `complete(criteria, sort)`. `RelationalQueryCreator` maps it onto the same `Criteria` objects as the template API:

```java
protected Criteria create(Part part, Iterator<Object> it) { return criteriaFactory.createCriteria(part); }
protected Criteria and(Part part, Criteria base, Iterator<Object> it) { return base.and(criteriaFactory.createCriteria(part)); }
protected Criteria or(Criteria base, Criteria criteria) { return base.or(criteria); }
```

  `CriteriaFactory` is a `switch (part.getType())` mapping to `criteriaStep.is / greaterThan / like / in / between /
  isNull …`.
- `JdbcQueryCreator` turns the `Criteria` into an AST `Select` using `QueryMapper` and the dialect, joining one-to-one
  relations like `findById`. It produces a `ParametrizedQuery` (SQL string + parameter source).
  `JdbcCountQueryCreator` and `JdbcDeleteQueryCreator` handle count and delete.
- **Return types and projections:**
  - `JdbcQueryExecution` picks single, collection, stream, `Page`, `Slice`, `Window` (scrolling) or modifying
    execution. `Page` triggers an extra count query.
  - `ResultProcessor` / `ReturnedType` handle interface projections (proxies over a backing map/entity) and DTO
    projections (constructor binding), which can narrow the `SELECT` columns.
  - Collections are loaded per entity through the normal `EntityRowMapper` → `RelationResolver` path.
- **Query by Example:** `RelationalExampleMapper` + `FetchableFluentQueryByExample`.

### 5.4 AOT repositories (Spring Data 2025.1 / 4.0)

- At build time (Spring AOT / native image), `JdbcRepositoryContributor` and `JdbcCodeBlocks`
  (`jdbc/repository/aot`: also `AotQueries`, `DerivedAotQuery`, `StringAotQuery`, `QueriesFactory`) generate
  **real Java source** for each query method into a `<Repo>Impl__Aot` fragment (extends
  `AotRepositoryFragmentSupport`). SQL is pre-rendered from `PartTree` or `@Query`.
- At startup that fragment is put into the `RepositoryComposition`, skipping runtime parsing and reflection.
- Enabled with `spring.aot.enabled=true`; disabled with `spring.aot.jdbc.repositories.enabled=false`.
  Reactive repositories are not covered.

### 5.5 Coroutines

`CoroutineCrudRepository` (commons) exists only for **reactive** stores; adapted through `isReactiveRepository()` and
Reactor↔coroutine bridges. Spring Data R2DBC supports it; Spring Data JDBC does not (`suspend` = blocking calls).

---

## 6. Facts relevant to an Exposed version (observations, no design)

- **Exposed's `SpringTransactionManager`** (`spring-transaction`, `spring7-transaction`) already plugs into Spring's
  `@Transactional`. Spring Data's `TransactionalRepositoryProxyPostProcessor` only needs a
  `PlatformTransactionManager`, so it would work with it.
- **The commons layer has no ties to JDBC.** A store only has to implement:
  - `RepositoryFactorySupport` (`getTargetRepository`, `getQueryLookupStrategy`, `getEntityInformation`),
  - a `RepositoryFactoryBeanSupport`,
  - a `RepositoryConfigurationExtensionSupport` with an `@Enable…Repositories` registrar,
  - a base CRUD class,
  - `RepositoryQuery` implementations.

  `PartTree` / `AbstractQueryCreator` are directly reusable; the creator's output type `<T>` can be anything — for
  Exposed it would be an `Op<Boolean>`.
- **Already present in Exposed:**
  - SQL AST + dialect rendering (`SqlGenerator`, `core/sql`, `Dialect`) ≈ Exposed `Table` / `Query` / `Op` /
    `DatabaseDialect` / `FunctionProvider`,
  - `Criteria` / `QueryMapper` ≈ Exposed `Op` expressions,
  - generated keys ≈ Exposed `insert` / `batchInsert` returning `ResultRow` with auto-increment values,
  - `upsert` ≈ Exposed `upsert` / `batchUpsert` (dialect-aware).
- **Not present in Exposed:** the metadata layer (`MappingContext`, `PersistentEntity`, `AggregatePath`), the
  class↔table binding, the tree-to-`DbAction` split with back-references and qualifiers, the rebuild of immutable
  trees, and row folding for single-query aggregate loading.
- **Model differences:** an Exposed `Table` object is an explicit, type-safe schema that Spring Data must infer from
  annotations via `NamingStrategy`. The Exposed DAO (`Entity` / `EntityClass`) uses identity maps, caching and lazy
  references — the opposite of the Spring Data JDBC rules.
- **The R2DBC gap is the same for Exposed.** Spring blocks relation loading in R2DBC because mapping is synchronous;
  single-query loading with row folding is their stated direction for fixing that.
- **Prior art in Kotlin:**
  - Komapper: KSP-generated metamodel, JDBC and R2DBC, Spring Boot starters, no Spring Data repositories.
  - Jimmer: its own Spring Data-style `KRepository`.
  - No maintained "Spring Data Exposed" project found. Community usage is mostly Exposed alongside
    `spring-data-jdbc` (e.g. JetBrains/Exposed issue #1504).

---

## 7. Key source files

In `spring-data-relational` unless noted:

- **Write path:** `spring-data-jdbc/.../core/JdbcAggregateTemplate.java`, `AggregateChangeExecutor.java`,
  `JdbcAggregateChangeExecutionContext.java`; `spring-data-relational/.../core/conversion/WritingContext.java`,
  `DbAction.java`, `SaveBatchingAggregateChange.java`, `RelationalEntityDeleteWriter.java`
- **Data access:** `spring-data-jdbc/.../core/convert/DefaultDataAccessStrategy.java`, `SqlGenerator.java`,
  `SqlParametersFactory.java`, `InsertStrategyFactory.java`, `IdGeneratingInsertStrategy.java`,
  `JdbcIdentifierBuilder.java`, `IdGeneratingEntityCallback.java`
- **Read path:** `EntityRowMapper.java`, `MapEntityRowMapper.java`, `MappingJdbcConverter.java`,
  `RelationResolver.java`, `SingleQueryDataAccessStrategy.java`, `SingleQueryFallbackDataAccessStrategy.java`,
  `AggregateReader.java`, `RowDocumentResultSetExtractor.java`, `RowDocumentExtractorSupport.java`,
  `PathToColumnMapping.java`; `relational/core/sqlgeneration/SingleQuerySqlGenerator.java`, `AliasFactory.java`;
  `relational/core/conversion/MappingRelationalConverter.java`; `relational/core/sql/**/render/*`
- **Mapping:** `relational/core/mapping/RelationalMappingContext.java`, `AggregatePath.java`, `NamingStrategy.java`;
  commons `mapping/context/AbstractMappingContext.java`, `mapping/model/PersistentEntityIsNewStrategy.java`,
  `InstantiationAwarePropertyAccessor.java`, `KotlinClassGeneratingEntityInstantiator.java`
- **Repositories:** commons `repository/core/support/RepositoryFactorySupport.java`,
  `QueryExecutorMethodInterceptor.java`, `repository/query/parser/PartTree.java`, `AbstractQueryCreator.java`;
  JDBC `repository/support/JdbcRepositoryFactory.java`, `JdbcQueryLookupStrategy.java`, `SimpleJdbcRepository.java`,
  `repository/query/PartTreeJdbcQuery.java`, `JdbcQueryCreator.java`, `StringBasedJdbcQuery.java`;
  relational `repository/query/RelationalQueryCreator.java`, `CriteriaFactory.java`;
  AOT: `spring-data-jdbc/.../repository/aot/*`

## 8. Further reading

- Spring Data JDBC reference ("Domain Driven Design", "Persisting Entities"): https://docs.spring.io/spring-data/relational/reference/jdbc/
- Single Query Loading: https://spring.io/blog/2023/08/31/this-is-the-beginning-of-the-end-of-the-n-1-problem-introducing-single-query
- AOT repositories: https://spring.io/blog/2025/05/22/spring-data-ahead-of-time-repositories and https://spring.io/blog/2025/11/25/spring-data-ahead-of-time-repositories-part-2
- Jens Schauder, "Migrating from JPA to Spring Data JDBC" (Spring I/O 2024) and "The New Kid on the Block" (InfoQ)
- Delete-and-reinsert in practice: https://thorben-janssen.com/spring-data-jdbc-aggregates
