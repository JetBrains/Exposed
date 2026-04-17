package org.jetbrains.exposed.r2dbc.dao.relationships

import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.v1.core.Column
import kotlin.reflect.KProperty

class R2dbcBackReference<ParentID : Any, out Parent : R2dbcEntity<ParentID>, ChildID : Any, in Child : R2dbcEntity<ChildID>, REF>(
    reference: Column<REF>,
    factory: R2dbcEntityClass<ParentID, Parent>
) {
    internal val delegate = R2dbcReferrers<ChildID, Child, ParentID, Parent, REF>(
        reference,
        factory,
        cache = true
    )

    operator fun getValue(thisRef: Child, property: KProperty<*>): suspend () -> Parent {
        thisRef.id.value

        val referrersLambda = delegate.getValue(thisRef, property)

        return suspend {
            val referrers = referrersLambda()
            referrers.single()
        }
    }
}

class R2dbcOptionalBackReference<ParentID : Any, out Parent : R2dbcEntity<ParentID>, ChildID : Any, in Child : R2dbcEntity<ChildID>, REF>(
    reference: Column<REF?>,
    factory: R2dbcEntityClass<ParentID, Parent>
) {
    internal val delegate = R2dbcReferrers<ChildID, Child, ParentID, Parent, REF?>(
        reference,
        factory,
        cache = true
    )

    operator fun getValue(thisRef: Child, property: KProperty<*>): suspend () -> Parent? {
        thisRef.id.value

        val referrersLambda = delegate.getValue(thisRef, property)

        return suspend {
            val referrers = referrersLambda()
            referrers.singleOrNull()
        }
    }
}
