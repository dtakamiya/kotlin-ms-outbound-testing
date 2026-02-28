package com.example.orderservice.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

/**
 * 冪等性キーの Spring Data JPA リポジトリ
 */
interface IdempotencyJpaRepository : JpaRepository<IdempotencyEntity, String> {

    @Modifying
    @Query("DELETE FROM IdempotencyEntity e WHERE e.expiresAt < :now")
    fun deleteByExpiresAtBefore(now: Instant): Int
}
