package com.example.orderservice.presentation.controller

import com.example.orderservice.application.dto.OrderRequestDto
import com.example.orderservice.application.dto.OrderResponseDto
import com.example.orderservice.application.service.IdempotentOrderService
import com.example.orderservice.application.service.IdempotentResult
import com.example.orderservice.domain.model.OrderStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal

@WebMvcTest(OrderController::class)
class OrderControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var idempotentOrderService: IdempotentOrderService

    private val sampleRequest = OrderRequestDto(
        productId = "PROD-100",
        quantity = 3,
        customerId = "CUST-100"
    )

    private val sampleResponse = OrderResponseDto(
        orderId = "ORDER-888",
        productId = "PROD-100",
        quantity = 3,
        customerId = "CUST-100",
        totalAmount = BigDecimal("4500.00"),
        status = OrderStatus.CONFIRMED
    )

    @Nested
    inner class `ヘッダーなし_後方互換性` {
        @Test
        fun `正常なリクエストの場合_200 OKと注文レスポンスが返ること`() {
            every { idempotentOrderService.createOrder(any(), null) } returns
                    IdempotentResult.Fresh(sampleResponse)

            mockMvc.post("/api/orders") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(sampleRequest)
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.orderId") { value("ORDER-888") }
                jsonPath("$.productId") { value("PROD-100") }
                jsonPath("$.status") { value("CONFIRMED") }
            }

            verify(exactly = 1) {
                idempotentOrderService.createOrder(
                    match { it.productId == "PROD-100" && it.quantity == 3 },
                    null
                )
            }
        }

        @Test
        fun `数量が0などの不正なリクエストの場合_400 Bad Requestが返ること`() {
            val request = OrderRequestDto(
                productId = "",
                quantity = 0,
                customerId = ""
            )

            mockMvc.post("/api/orders") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.status") { value(400) }
                jsonPath("$.message") { value("Validation failed") }
                jsonPath("$.details.productId") { value("productId must not be blank") }
                jsonPath("$.details.quantity") { value("quantity must be at least 1") }
                jsonPath("$.details.customerId") { value("customerId must not be blank") }
            }

            verify(exactly = 0) {
                idempotentOrderService.createOrder(any(), any())
            }
        }
    }

    @Nested
    inner class `Idempotency-Key付きリクエスト` {
        @Test
        fun `初回リクエスト_200 OKとIdempotency-Keyヘッダーがエコーバックされること`() {
            every { idempotentOrderService.createOrder(any(), "test-key-001") } returns
                    IdempotentResult.Fresh(sampleResponse)

            mockMvc.post("/api/orders") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(sampleRequest)
                accept = MediaType.APPLICATION_JSON
                header("Idempotency-Key", "test-key-001")
            }.andExpect {
                status { isOk() }
                header { string("Idempotency-Key", "test-key-001") }
                jsonPath("$.orderId") { value("ORDER-888") }
            }
        }

        @Test
        fun `キャッシュヒット_200 OKとキャッシュレスポンスが返ること`() {
            every { idempotentOrderService.createOrder(any(), "cached-key") } returns
                    IdempotentResult.Cached(sampleResponse, 200)

            mockMvc.post("/api/orders") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(sampleRequest)
                accept = MediaType.APPLICATION_JSON
                header("Idempotency-Key", "cached-key")
            }.andExpect {
                status { isOk() }
                header { string("Idempotency-Key", "cached-key") }
                jsonPath("$.orderId") { value("ORDER-888") }
            }
        }

        @Test
        fun `処理中のキー_409 ConflictとRetry-Afterヘッダーが返ること`() {
            every { idempotentOrderService.createOrder(any(), "processing-key") } returns
                    IdempotentResult.Conflict

            mockMvc.post("/api/orders") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(sampleRequest)
                accept = MediaType.APPLICATION_JSON
                header("Idempotency-Key", "processing-key")
            }.andExpect {
                status { isConflict() }
                header { string("Retry-After", "1") }
                header { string("Idempotency-Key", "processing-key") }
                jsonPath("$.status") { value(409) }
                jsonPath("$.message") { value("A request with this Idempotency-Key is currently being processed") }
            }
        }

        @Test
        fun `ボディ不一致_422 Unprocessable Entityが返ること`() {
            every { idempotentOrderService.createOrder(any(), "mismatch-key") } returns
                    IdempotentResult.FingerprintMismatch

            mockMvc.post("/api/orders") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(sampleRequest)
                accept = MediaType.APPLICATION_JSON
                header("Idempotency-Key", "mismatch-key")
            }.andExpect {
                status { isUnprocessableEntity() }
                header { string("Idempotency-Key", "mismatch-key") }
                jsonPath("$.status") { value(422) }
            }
        }

        @Test
        fun `キー長超過_400 Bad Requestが返ること`() {
            val longKey = "a".repeat(257)

            mockMvc.post("/api/orders") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(sampleRequest)
                accept = MediaType.APPLICATION_JSON
                header("Idempotency-Key", longKey)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.status") { value(400) }
                jsonPath("$.message") { value("Idempotency-Key must be 1-256 alphanumeric characters, hyphens, underscores, dots, or colons") }
            }

            verify(exactly = 0) {
                idempotentOrderService.createOrder(any(), any())
            }
        }
    }
}
