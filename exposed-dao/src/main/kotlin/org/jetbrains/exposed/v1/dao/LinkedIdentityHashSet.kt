package org.jetbrains.exposed.v1.dao

import java.util.*

internal class LinkedIdentityHashSet<T> : MutableSet<T> {
    private val set: MutableSet<T> = Collections.newSetFromMap(IdentityHashMap())
    private val list: MutableList<T> = LinkedList()

    override fun add(element: T): Boolean {
        return set.add(element).also { if (it) list.add(element) }
    }

    override fun addAll(elements: Collection<T>): Boolean {
        val toAdd = elements.filter { it !in set } // Maintain order
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
        return set.remove(element).also { if (it) list.remove(element) }
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        val toRemove = set intersect elements
        if (toRemove.isEmpty()) return false
        set.removeAll(toRemove)
        list.removeAll(toRemove)
        return true
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        return removeAll(set subtract elements)
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
