package org.jetbrains.exposed.sql.statements

open class BatchReplaceExecutable(
    override val statement: BatchReplaceStatement
) : BaseBatchInsertExecutable<BatchReplaceStatement>(statement)
