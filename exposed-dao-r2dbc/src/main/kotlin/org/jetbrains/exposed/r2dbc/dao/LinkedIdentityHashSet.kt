package org.jetbrains.exposed.r2dbc.dao

import java.util.*

internal class LinkedIdentityHashSet<T> : MutableSet<T> {
    private val set: MutableSet<T> = Collections.newSetFromMap(IdentityHashMap())
    private val list: MutableList<T> = LinkedList()

    override fun add(element: T): Boolean {
        return set.add(element).also { if (it) list.add(element) }
    }

    override fun addAll(elements: Collection<T>): Boolean {
        val toAdd = elements.filter { it !in set }
        if (toAdd.isEmpty()) return false
        set.addAll(toAdd)
        list.addAll(toAdd)
        return true
    }

    override fun clear() {
        set.clear()
        list.clear()
    }

    override fun iterator(): MutableIterator<T> {
        return object : MutableIterator<T> {
            private val delegate = list.iterator()
            private var current: T? = null

            override fun hasNext() = delegate.hasNext()

            override fun next() = delegate.next().also {
                current = it
            }

            override fun remove() {
                val p = checkNotNull(current)
                this@LinkedIdentityHashSet.remove(p)
                current = null
            }
        }
    }

    override fun remove(element: T): Boolean {
        return set.remove(element).also { if (it) removeFromListByIdentity(element) }
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        var changed = false
        for (e in elements) if (remove(e)) changed = true
        return changed
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        val toKeep: MutableSet<T> = Collections.newSetFromMap(IdentityHashMap())
        toKeep.addAll(elements)
        val toRemove = list.filter { it !in toKeep }
        if (toRemove.isEmpty()) return false
        for (e in toRemove) remove(e)
        return true
    }

    private fun removeFromListByIdentity(element: T) {
        val iter = list.iterator()
        while (iter.hasNext()) {
            if (iter.next() === element) {
                iter.remove()
                return
            }
        }
    }

    override val size: Int
        get() = set.size

    override fun contains(element: T): Boolean {
        return set.contains(element)
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        return set.containsAll(elements)
    }

    override fun isEmpty(): Boolean {
        return set.isEmpty()
    }
}
