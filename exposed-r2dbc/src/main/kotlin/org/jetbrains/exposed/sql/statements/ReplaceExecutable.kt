package org.jetbrains.exposed.sql.statements

open class ReplaceExecutable<Key : Any>(
    override val statement: ReplaceStatement<Key>
) : InsertExecutable<Key, ReplaceStatement<Key>>(statement)

open class ReplaceSelectExecutable(
    override val statement: ReplaceSelectStatement
) : InsertSelectExecutable(statement)
