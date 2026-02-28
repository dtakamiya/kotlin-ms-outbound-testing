package com.example.orderservice.infrastructure.persistence

import com.example.orderservice.application.port.out.IdempotencyPort
import com.example.orderservice.application.port.out.IdempotencyRecord
import com.example.orderservice.application.port.out.IdempotencyStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * IdempotencyPort の JPA 実装
 *
 * 各メソッドは REQUIRES_NEW トランザクションで実行され、
 * 呼び出し元のトランザクションから独立して動作する。
 */
@Component
class IdempotencyPortImpl(
    private val jpaRepository: IdempotencyJpaRepository
) : IdempotencyPort {

    /**
     * 冪等性レコードを PROCESSING 状態で新規作成する。
     * 既に同一キーが存在する場合は false を返す。
     *
     * existsById でプリチェックし、競合時は DB の UNIQUE 制約で保護する。
     * REQUIRES_NEW トランザクション内で DataIntegrityViolationException が
     * 発生してもこのトランザクションのみがロールバックされる。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun tryCreate(
        idempotencyKey: String,
        requestFingerprint: String,
        expiresAt: Instant
    ): Boolean {
        if (jpaRepository.existsById(idempotencyKey)) {
            return false
        }
        return try {
            val entity = IdempotencyEntity(
                idempotencyKey = idempotencyKey,
                status = IdempotencyStatus.PROCESSING,
                requestFingerprint = requestFingerprint,
                createdAt = Instant.now(),
                expiresAt = expiresAt
            )
            jpaRepository.save(entity)
            jpaRepository.flush()
            true
        } catch (e: DataIntegrityViolationException) {
            false
        }
    }

    @Transactional(readOnly = true)
    override fun findByKey(idempotencyKey: String): IdempotencyRecord? {
        val entity = jpaRepository.findByIdOrNull(idempotencyKey) ?: return null
        return toRecord(entity)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun markCompleted(idempotencyKey: String, responseBody: String, responseStatus: Int) {
        val entity = jpaRepository.findByIdOrNull(idempotencyKey)
            ?: throw IllegalStateException("Idempotency record not found: $idempotencyKey")
        entity.status = IdempotencyStatus.COMPLETED
        entity.responseBody = responseBody
        entity.responseStatus = responseStatus
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun markFailed(idempotencyKey: String) {
        val entity = jpaRepository.findByIdOrNull(idempotencyKey)
            ?: throw IllegalStateException("Idempotency record not found: $idempotencyKey")
        entity.status = IdempotencyStatus.FAILED
    }

    @Transactional
    override fun deleteExpired(now: Instant): Int {
        return jpaRepository.deleteByExpiresAtBefore(now)
    }

    private fun toRecord(entity: IdempotencyEntity): IdempotencyRecord {
        return IdempotencyRecord(
            idempotencyKey = entity.idempotencyKey,
            status = entity.status,
            requestFingerprint = entity.requestFingerprint,
            responseBody = entity.responseBody,
            responseStatus = entity.responseStatus,
            createdAt = entity.createdAt,
            expiresAt = entity.expiresAt
        )
    }
}
