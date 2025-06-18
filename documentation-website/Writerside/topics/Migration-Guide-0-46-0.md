# Migrating from 0.45.0 to 0.46.0

While Exposed provides migration support in the code itself (by using the `@Deprecated` annotation and `ReplaceWith` quickfix),
this document serves as a reference point for the migration steps necessary to switch to the new query DSL.

### SELECT Query DSL

Exposed's query DSL has been refactored to bring it closer to the syntax of a standard SQL `SELECT` statement.

The `slice()` function has been deprecated in favor of a new `select()` function that accepts the same variable amount of columns and creates a `Query` instance.
If all columns should be selected, use `selectAll()` to create a `Query` instance.

The `Query` class now has the method `where()`, which can be chained to replace the old version of `select { }`.

[Go to migration steps](#migration-steps)

Putting these changes together results in the following new DSL:

```kotlin
// Example 1
// before
TestTable
    .slice(TestTable.columnA)
    .select { TestTable.columnA eq 1 }

// after
TestTable
    .select(TestTable.columnA)
    .where { TestTable.columnA eq 1 }

// Example 2
// before
TestTable
    .slice(TestTable.columnA)
    .selectAll()

// after
TestTable
    .select(TestTable.columnA)

// Example 3
// before
TestTable
    .select { TestTable.columnA eq 1 }

// after
TestTable
    .selectAll()
    .where { TestTable.columnA eq 1 }

// Example 4 - no change
TestTable.selectAll()
```

To be consistent with these changes, the functions `selectBatched()` and `selectAllBatched()` have also been deprecated.
A new `Query` method, `fetchBatchedResults()`, should be used instead as a terminal operation on an existing `Query`:

```kotlin
// Example 1
// before
TestTable
    .selectBatched(50) { TestTable.columnA eq 1 }

// after
TestTable
    .selectAll()
    .where { TestTable.columnA eq 1 }
    .fetchBatchedResults(50)

// Example 2
// before
TestTable
    .slice(TestTable.columnA)
    .selectAllBatched(50)

// after
TestTable
    .select(TestTable.columnA)
    .fetchBatchedResults(50)
```

Lastly, `adjustSlice()` has been renamed to `adjustSelect()`:

```kotlin
// before
val originalQuery = TestTable.select { TestTable.columnA eq 1 }
originalQuery.adjustSlice { slice(TestTable.columnA) }

// after
val originalQuery = TestTable.selectAll().where { TestTable.columnA eq 1 }
originalQuery.adjustSelect { select(TestTable.columnA) }
```

### Migration Steps

1. Use *Edit > Find > Find in Files...* to find any use of `adjustSlice`, then use the `Alt+Enter` quickfix with "Replace usages of '...' in whole project".
2. Repeat step 1 with all the deprecated methods in the following list:
    * `slice`
    * `Query.select`: enter `select\((\s*.+\s*)\)(\s*)\.select` in the search bar (with the regex tab enabled) to find this method easily
    * `select`
    * `selectBatched`
    * `selectAllBatched`
3. Use *Edit > Find > Replace in Files...* to resolve any redundant/incompatible uses of `selectAll()`:
    * Enter `select\((\s*.+\s*)\)(\s*)\.selectAll\(\)` in the search bar (with the regex tab enabled)
    * Enter `select\($1\)` in the replace bar
    * Confirm the results and select "Replace All"
4. Rebuild the project
