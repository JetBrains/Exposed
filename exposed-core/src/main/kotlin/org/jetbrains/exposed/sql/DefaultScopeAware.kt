package org.jetbrains.exposed.sql

interface DefaultScopeAware {

    fun materializeDefaultScope() : Op<Boolean>?
}
