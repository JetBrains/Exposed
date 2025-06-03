@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain.comment

class CommentService(
    private val commentRepository: CommentRepository
) {
    suspend fun createComment(comment: Comment): Comment {
        return commentRepository.save(comment)
    }

    suspend fun editComment(comment: Comment): Comment {
        return commentRepository.update(comment)
    }

    suspend fun removeComment(id: Long): Boolean {
        return commentRepository.delete(CommentId(id))
    }
}
