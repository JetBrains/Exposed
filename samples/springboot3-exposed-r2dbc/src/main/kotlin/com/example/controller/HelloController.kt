package com.example.controller

import com.example.bean.EmailUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.io.InputStream
import java.nio.file.Paths

private const val BYTE_SIZE: Int = 8192

@RestController
@RequestMapping(value = ["/hello"])
internal final class HelloController(
    private val emailUser: EmailUser
) {
    internal final val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    @GetMapping(value = ["/hello"])
    internal final suspend fun hello(): String {
        delay(timeMillis = 100)
        logger.info("HELLO WORLD emailUser: $emailUser")
        return "Hello World!"
    }

    @GetMapping(value = ["/download"])
    internal final suspend fun download(
        @RequestHeader headers: Map<String, String>,
        response: ServerHttpResponse,
        @RequestParam(value = "fileName") fileName: String
    ) {
        logger.info("download headers: $headers, fileName: $fileName")
        val file: File = Paths.get("uploads/$fileName").toFile()
        response.headers.apply {
            contentType = MediaType.APPLICATION_OCTET_STREAM
            contentLength = file.length()
            set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$fileName")
        }
        val flow = flow {
            file.inputStream().use { inputStream: InputStream ->
                var length = 0
                val buffer = ByteArray(BYTE_SIZE)
                while ((inputStream.read(buffer).also { length = it }) > 0) {
                    emit(value = response.bufferFactory().wrap(buffer.copyOf(length)))
                }
            }
        }.flowOn(Dispatchers.IO)

        response.writeWith(flow.asPublisher()).awaitSingleOrNull()
    }
}
