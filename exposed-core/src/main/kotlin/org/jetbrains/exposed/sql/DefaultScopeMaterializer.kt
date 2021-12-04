package org.jetbrains.exposed.sql

interface DefaultScopeMaterializer {

    fun materializeDefaultScope() : Op<Boolean>?
}
