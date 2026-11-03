@file:Suppress("MagicNumber")

package org.jetbrains.exposed.samples.springdata.nativeimage

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.spring.data.event.BeforeSaveCallback
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.time.temporal.TemporalAccessor
import java.util.Optional
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

// ---- customers: CRUD, derived and paged queries, projections, query { }, entity callback ----

object Customers : LongIdTable("customers") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val age = integer("age")
}

data class Customer(
    val id: Long? = null,
    val name: String,
    val email: String,
    val age: Int,
)

/** DTO projection, found in a repository method's return type. */
data class CustomerName(val name: String)

/** Interface projection, found in a repository method's return type. */
interface CustomerView {
    val name: String
    val age: Int
}

/** DTO projection that is only used dynamically (`Class<T>` argument). */
data class CustomerAge(val age: Int)

/** Lower-cases the email of every saved customer and counts its invocations. */
class EmailNormalizingCallback : BeforeSaveCallback<Customer> {
    val invocations = AtomicInteger()

    override fun onBeforeSave(entity: Customer, table: Table): Customer {
        invocations.incrementAndGet()
        return entity.copy(email = entity.email.lowercase())
    }
}

// ---- notes: auditing ----

object Notes : LongIdTable("notes") {
    val text = varchar("text", 255)
    val createdAt = timestamp("created_at").nullable()
    val modifiedAt = timestamp("modified_at").nullable()
    val createdBy = varchar("created_by", 64).nullable()
    val modifiedBy = varchar("modified_by", 64).nullable()
}

data class Note(
    val id: Long? = null,
    val text: String,
    @CreatedDate val createdAt: Instant? = null,
    @LastModifiedDate val modifiedAt: Instant? = null,
    @CreatedBy val createdBy: String? = null,
    @LastModifiedBy val modifiedBy: String? = null,
)

class SampleAuditor : AuditorAware<String> {
    @Volatile
    var current: String? = "alice"

    override fun getCurrentAuditor(): Optional<String> = Optional.ofNullable(current)
}

class SampleClock : DateTimeProvider {
    @Volatile
    var now: Instant = Instant.parse("2025-03-01T10:15:30.123Z")

    override fun getNow(): Optional<TemporalAccessor> = Optional.of(now)
}

// ---- articles: Persistable with a private, application-assigned id ----

object Articles : UUIDTable("articles") {
    val title = varchar("title", 255)
}

data class Article(
    private val id: UUID?,
    val title: String,
    private val newEntity: Boolean = false,
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = newEntity

    companion object {
        fun create(title: String, id: UUID = UUID.randomUUID()) = Article(id = id, title = title, newEntity = true)
    }
}
