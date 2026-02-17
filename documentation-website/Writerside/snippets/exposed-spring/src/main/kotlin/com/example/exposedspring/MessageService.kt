package com.example.exposedspring

import com.example.exposedspring.MessageController.MessageCreateRequestForm
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class MessageService {
    // read all messages
    fun findMessages(): List<Message> {
        return MessageEntity.selectAll().map {
            Message(
                id = MessageId(it[MessageEntity.id].value),
                text = it[MessageEntity.text]
            )
        }
    }

    // read a message by message primary key
    fun findMessageById(id: MessageId): Message? {
        // Use Exposed dsl without `transaction { }`
        return MessageEntity.selectAll().where { MessageEntity.id eq id.value }.firstOrNull()?.let {
            Message(
                id = MessageId(it[MessageEntity.id].value),
                text = it[MessageEntity.text]
            )
        }
    }

    fun save(message: MessageCreateRequestForm): MessageId {
        val id = MessageEntity.insertAndGetId {
            it[text] = message.text
        }

        return MessageId(id.value)
    }
}