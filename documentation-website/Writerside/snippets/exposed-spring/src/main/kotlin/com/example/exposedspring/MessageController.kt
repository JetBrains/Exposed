package com.example.exposedspring

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/")
class MessageController(private val service: MessageService) {
    @GetMapping
    fun listMessages() = service.findMessages()

    // Read Message
    @GetMapping("/{id}")
    fun findMessageById(
        @PathVariable id: Long
    ): ResponseEntity<Message> {
        val message = service.findMessageById(MessageId(id))

        return if (message != null) {
            ResponseEntity.ok(message)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping
    fun post(@RequestBody form: MessageCreateRequestForm): ResponseEntity<MessageId> {
        val savedMessageId = service.save(form)
        return ResponseEntity.ok(MessageId(savedMessageId.value))
    }

    data class MessageCreateRequestForm(val text: String)
}