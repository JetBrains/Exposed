# Custom type mapping

<primary-label ref="r2dbc"/>

Custom PostgreSQL types like `citext` or `int4range` can be supported in Exposed’s R2DBC module using custom
[`TypeMapper`](https://jetbrains.github.io/Exposed/api/exposed-r2dbc/org.jetbrains.exposed.v1.r2dbc.mappers/-type-mapper/index.html)
implementations. This enables accurate value binding and SQL generation without depending on JDBC-only features like
`PGobject`.

To achieve this, you need to do the following:

1. [Define a custom column type](#defining-a-custom-column-type).
2. [Implement a custom `TypeMapper`](#implementing-a-typemapper) that binds values of those column types.
3. [Register your mapper](#registering-the-type-mapper) so it takes effect before the built-in `PostgresSpecificTypeMapper`.

## Defining a custom column type

Custom column types are responsible for generating appropriate SQL types and converting values to and from database
representations.

For example, the following `CitextR2dbcColumnType` emits `CITEXT` as its SQL type and can be used in column definitions:

```kotlin
class CitextR2dbcColumnType(
    colLength: Int
) : VarCharColumnType(colLength) {
    override fun sqlType(): String = "CITEXT"
}
```

Similarly, to support range types like `int4range`, you can create an abstract base type:

```kotlin
abstract class RangeR2dbcColumnType<T : Comparable<T>, R : ClosedRange<T>>(
    val subType: ColumnType<T>,
) : ColumnType<R>() {
    abstract fun List<String>.toRange(): R

    override fun nonNullValueToString(value: R): String =
        toPostgresqlValue(value)

    override fun nonNullValueAsDefaultString(value: R): String =
        "'${nonNullValueToString(value)}'"

    override fun valueFromDB(value: Any): R = when (value) {
        is String -> value.trim('[', ')').split(',').toRange()
        else -> error("Unexpected DB value type: ${value::class.simpleName}")
    }

    companion object {
        fun <T : Comparable<T>, R : ClosedRange<T>> toPostgresqlValue(range: R): String =
            "[${range.start},${range.endInclusive}]"
    }
}
```

Concrete subclasses like `IntRangeColumnType` can then implement `toRange()` to handle parsing.

## Implementing a `TypeMapper`

A `TypeMapper` is responsible for binding Kotlin values to R2DBC `Statement` parameters based on the dialect and
column type.

Here's an example `CustomTypeMapper` that supports both `citext` and `int4range`:

```kotlin
class CustomTypeMapper : TypeMapper {
    override val priority: Double = 1.9

    override val dialects = listOf(PostgreSQLDialect::class)

    override val columnTypes = listOf(
        CitextR2dbcColumnType::class,
        IntRangeColumnType::class
    )

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        typeMapping: R2dbcTypeMapping,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        if (value == null) return false

        return when (columnType) {
            is CitextR2dbcColumnType -> {
                statement.bind(index - 1, Parameters.`in`(PostgresqlObjectId.UNSPECIFIED, value))
                true
            }
            is IntRangeColumnType -> {
                statement.bind(
                    index - 1,
                    Parameters.`in`(
                        PG_INT_RANGE_TYPE,
                        RangeR2dbcColumnType.toPostgresqlValue(value as IntRange)
                    )
                )
                true
            }
            else -> false
        }
    }

    private val PG_INT_RANGE_TYPE = PostgresTypes.PostgresType(
        3904, 3904, 3905, 3905, "int4range", "R"
    )
}
```

This implementation ensures that Exposed can serialize these custom types properly at runtime.

## Registering the type mapper

Exposed uses the Java SPI `ServiceLoader` to discover and load any implementations of this interface.
To register your mapper, a new file should be created in the **resources** folder.

1. Create the following file in your project:

    ```generic
    src/main/resources/META-INF/services/org.jetbrains.exposed.v1.r2dbc.mappers.TypeMapper
    ```

2. Add the fully qualified class name of your type mapper to the file:

    ```generic
    com.example.mapper.CustomTypeMapper
    ```

When Exposed initializes, your custom mapper will be loaded and added to the `R2dbcRegistryTypeMapping`.

