package kotlin.sql

import kotlin.properties.Delegates

public trait SizedIterable<out T>: Iterable<T> {
    fun count(): Int
    fun empty(): Boolean
}

public class SizedCollection<out T>(val delegate: Collection<T>): SizedIterable<T> {
    override fun iterator() = delegate.iterator()
    override fun count() = delegate.size()
    override fun empty() = delegate.empty
}

public class LazySizedCollection<out T>(val delegate: SizedIterable<T>): SizedIterable<T> {
    var _wrapper: List<T>? = null
    val wrapper: List<T> get() {
        if (_wrapper == null) {
            _wrapper = delegate.toList()
        }
        return _wrapper!!
    }

    override fun iterator() = wrapper.iterator()
    override fun count() = _wrapper?.size() ?: delegate.count()
    override fun empty() = _wrapper?.isEmpty() ?: delegate.empty()
}

fun<T:Any> Iterable<T>.single() : T {
    var answer: T? = null;
    var found: Boolean = false;
    for (t in this) {
        if (found) throw RuntimeException ("Duplicate items")

        answer = t;
        found = true;
    }

    if (!found) throw RuntimeException ("No items found")
    return answer!!;
}

fun<T> Iterable<T>.any() : Boolean {
    for (t in this) {
        return true
    }
    return false
}

fun<T:Any> Iterable<T>.firstOrNull() : T? {
    for (t in this) {
        return t
    }
    return null
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
