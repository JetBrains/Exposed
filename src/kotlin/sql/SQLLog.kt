package kotlin.sql

import org.apache.log4j.Logger

private val logger = Logger.getLogger("Exposed")

fun log(stmt: String) {
    logger?.debug(stmt)
}

fun log(stmt: StringBuilder) {
    log(stmt.toString())
}
