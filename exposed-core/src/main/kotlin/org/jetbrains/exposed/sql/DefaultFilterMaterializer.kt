package org.jetbrains.exposed.sql

interface DefaultFilterMaterializer {

    fun materializeDefaultFilter() : Op<Boolean>?
}
