package kotlin.dao

import java.util.ArrayList

object EntityHook {
    private val entitySubscribers: ArrayList<(Entity, Boolean) -> Unit> = ArrayList()

    public fun subscribe (action: (Entity)->Unit) {
        entitySubscribers.add { e, isCreated ->
            action(e)
        }
    }

    public fun subscribe (action: (Entity, Boolean)->Unit) {
        entitySubscribers.add { e, isCreated ->
            action(e, isCreated)
        }
    }

    public fun alertSubscribers(o: Entity, isCreated: Boolean) {
        entitySubscribers.forEach { it(o, isCreated) }
    }
}