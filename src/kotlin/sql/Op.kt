package kotlin.sql

abstract class Op<T>() : Expression<T> {
    fun and(op: Expression<T>): Op<Boolean> {
        return AndOp(this, op)
    }

    fun or(op: Expression<T>): Op<Boolean> {
        return OrOp(this, op)
    }
}

class IsNullOp(val column: Column<*>): Op<Boolean>() {
    override fun toSQL():String {
        return "${Session.get().fullIdentity(column)} IS NULL"
    }
}

class IsNotNullOp(val column: Column<*>): Op<Boolean>() {
    override fun toSQL():String {
        return "${Session.get().fullIdentity(column)} IS NOT NULL"
    }
}

class LiteralOp<T>(val columnType: ColumnType, val value: Any): Expression<T> {
    override fun toSQL():String {
        return columnType.valueToString(value)
    }
}

fun intLiteral(value: Int) : LiteralOp<Int> = LiteralOp<Int> (IntegerColumnType(), value)
fun longLiteral(value: Long) : LiteralOp<Long> = LiteralOp<Long>(LongColumnType(), value)
fun stringLiteral(value: String) : LiteralOp<String> = LiteralOp<String>(StringColumnType(), value)

abstract class ComparisonOp<out T>(val expr1: Expression<T>, val expr2: Expression<T>, val opSign: String): Op<Boolean>() {
    override fun toSQL():String {
        val sb = StringBuilder()
        if (expr1 is OrOp<*>) {
            sb.append("(").append(expr1.toSQL()).append(")")
        } else {
            sb.append(expr1.toSQL())
        }
        sb.append(" $opSign ")
        if (expr2 is OrOp<*>) {
            sb.append("(").append(expr2.toSQL()).append(")")
        } else {
            sb.append(expr2.toSQL())
        }
        return sb.toString()
    }
}

class EqOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, "=") {
}

class NeqOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, "<>") {
}

class LessOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, "<") {
}

class LessEqOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, "<=") {
}

class GreaterOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, ">") {
}

class GreaterEqOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, ">=") {
}

class LikeOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, "LIKE") {
}

class AndOp<out T>(val expr1: Expression<T>, val expr2: Expression<T>): Op<Boolean>() {
    override fun toSQL():String {
        val sb = StringBuilder()
        if (expr1 is OrOp<*>) {
            sb.append("(").append(expr1.toSQL()).append(")")
        } else {
            sb.append(expr1.toSQL())
        }
        sb.append(" and ")
        if (expr2 is OrOp<*>) {
            sb.append("(").append(expr2.toSQL()).append(")")
        } else {
            sb.append(expr2.toSQL())
        }
        return sb.toString()
    }
}

class OrOp<out T>(val expr1: Expression<T>, val expr2: Expression<T>): Op<Boolean>() {
    override fun toSQL():String {
        return expr1.toSQL() + " or " + expr2.toSQL()
    }
}
