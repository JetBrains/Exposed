package kotlin.dao

import org.h2.engine.Session
import java.util.*
import kotlin.sql

object EntityHook {
    private val entitySubscribers: ArrayList<(Entity, Boolean) -> Unit> = ArrayList<(Entity, Boolean) -> Unit>()

    public fun subscribe (action: (Entity)->Unit) {
        entitySubscribers.add { (e, isCreated) ->
            action(e)
        }
    }

    public fun subscribe (action: (Entity, Boolean)->Unit) {
        entitySubscribers.add { (e, isCreated) ->
            action(e, isCreated)
        }
    }

    public fun alertSubscribers(o: Entity, isCreated: Boolean) {
        entitySubscribers.forEach { it(o, isCreated) }
    }
}