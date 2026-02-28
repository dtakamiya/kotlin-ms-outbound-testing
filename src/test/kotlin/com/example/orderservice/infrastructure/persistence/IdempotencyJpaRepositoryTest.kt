package com.example.orderservice.infrastructure.persistence

import com.example.orderservice.application.port.out.IdempotencyStatus
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.time.Instant
import java.time.temporal.ChronoUnit

@DataJpaTest
class IdempotencyJpaRepositoryTest {

    @Autowired
    private lateinit var repository: IdempotencyJpaRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `冪等性レコードを保存して検索できること`() {
        val entity = IdempotencyEntity(
            idempotencyKey = "test-key-001",
            status = IdempotencyStatus.PROCESSING,
            requestFingerprint = "sha256-fingerprint",
            createdAt = Instant.now(),
            expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
        )

        repository.saveAndFlush(entity)
        entityManager.clear()

        val found = repository.findById("test-key-001")

        found.shouldBePresent()
        found.get().status shouldBe IdempotencyStatus.PROCESSING
        found.get().requestFingerprint shouldBe "sha256-fingerprint"
    }

    @Test
    fun `同一キーで重複保存するとPersistenceExceptionが発生すること`() {
        val entity1 = IdempotencyEntity(
            idempotencyKey = "duplicate-key",
            status = IdempotencyStatus.PROCESSING,
            requestFingerprint = "fingerprint-1",
            createdAt = Instant.now(),
            expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
        )
        entityManager.persist(entity1)
        entityManager.flush()

        val entity2 = IdempotencyEntity(
            idempotencyKey = "duplicate-key",
            status = IdempotencyStatus.PROCESSING,
            requestFingerprint = "fingerprint-2",
            createdAt = Instant.now(),
            expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
        )

        assertThrows<PersistenceException> {
            entityManager.persist(entity2)
            entityManager.flush()
        }
    }

    @Test
    fun `期限切れレコードを削除できること`() {
        val expired = IdempotencyEntity(
            idempotencyKey = "expired-key",
            status = IdempotencyStatus.COMPLETED,
            requestFingerprint = "fp-expired",
            createdAt = Instant.now().minus(48, ChronoUnit.HOURS),
            expiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
        )
        val valid = IdempotencyEntity(
            idempotencyKey = "valid-key",
            status = IdempotencyStatus.COMPLETED,
            requestFingerprint = "fp-valid",
            createdAt = Instant.now(),
            expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
        )
        repository.saveAll(listOf(expired, valid))
        repository.flush()

        val deletedCount = repository.deleteByExpiresAtBefore(Instant.now())
        entityManager.flush()
        entityManager.clear()

        deletedCount shouldBe 1
        repository.findById("expired-key").isPresent shouldBe false
        repository.findById("valid-key").isPresent shouldBe true
    }

    @Test
    fun `ステータスを更新できること`() {
        val entity = IdempotencyEntity(
            idempotencyKey = "update-key",
            status = IdempotencyStatus.PROCESSING,
            requestFingerprint = "fp-update",
            createdAt = Instant.now(),
            expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
        )
        repository.saveAndFlush(entity)
        entityManager.clear()

        val loaded = repository.findById("update-key").get()
        loaded.status = IdempotencyStatus.COMPLETED
        loaded.responseBody = """{"orderId":"ORDER-001"}"""
        loaded.responseStatus = 200
        entityManager.flush()
        entityManager.clear()

        val found = repository.findById("update-key")
        found.shouldBePresent()
        found.get().status shouldBe IdempotencyStatus.COMPLETED
        found.get().responseBody shouldBe """{"orderId":"ORDER-001"}"""
        found.get().responseStatus shouldBe 200
    }
}
