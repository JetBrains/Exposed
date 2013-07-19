package kotlin.sql

trait Expression {
    fun toSQL(): String
}