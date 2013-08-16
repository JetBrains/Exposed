package kotlin.sql

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

fun <T, R> Iterable<T>.mapLazy(f:(T)->R):Iterable<R> {
    val source = this
    return object : Iterable<R> {
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
