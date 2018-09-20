package org.jetbrains.exposed.sql

interface SizedIterable<out T>: Iterable<T> {
    fun limit(n: Int, offset: Int = 0): SizedIterable<T>
    fun count(): Int
    fun empty(): Boolean
    fun forUpdate(): SizedIterable<T> = this
    fun notForUpdate(): SizedIterable<T> = this
}

fun <T> emptySized() : SizedIterable<T> = EmptySizedIterable()

class EmptySizedIterable<out T> : SizedIterable<T>, Iterator<T> {
    override fun count(): Int = 0

    override fun limit(n: Int, offset: Int): SizedIterable<T> = this

    override fun empty(): Boolean = true

    override operator fun iterator(): Iterator<T> = this

    override operator fun next(): T {
        throw UnsupportedOperationException()
    }

    override fun hasNext(): Boolean = false
}

class SizedCollection<out T>(val delegate: Collection<T>): SizedIterable<T> {
    override fun limit(n: Int, offset: Int): SizedIterable<T> = SizedCollection(delegate.drop(offset).take(n))

    override operator fun iterator() = delegate.iterator()
    override fun count() = delegate.size
    override fun empty() = delegate.isEmpty()
}

class LazySizedCollection<out T>(val delegate: SizedIterable<T>): SizedIterable<T> {
    private var _wrapper: List<T>? = null
    private var _size: Int? = null
    private var _empty: Boolean? = null

    val wrapper: List<T> get() {
        if (_wrapper == null) {
            _wrapper = delegate.toList()
        }
        return _wrapper!!
    }

    override fun limit(n: Int, offset: Int): SizedIterable<T> = delegate.limit(n, offset)
    override operator fun iterator() = wrapper.iterator()
    override fun count() = _wrapper?.size ?: _count()
    override fun empty() = _wrapper?.isEmpty() ?: _empty()
    override fun forUpdate(): SizedIterable<T> = delegate.forUpdate()
    override fun notForUpdate(): SizedIterable<T> = delegate.notForUpdate()

    private fun _count(): Int {
        if (_size == null) {
            _size = delegate.count()
            _empty = (_size == 0)
        }
        return _size!!
    }

    private fun _empty(): Boolean {
        if (_empty == null) {
            _empty = delegate.empty()
            if (_empty == true) _size = 0
        }

        return _empty!!
    }
}

infix fun <T, R> SizedIterable<T>.mapLazy(f:(T)->R):SizedIterable<R> {
    val source = this
    return object : SizedIterable<R> {
        override fun limit(n: Int, offset: Int): SizedIterable<R> = source.limit(n, offset).mapLazy(f)
        override fun forUpdate(): SizedIterable<R> = source.forUpdate().mapLazy(f)
        override fun notForUpdate(): SizedIterable<R> = source.notForUpdate().mapLazy(f)
        override fun count(): Int = source.count()
        override fun empty(): Boolean = source.empty()

        override operator fun iterator(): Iterator<R> {
            val sourceIterator = source.iterator()
            return object: Iterator<R> {
                override operator fun next(): R = f(sourceIterator.next())

                override fun hasNext(): Boolean = sourceIterator.hasNext()
            }

        }
    }
}
