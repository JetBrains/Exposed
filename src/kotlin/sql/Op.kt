package kotlin.sql

open class Op : Expression {
    fun and(op: Op): Op {
        return AndOp(this, op)
    }

    fun or(op: Op): Op {
        return OrOp(this, op)
    }
}

class IsNullOp(val column: Column<*>): Op() {
    fun toString():String {
        return "${column} IS NULL"
    }
}

class LiteralOp(val value: Any): Op() {
    fun toString():String {
        return if (value is String) "'" + value + "'" else value.toString()
    }
}

class EqualsOp(val expr1: Expression, val expr2: Expression): Op() {
    fun toString():String {
        val sb = StringBuilder()
        if (expr1 is OrOp) {
            sb.append("(").append(expr1).append(")")
        } else {
            sb.append(expr1)
        }
        sb.append(" = ")
        if (expr2 is OrOp) {
            sb.append("(").append(expr2).append(")")
        } else {
            sb.append(expr2)
        }
        return sb.toString()
    }
}

class AndOp(val expr1: Expression, val expr2: Expression): Op() {
    fun toString():String {
        val sb = StringBuilder()
        if (expr1 is OrOp) {
            sb.append("(").append(expr1).append(")")
        } else {
            sb.append(expr1)
        }
        sb.append(" and ")
        if (expr2 is OrOp) {
            sb.append("(").append(expr2).append(")")
        } else {
            sb.append(expr2)
        }
        return sb.toString()
    }
}

class OrOp(val expr1: Expression, val expr2: Expression): Op() {
    fun toString():String {
        return expr1.toString() + " or " + expr2.toString()
    }
}