package com.example.orderservice.application.service

import com.example.orderservice.application.dto.OrderRequestDto
import com.example.orderservice.application.dto.OrderResponseDto
import com.example.orderservice.application.port.out.IdempotencyPort
import com.example.orderservice.application.port.out.IdempotencyRecord
import com.example.orderservice.application.port.out.IdempotencyStatus
import com.example.orderservice.domain.model.OrderStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

@ExtendWith(MockKExtension::class)
class IdempotentOrderServiceUnitTest {

    @MockK
    private lateinit var orderApplicationService: OrderApplicationService

    @MockK
    private lateinit var idempotencyPort: IdempotencyPort

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private lateinit var idempotentOrderService: IdempotentOrderService

    private val sampleRequest = OrderRequestDto(
        productId = "PROD-001",
        quantity = 2,
        customerId = "CUST-001"
    )

    private val sampleResponse = OrderResponseDto(
        orderId = "ORDER-001",
        productId = "PROD-001",
        quantity = 2,
        customerId = "CUST-001",
        totalAmount = BigDecimal("3000.00"),
        status = OrderStatus.CONFIRMED
    )

    @BeforeEach
    fun setUp() {
        idempotentOrderService = IdempotentOrderService(
            orderApplicationService = orderApplicationService,
            idempotencyPort = idempotencyPort,
            objectMapper = objectMapper,
            ttlHours = 24
        )
    }

    @Nested
    inner class `キーなしリクエスト` {
        @Test
        fun `キーがnullの場合_通常の注文作成が実行されること`() {
            every { orderApplicationService.createOrder(any()) } returns sampleResponse

            val result = idempotentOrderService.createOrder(sampleRequest, null)

            result.shouldBeInstanceOf<IdempotentResult.Fresh<OrderResponseDto>>()
            result.data shouldBe sampleResponse
            verify(exactly = 0) { idempotencyPort.tryCreate(any(), any(), any()) }
        }
    }

    @Nested
    inner class `初回リクエスト` {
        @Test
        fun `PROCESSING作成後_処理実行_COMPLETEDに更新されること`() {
            every { idempotencyPort.tryCreate(any(), any(), any()) } returns true
            every { orderApplicationService.createOrder(any()) } returns sampleResponse
            every { idempotencyPort.markCompleted(any(), any(), any()) } just runs

            val result = idempotentOrderService.createOrder(sampleRequest, "key-001")

            result.shouldBeInstanceOf<IdempotentResult.Fresh<OrderResponseDto>>()
            result.data shouldBe sampleResponse
            verify(exactly = 1) { idempotencyPort.tryCreate("key-001", any(), any()) }
            verify(exactly = 1) { idempotencyPort.markCompleted("key-001", any(), 200) }
        }
    }

    @Nested
    inner class `完了済みキー` {
        @Test
        fun `キャッシュレスポンスが返却されること`() {
            val responseJson = objectMapper.writeValueAsString(sampleResponse)
            val fingerprint = computeFingerprint(sampleRequest)

            every { idempotencyPort.tryCreate(any(), any(), any()) } returns false
            every { idempotencyPort.findByKey("key-completed") } returns IdempotencyRecord(
                idempotencyKey = "key-completed",
                status = IdempotencyStatus.COMPLETED,
                requestFingerprint = fingerprint,
                responseBody = responseJson,
                responseStatus = 200,
                createdAt = Instant.now(),
                expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
            )

            val result = idempotentOrderService.createOrder(sampleRequest, "key-completed")

            result.shouldBeInstanceOf<IdempotentResult.Cached<OrderResponseDto>>()
            result.data.orderId shouldBe "ORDER-001"
            result.responseStatus shouldBe 200
            verify(exactly = 0) { orderApplicationService.createOrder(any()) }
        }
    }

    @Nested
    inner class `PROCESSING中のキー` {
        @Test
        fun `Conflictが返されること`() {
            val fingerprint = computeFingerprint(sampleRequest)

            every { idempotencyPort.tryCreate(any(), any(), any()) } returns false
            every { idempotencyPort.findByKey("key-processing") } returns IdempotencyRecord(
                idempotencyKey = "key-processing",
                status = IdempotencyStatus.PROCESSING,
                requestFingerprint = fingerprint,
                responseBody = null,
                responseStatus = null,
                createdAt = Instant.now(),
                expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
            )

            val result = idempotentOrderService.createOrder(sampleRequest, "key-processing")

            result.shouldBeInstanceOf<IdempotentResult.Conflict>()
            verify(exactly = 0) { orderApplicationService.createOrder(any()) }
        }
    }

    @Nested
    inner class `ボディ不一致` {
        @Test
        fun `FingerprintMismatchが返されること`() {
            every { idempotencyPort.tryCreate(any(), any(), any()) } returns false
            every { idempotencyPort.findByKey("key-mismatch") } returns IdempotencyRecord(
                idempotencyKey = "key-mismatch",
                status = IdempotencyStatus.COMPLETED,
                requestFingerprint = "different-fingerprint",
                responseBody = null,
                responseStatus = null,
                createdAt = Instant.now(),
                expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
            )

            val result = idempotentOrderService.createOrder(sampleRequest, "key-mismatch")

            result.shouldBeInstanceOf<IdempotentResult.FingerprintMismatch>()
            verify(exactly = 0) { orderApplicationService.createOrder(any()) }
        }
    }

    @Nested
    inner class `例外発生時` {
        @Test
        fun `FAILEDにマークされて例外が再スローされること`() {
            every { idempotencyPort.tryCreate(any(), any(), any()) } returns true
            every { orderApplicationService.createOrder(any()) } throws RuntimeException("Payment error")
            every { idempotencyPort.markFailed(any()) } just runs

            assertThrows<RuntimeException> {
                idempotentOrderService.createOrder(sampleRequest, "key-error")
            }

            verify(exactly = 1) { idempotencyPort.markFailed("key-error") }
        }
    }

    @Nested
    inner class `FAILEDキーの再試行` {
        @Test
        fun `再処理が実行されること`() {
            val fingerprint = computeFingerprint(sampleRequest)

            every { idempotencyPort.tryCreate(any(), any(), any()) } returns false
            every { idempotencyPort.findByKey("key-failed") } returns IdempotencyRecord(
                idempotencyKey = "key-failed",
                status = IdempotencyStatus.FAILED,
                requestFingerprint = fingerprint,
                responseBody = null,
                responseStatus = null,
                createdAt = Instant.now(),
                expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
            )
            every { orderApplicationService.createOrder(any()) } returns sampleResponse
            every { idempotencyPort.markCompleted(any(), any(), any()) } just runs

            val result = idempotentOrderService.createOrder(sampleRequest, "key-failed")

            result.shouldBeInstanceOf<IdempotentResult.Fresh<OrderResponseDto>>()
            result.data shouldBe sampleResponse
            verify(exactly = 1) { orderApplicationService.createOrder(any()) }
            verify(exactly = 1) { idempotencyPort.markCompleted("key-failed", any(), 200) }
        }
    }

    private fun computeFingerprint(request: OrderRequestDto): String {
        val json = objectMapper.writeValueAsString(request)
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(json.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
