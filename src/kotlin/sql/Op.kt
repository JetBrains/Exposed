package kotlin.sql

abstract class Op() : Expression {
    fun and(op: Op): Op {
        return AndOp(this, op)
    }

    fun or(op: Op): Op {
        return OrOp(this, op)
    }
}

class IsNullOp(val column: Column<*>): Op() {
    override fun toSQL():String {
        return "${Session.get().fullIdentity(column)} IS NULL"
    }
}

class IsNotNullOp(val column: Column<*>): Op() {
    override fun toSQL():String {
        return "${Session.get().fullIdentity(column)} IS NOT NULL"
    }
}

class LiteralOp(val columnType: ColumnType, val value: Any): Op() {
    override fun toSQL():String {
        return columnType.valueToString(value)
    }
}

abstract class ComparisonOp(val expr1: Expression, val expr2: Expression, val opSign: String): Op() {
    override fun toSQL():String {
        val sb = StringBuilder()
        if (expr1 is OrOp) {
            sb.append("(").append(expr1.toSQL()).append(")")
        } else {
            sb.append(expr1.toSQL())
        }
        sb.append(" $opSign ")
        if (expr2 is OrOp) {
            sb.append("(").append(expr2.toSQL()).append(")")
        } else {
            sb.append(expr2.toSQL())
        }
        return sb.toString()
    }
}

class EqOp(expr1: Expression, expr2: Expression): ComparisonOp(expr1, expr2, "=") {
}

class NeqOp(expr1: Expression, expr2: Expression): ComparisonOp(expr1, expr2, "<>") {
}

class LessOp(expr1: Expression, expr2: Expression): ComparisonOp(expr1, expr2, "<") {
}

class LessEqOp(expr1: Expression, expr2: Expression): ComparisonOp(expr1, expr2, "<=") {
}

class GreaterOp(expr1: Expression, expr2: Expression): ComparisonOp(expr1, expr2, ">") {
}

class GreaterEqOp(expr1: Expression, expr2: Expression): ComparisonOp(expr1, expr2, ">=") {
}

class LikeOp(expr1: Expression, expr2: Expression): ComparisonOp(expr1, expr2, "LIKE") {
}

class AndOp(val expr1: Expression, val expr2: Expression): Op() {
    override fun toSQL():String {
        val sb = StringBuilder()
        if (expr1 is OrOp) {
            sb.append("(").append(expr1.toSQL()).append(")")
        } else {
            sb.append(expr1.toSQL())
        }
        sb.append(" and ")
        if (expr2 is OrOp) {
            sb.append("(").append(expr2.toSQL()).append(")")
        } else {
            sb.append(expr2.toSQL())
        }
        return sb.toString()
    }
}

class OrOp(val expr1: Expression, val expr2: Expression): Op() {
    override fun toSQL():String {
        return expr1.toSQL() + " or " + expr2.toSQL()
    }
}
