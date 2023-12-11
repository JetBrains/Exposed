package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.ForUpdateOption

/** Represents iterable elements of a database result that can be manipulated using SQL clauses. */
interface SizedIterable<out T> : Iterable<T> {
    /** Returns the specified amount of elements, [n], starting after the specified [offset]. */
    fun limit(n: Int, offset: Long = 0): SizedIterable<T>

    /** Returns the number of elements that match a specified criterion. */
    fun count(): Long

    /** Whether there are no elements. */
    fun empty(): Boolean

    /** Adds a locking read for the elements against concurrent updates according to the rules specified by [option]. */
    fun forUpdate(option: ForUpdateOption = ForUpdateOption.ForUpdate): SizedIterable<T> = this

    /** Removes any locking read for the elements. */
    fun notForUpdate(): SizedIterable<T> = this

    /** Copies the elements. */
    fun copy(): SizedIterable<T>

    /** Sorts the elements according to the specified expression [order]. */
    fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): SizedIterable<T>
}

/** Returns an [EmptySizedIterable]. */
fun <T> emptySized(): SizedIterable<T> = EmptySizedIterable()

/** Represents an empty set of elements that cannot be iterated over. */
@Suppress("IteratorNotThrowingNoSuchElementException")
class EmptySizedIterable<out T> : SizedIterable<T>, Iterator<T> {
    override fun count(): Long = 0

    override fun limit(n: Int, offset: Long): SizedIterable<T> = this

    override fun empty(): Boolean = true

    override operator fun iterator(): Iterator<T> = this

    override operator fun next(): T {
        throw UnsupportedOperationException()
    }

    override fun hasNext(): Boolean = false

    override fun copy(): SizedIterable<T> = this

    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): SizedIterable<T> = this
}

/** Represents a collection of elements that defers to the specified [delegate]. */
class SizedCollection<out T>(val delegate: Collection<T>) : SizedIterable<T> {
    constructor(vararg values: T) : this(values.toList())
    override fun limit(n: Int, offset: Long): SizedIterable<T> {
        return if (offset >= Int.MAX_VALUE) {
            EmptySizedIterable()
        } else {
            SizedCollection(delegate.drop(offset.toInt()).take(n))
        }
    }

    override operator fun iterator() = delegate.iterator()
    override fun count(): Long = delegate.size.toLong()
    override fun empty() = delegate.isEmpty()
    override fun copy(): SizedIterable<T> = SizedCollection(delegate)
    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): SizedIterable<T> = this
}

/** Represents a lazily loaded collection of elements. */
class LazySizedCollection<out T>(_delegate: SizedIterable<T>) : SizedIterable<T> {
    private var delegate: SizedIterable<T> = _delegate

    private var _wrapper: List<T>? = null
    private var _size: Long? = null
    private var _empty: Boolean? = null

    /** A list wrapping data loaded lazily loaded. */
    val wrapper: List<T> get() {
        if (_wrapper == null) {
            _wrapper = delegate.toList()
        }
        return _wrapper!!
    }

    override fun limit(n: Int, offset: Long): SizedIterable<T> = LazySizedCollection(delegate.limit(n, offset))
    override operator fun iterator() = wrapper.iterator()
    override fun count(): Long = _wrapper?.size?.toLong() ?: countInternal()
    override fun empty() = _wrapper?.isEmpty() ?: emptyInternal()
    override fun forUpdate(option: ForUpdateOption): SizedIterable<T> {
        val localDelegate = delegate
        if (_wrapper != null && localDelegate is Query && localDelegate.hasCustomForUpdateState() && !localDelegate.isForUpdate()) {
            error("Impossible to change forUpdate state for loaded data")
        }
        if (_wrapper == null) {
            delegate = delegate.forUpdate(option)
        }
        return this
    }

    override fun notForUpdate(): SizedIterable<T> {
        val localDelegate = delegate
        if (_wrapper != null && localDelegate is Query && localDelegate.hasCustomForUpdateState() && localDelegate.isForUpdate()) {
            error("Impossible to change forUpdate state for loaded data")
        }
        if (_wrapper == null) {
            delegate = delegate.notForUpdate()
        }
        return this
    }

    private fun countInternal(): Long {
        if (_size == null) {
            _size = delegate.count()
            _empty = (_size == 0L)
        }
        return _size!!
    }

    private fun emptyInternal(): Boolean {
        if (_empty == null) {
            _empty = delegate.empty()
            if (_empty == true) _size = 0
        }

        return _empty!!
    }

    override fun copy(): SizedIterable<T> = LazySizedCollection(delegate.copy())

    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): SizedIterable<T> {
        check(_wrapper == null) { "Can't order already loaded data" }
        delegate = delegate.orderBy(*order)
        return this
    }

    /** Whether the collection already has data loaded by its delegate. */
    fun isLoaded(): Boolean = _wrapper != null
}

/** */
infix fun <T, R> SizedIterable<T>.mapLazy(f: (T) -> R): SizedIterable<R> {
    val source = this
    return object : SizedIterable<R> {
        override fun limit(n: Int, offset: Long): SizedIterable<R> = source.copy().limit(n, offset).mapLazy(f)
        override fun forUpdate(option: ForUpdateOption): SizedIterable<R> = source.copy().forUpdate(option).mapLazy(f)
        override fun notForUpdate(): SizedIterable<R> = source.copy().notForUpdate().mapLazy(f)
        override fun count(): Long = source.count()
        override fun empty(): Boolean = source.empty()
        override fun copy(): SizedIterable<R> = source.copy().mapLazy(f)
        override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>) = source.orderBy(*order).mapLazy(f)

        @Suppress("IteratorNotThrowingNoSuchElementException")
        override operator fun iterator(): Iterator<R> {
            val sourceIterator = source.iterator()
            return object : Iterator<R> {
                override operator fun next(): R = f(sourceIterator.next())

                override fun hasNext(): Boolean = sourceIterator.hasNext()
            }
        }
    }
}
