package com.example.orderservice.infrastructure.scheduler

import com.example.orderservice.application.port.out.IdempotencyPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 期限切れの冪等性レコードを定期的に削除するスケジューラー
 */
@Component
class IdempotencyCleanupScheduler(
    private val idempotencyPort: IdempotencyPort
) {
    private val log = LoggerFactory.getLogger(IdempotencyCleanupScheduler::class.java)

    @Scheduled(cron = "\${idempotency.cleanup-cron:0 0 * * * *}")
    fun cleanupExpiredRecords() {
        try {
            val deletedCount = idempotencyPort.deleteExpired(Instant.now())
            if (deletedCount > 0) {
                log.info("Deleted {} expired idempotency records", deletedCount)
            }
        } catch (e: Exception) {
            log.error("Failed to cleanup expired idempotency records", e)
        }
    }
}
