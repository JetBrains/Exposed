package org.jetbrains.exposed.dao

import java.util.ArrayList

object EntityHook {
    private val entitySubscribers: ArrayList<(Entity, Boolean) -> Unit> = ArrayList()

    fun subscribe (action: (Entity)->Unit) {
        entitySubscribers.add { e, isCreated ->
            action(e)
        }
    }

    fun subscribe (action: (Entity, Boolean)->Unit) {
        entitySubscribers.add { e, isCreated ->
            action(e, isCreated)
        }
    }

    fun alertSubscribers(o: Entity, isCreated: Boolean) {
        entitySubscribers.forEach { it(o, isCreated) }
    }
}
