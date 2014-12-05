package kotlin.sql

public trait SizedIterable<out T>: Iterable<T> {
    fun count(): Int
    fun empty(): Boolean
}

fun <T> emptySized() : SizedIterable<T> {
    return EmptySizedIterable()
}

class EmptySizedIterable<T> : SizedIterable<T>, Iterator<T> {
    override fun count(): Int {
        return 0
    }

    override fun empty(): Boolean {
        return true
    }

    override fun iterator(): Iterator<T> {
        return this
    }

    override fun next(): T {
        throw UnsupportedOperationException()
    }

    override fun hasNext(): Boolean {
        return false;
    }
}

public class SizedCollection<out T>(val delegate: Collection<T>): SizedIterable<T> {
    override fun iterator() = delegate.iterator()
    override fun count() = delegate.size()
    override fun empty() = delegate.empty
}

public class LazySizedCollection<out T>(val delegate: SizedIterable<T>): SizedIterable<T> {
    private var _wrapper: List<T>? = null
    private var _size: Int? = null
    private var _empty: Boolean? = null

    val wrapper: List<T> get() {
        if (_wrapper == null) {
            _wrapper = delegate.toList()
        }
        return _wrapper!!
    }

    override fun iterator() = wrapper.iterator()
    override fun count() = _wrapper?.size() ?: _count()
    override fun empty() = _wrapper?.isEmpty() ?: _empty()

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

fun<T:Any> Iterable<T>.single() : T {
    var answer: T? = null;
    var found: Boolean = false;
    for (t in this) {
        if (found) error ("Duplicate items")

        answer = t;
        found = true;
    }

        if (!found) error ("No items found")
    return answer!!;
}

fun <T, R> SizedIterable<T>.mapLazy(f:(T)->R):SizedIterable<R> {
    val source = this
    return object : SizedIterable<R> {
        override fun count(): Int {
            return source.count()
        }

        override fun empty(): Boolean {
            return source.empty()
        }

        public override fun iterator(): Iterator<R> {
            val sourceIterator = source.iterator()
            return object: Iterator<R> {
                public override fun next(): R {
                    return f(sourceIterator.next())
                }

                public override fun hasNext(): Boolean {
                    return sourceIterator.hasNext()
                }
            }

        }
    }
}
