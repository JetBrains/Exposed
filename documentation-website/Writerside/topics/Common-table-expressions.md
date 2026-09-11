# Common table expressions

Exposed supports ordinary and recursive common table expressions (CTEs) in both the JDBC and R2DBC DSLs.
A CTE is a named query result that you can select from, join, and reference from later CTE definitions.

## Create an ordinary CTE

Create a CTE with `asCte()`, map its output fields, and attach it to the outermost query with `withCtes()`:

```kotlin
val activeUsers = Users
    .select(Users.id, Users.name)
    .where { Users.active eq true }
    .asCte("active_users")

val userId = activeUsers[Users.id]
val userName = activeUsers[Users.name]

val query = activeUsers
    .select(userId, userName)
    .withCtes(activeUsers)

query.forEach { row ->
    println("${row[userId]}: ${row[userName]}")
}
```

The same query construction API is available with R2DBC. Consume the result as a flow:

```kotlin
activeUsers
    .select(userId, userName)
    .withCtes(activeUsers)
    .collect { row -> println(row[userName]) }
```

Use the fields returned by the CTE, such as `userId` and `userName`, when reading a `ResultRow`.
The original table fields identify the corresponding CTE output, but they are not interchangeable in the final result row.

Every computed output must have an explicit alias before it is used in a CTE:

```kotlin
val total = Orders.amount.sum().alias("total")
val totals = Orders.select(Orders.region, total).asCte("regional_totals")
val cteTotal = totals[total]
```

## Use multiple and dependent CTEs

Pass CTEs to `withCtes()` in dependency order. Exposed preserves this order and does not reorder definitions automatically:

```kotlin
val total = Orders.amount.sum().alias("total")
val regionalSales = Orders
    .select(Orders.region, total)
    .groupBy(Orders.region)
    .asCte("regional_sales")

val region = regionalSales[Orders.region]
val regionalTotal = regionalSales[total]

val topRegions = regionalSales
    .select(region, regionalTotal)
    .where { regionalTotal greater BigDecimal("1000") }
    .asCte("top_regions")

val query = topRegions
    .selectAll()
    .withCtes(regionalSales, topRegions)
```

A CTE can also be joined to a table or another CTE because `CommonTableExpression` is a `ColumnSet`.

## Create a recursive CTE

Recursive CTEs require an explicit output schema so the definition can refer to itself:

```kotlin
val number = intLiteral(1).alias("number")

val numbers = recursiveCte("numbers", listOf(number)) { self ->
    val current = self[number]
    val seed = Table.Dual.select(number)
    val next = (current + intLiteral(1)).alias("number")
    val recursiveMember = self.select(next).where { current less 5 }

    seed.unionAll(recursiveMember)
}

val result = numbers[number]
val query = numbers
    .select(result)
    .withCtes(numbers)
    .orderBy(result)
```

Exposed emits `WITH RECURSIVE` only for dialects that require the keyword. SQL Server and Oracle use `WITH` for recursive CTEs.

## Keep the WITH clause outermost

Attach all CTEs to the outermost executable query. A query that owns a `WITH` clause cannot be embedded as a query alias, scalar subquery, predicate subquery, set-operation operand, or insert/select source.

```kotlin
val source = Users.select(Users.id).asCte("source")
val sourceId = source[Users.id]

// Correct: the final executable query owns the WITH clause.
val query = source.select(sourceId).withCtes(source)
```

CTE definitions are snapshotted when `asCte()` or `recursiveCte()` returns, so later mutation of the source query does not change the CTE.

## Dialect support

| Dialect | Ordinary CTEs | Recursive CTEs | Recursive declaration |
|---|---:|---:|---|
| H2 | Yes | Yes | `WITH RECURSIVE` |
| PostgreSQL | Yes | Yes | `WITH RECURSIVE` |
| Redshift | Yes | Yes | `WITH RECURSIVE` |
| SQLite | Yes | Yes | `WITH RECURSIVE` |
| MySQL 8 and later | Yes | Yes | `WITH RECURSIVE` |
| MariaDB 10.2 and later | Yes | Yes | `WITH RECURSIVE` |
| SQL Server | Yes | Yes | `WITH` |
| Oracle | Yes | Yes | `WITH` |

Unsupported versions fail with `UnsupportedByDialectException` before the statement is executed.
The initial CTE API supports `SELECT` queries. Data-modifying CTE definitions and top-level `WITH` clauses on `INSERT`, `UPDATE`, `DELETE`, or `MERGE` are not supported.
