package com.example.exposedspring

data class Message(val id: MessageId, val text: String)

@JvmInline
value class MessageId(val value: Long)