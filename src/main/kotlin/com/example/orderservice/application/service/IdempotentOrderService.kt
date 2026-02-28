package com.example.orderservice.application.service

import com.example.orderservice.application.dto.OrderRequestDto
import com.example.orderservice.application.dto.OrderResponseDto
import com.example.orderservice.application.port.out.IdempotencyPort
import com.example.orderservice.application.port.out.IdempotencyStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 冪等性チェックの結果
 */
sealed class IdempotentResult<out T> {
    data class Fresh<T>(val data: T) : IdempotentResult<T>()
    data class Cached<T>(val data: T, val responseStatus: Int) : IdempotentResult<T>()
    data object Conflict : IdempotentResult<Nothing>()
    data object FingerprintMismatch : IdempotentResult<Nothing>()
}

/**
 * OrderApplicationService を冪等性でラップするデコレータサービス
 */
@Service
class IdempotentOrderService(
    private val orderApplicationService: OrderApplicationService,
    private val idempotencyPort: IdempotencyPort,
    private val objectMapper: ObjectMapper,
    @Value("\${idempotency.ttl-hours:24}")
    private val ttlHours: Long = 24
) {
    private val log = LoggerFactory.getLogger(IdempotentOrderService::class.java)

    /**
     * 冪等性キー付きで注文を作成する。
     * キーが null の場合は通常の注文作成を実行する。
     */
    fun createOrder(
        request: OrderRequestDto,
        idempotencyKey: String?
    ): IdempotentResult<OrderResponseDto> {
        if (idempotencyKey == null) {
            val response = orderApplicationService.createOrder(request)
            return IdempotentResult.Fresh(response)
        }

        val fingerprint = computeFingerprint(request)

        // 新規レコード作成を試みる
        val created = idempotencyPort.tryCreate(
            idempotencyKey = idempotencyKey,
            requestFingerprint = fingerprint,
            expiresAt = Instant.now().plus(ttlHours, ChronoUnit.HOURS)
        )

        if (created) {
            return executeAndRecord(idempotencyKey, request)
        }

        // 既存レコードを取得
        val existing = idempotencyPort.findByKey(idempotencyKey)
            ?: run {
                // tryCreateがfalseだがレコードが見つからない（期限切れ等で削除された可能性）
                // 冪等性トラッキングを再確立するため、再度tryCreateを試みる
                log.warn("Idempotency record not found after create failure, retrying: {}", idempotencyKey)
                val retryCreated = idempotencyPort.tryCreate(
                    idempotencyKey = idempotencyKey,
                    requestFingerprint = fingerprint,
                    expiresAt = Instant.now().plus(ttlHours, ChronoUnit.HOURS)
                )
                if (retryCreated) {
                    return executeAndRecord(idempotencyKey, request)
                }
                return IdempotentResult.Conflict
            }

        // フィンガープリント不一致チェック
        if (existing.requestFingerprint != fingerprint) {
            log.warn("Fingerprint mismatch for key: {}", idempotencyKey)
            return IdempotentResult.FingerprintMismatch
        }

        return when (existing.status) {
            IdempotencyStatus.COMPLETED -> {
                val body = existing.responseBody
                if (body == null) {
                    log.error("Completed idempotency record has null response body: {}", idempotencyKey)
                    idempotencyPort.markFailed(idempotencyKey)
                    return executeAndRecord(idempotencyKey, request)
                }
                log.info("Returning cached response for key: {}", idempotencyKey)
                val cachedResponse = objectMapper.readValue(body, OrderResponseDto::class.java)
                IdempotentResult.Cached(cachedResponse, existing.responseStatus ?: 200)
            }

            IdempotencyStatus.PROCESSING -> {
                log.info("Request still processing for key: {}", idempotencyKey)
                IdempotentResult.Conflict
            }

            IdempotencyStatus.FAILED -> {
                log.info("Retrying failed request for key: {}", idempotencyKey)
                executeAndRecord(idempotencyKey, request)
            }
        }
    }

    private fun executeAndRecord(
        idempotencyKey: String,
        request: OrderRequestDto
    ): IdempotentResult<OrderResponseDto> {
        return try {
            val response = orderApplicationService.createOrder(request)
            val responseJson = objectMapper.writeValueAsString(response)
            idempotencyPort.markCompleted(idempotencyKey, responseJson, 200)
            IdempotentResult.Fresh(response)
        } catch (e: Exception) {
            log.error("Order creation failed, marking idempotency key as FAILED: {}", idempotencyKey, e)
            idempotencyPort.markFailed(idempotencyKey)
            throw e
        }
    }

    private fun computeFingerprint(request: OrderRequestDto): String {
        val json = objectMapper.writeValueAsString(request)
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(json.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
