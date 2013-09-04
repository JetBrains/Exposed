package kotlin.sql

fun log(stmt: String) {
    println(stmt)
}

fun log(stmt: StringBuilder) {
    log(stmt.toString())
}
