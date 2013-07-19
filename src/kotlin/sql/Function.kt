package kotlin.sql

abstract class Function<T>(vararg val columns: Column<*>): Field<T>() {
}