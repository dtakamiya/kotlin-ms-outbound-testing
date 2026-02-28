package com.example.orderservice.infrastructure.persistence

import com.example.orderservice.application.port.out.IdempotencyStatus
import jakarta.persistence.*
import org.springframework.data.domain.Persistable
import java.time.Instant

/**
 * 冪等性キー管理エンティティ（インフラストラクチャ層・DBマッピング用）
 */
@Entity
@Table(name = "idempotency_keys")
class IdempotencyEntity(
    @Id
    val idempotencyKey: String,

    @Enumerated(EnumType.STRING)
    var status: IdempotencyStatus,

    val requestFingerprint: String,

    @Column(length = 10000)
    var responseBody: String? = null,

    var responseStatus: Int? = null,

    val createdAt: Instant,

    val expiresAt: Instant
) : Persistable<String> {

    @Transient
    private var _isNew: Boolean = true

    override fun getId(): String = idempotencyKey

    override fun isNew(): Boolean = _isNew

    @PostPersist
    @PostLoad
    fun markNotNew() {
        _isNew = false
    }
}
