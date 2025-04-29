package org.jetbrains.exposed.r2dbc.sql.mtc

import java.util.*

// TODO make internal based on internal usage
//  Move mtc package as sub-package in transactions package since all internal
// Not sure that it's really needed for us,
// because I don't expect that one thread with tx context could start another one by itself
class ThreadLocalMap : InheritableThreadLocal<Hashtable<String, Any?>>() {
    @Suppress("UNCHECKED_CAST")
    override fun childValue(parentValue: Hashtable<String, Any?>?): Hashtable<String, Any?>? {
        return parentValue?.clone() as Hashtable<String, Any?>?
    }
}
