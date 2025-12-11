package org.jetbrains.exposed.v1.r2dbc

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption

/** Represents the iterable elements of a database result. */
interface SizedIterable<out T> : Flow<T> {
    /** Returns a new [SizedIterable] containing only [count] elements. */
    fun limit(count: Int): SizedIterable<T>

    /** Returns a new [SizedIterable] containing only elements starting from the specified [start]. */
    fun offset(start: Long): SizedIterable<T>

    /** Returns a new [SizedIterable] with a locking read for the elements according to the rules specified by [option]. */
    fun forUpdate(option: ForUpdateOption = ForUpdateOption.ForUpdate): SizedIterable<T> = this

    /** Returns a new [SizedIterable] without any locking read for the elements. */
    fun notForUpdate(): SizedIterable<T> = this

    /** Returns a new [SizedIterable] that is a copy of the original. */
    fun copy(): SizedIterable<T>

    /** Returns a new [SizedIterable] with the elements sorted according to the specified expression [order]. */
    fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): SizedIterable<T>

    /** Returns the number of elements stored. */
    suspend fun count(): Long

    /** Whether there are no elements stored. */
    suspend fun empty(): Boolean
}

/** Represents the iterable elements of a database result, which are stored once loaded on first access. */
interface LazySizedIterable<T> : SizedIterable<T> {
    /** The lazily loaded database result. */
    var loadedResult: List<T>?
}

/** Returns an [EmptySizedIterable]. */
fun <T> emptySized(): SizedIterable<T> = EmptySizedIterable()

/** Represents a [SizedIterable] that is empty and cannot be iterated over. */
@Suppress("IteratorNotThrowingNoSuchElementException")
class EmptySizedIterable<out T> : SizedIterable<T> {
    override suspend fun count(): Long = 0

    override fun limit(count: Int): SizedIterable<T> = this

    override fun offset(start: Long): SizedIterable<T> = this

    override suspend fun empty(): Boolean = true

    override suspend fun collect(collector: FlowCollector<T>) {
        throw UnsupportedOperationException()
    }

    override fun copy(): SizedIterable<T> = this

    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): SizedIterable<T> = this
}

/** Represents a [SizedIterable] that defers to the specified [delegate] collection. */
class SizedCollection<out T>(val delegate: Collection<T>) : SizedIterable<T> {
    constructor(vararg values: T) : this(values.asList())

    override fun limit(count: Int): SizedIterable<T> = SizedCollection(delegate.take(count))
    override fun offset(start: Long): SizedIterable<T> = if (start >= Int.MAX_VALUE) {
        EmptySizedIterable()
    } else {
        SizedCollection(delegate.drop(start.toInt()))
    }

    override suspend fun count(): Long = delegate.size.toLong()
    override suspend fun empty() = delegate.isEmpty()
    override fun copy(): SizedIterable<T> = SizedCollection(delegate)
    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): SizedIterable<T> = this

    override suspend fun collect(collector: FlowCollector<T>) {
        delegate.forEach { collector.emit(it) }
    }
}

/** Represents a [SizedIterable] whose elements are only loaded on first access. */
class LazySizedCollection<out T>(_delegate: SizedIterable<T>) : SizedIterable<T> {
    private var delegate: SizedIterable<T> = _delegate

    private var _wrapper: List<T>? = null
    private var _size: Long? = null
    private var _empty: Boolean? = null

    suspend fun wrapper(): List<T> {
        if (_wrapper == null) _wrapper = delegate.toList()
        return _wrapper!!
    }

    override fun limit(count: Int): SizedIterable<T> = LazySizedCollection(delegate.limit(count))
    override fun offset(start: Long): SizedIterable<T> = LazySizedCollection(delegate.offset(start))

    override suspend fun collect(collector: FlowCollector<T>) {
        wrapper().forEach { collector.emit(it) }
    }

    override suspend fun count(): Long = _wrapper?.size?.toLong() ?: countInternal()
    override suspend fun empty() = _wrapper?.isEmpty() ?: emptyInternal()
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

    private suspend fun countInternal(): Long {
        if (_size == null) {
            _size = delegate.count()
            _empty = (_size == 0L)
        }
        return _size!!
    }

    private suspend fun emptyInternal(): Boolean {
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

/**
 * Returns a [SizedIterable] containing the lazily evaluated results of applying the function [f] to each original element.
 */
infix fun <T, R> SizedIterable<T>.mapLazy(f: (T) -> R): SizedIterable<R> {
    val source = this
    return object : LazySizedIterable<R> {
        override var loadedResult: List<R>? = null

        suspend fun loadedResult(): List<R> {
            if (loadedResult == null) loadedResult = source.map { f(it) }.toList()
            return loadedResult!!
        }
        override fun limit(count: Int): SizedIterable<R> = source.copy().limit(count).mapLazy(f)
        override fun offset(start: Long): SizedIterable<R> = source.copy().offset(start).mapLazy(f)
        override fun forUpdate(option: ForUpdateOption): SizedIterable<R> = source.copy().forUpdate(option).mapLazy(f)
        override fun notForUpdate(): SizedIterable<R> = source.copy().notForUpdate().mapLazy(f)
        override suspend fun count(): Long = source.count()
        override suspend fun empty(): Boolean = source.empty()
        override fun copy(): SizedIterable<R> = source.copy().mapLazy(f)
        override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>) = source.orderBy(*order).mapLazy(f)

        override suspend fun collect(collector: FlowCollector<R>) {
            loadedResult().forEach { collector.emit(it) }
        }
    }
}
