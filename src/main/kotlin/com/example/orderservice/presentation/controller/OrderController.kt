package com.example.orderservice.presentation.controller

import com.example.orderservice.application.dto.OrderRequestDto
import com.example.orderservice.application.dto.OrderResponseDto
import com.example.orderservice.application.service.IdempotentOrderService
import com.example.orderservice.application.service.IdempotentResult
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid

/**
 * 注文 REST API コントローラー
 */
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val idempotentOrderService: IdempotentOrderService
) {

    companion object {
        private const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        private const val MAX_KEY_LENGTH = 256
        private val VALID_KEY_PATTERN = Regex("^[a-zA-Z0-9\\-_.:]+$")
    }

    /**
     * 注文を作成する
     */
    @PostMapping
    fun createOrder(
        @Valid @RequestBody request: OrderRequestDto,
        @RequestHeader(IDEMPOTENCY_KEY_HEADER, required = false) idempotencyKey: String?
    ): ResponseEntity<Any> {
        // キーバリデーション
        if (idempotencyKey != null &&
            (idempotencyKey.isBlank() || idempotencyKey.length > MAX_KEY_LENGTH || !VALID_KEY_PATTERN.matches(idempotencyKey))
        ) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "status" to 400,
                    "error" to "Bad Request",
                    "message" to "Idempotency-Key must be 1-$MAX_KEY_LENGTH alphanumeric characters, hyphens, underscores, dots, or colons"
                )
            )
        }

        val result = idempotentOrderService.createOrder(request, idempotencyKey)

        return when (result) {
            is IdempotentResult.Fresh -> {
                buildResponse(HttpStatus.OK, result.data, idempotencyKey)
            }

            is IdempotentResult.Cached -> {
                buildResponse(HttpStatus.OK, result.data, idempotencyKey)
            }

            is IdempotentResult.Conflict -> {
                ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("Retry-After", "1")
                    .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                    .body(
                        mapOf(
                            "status" to 409,
                            "error" to "Conflict",
                            "message" to "A request with this Idempotency-Key is currently being processed"
                        )
                    )
            }

            is IdempotentResult.FingerprintMismatch -> {
                ResponseEntity.unprocessableEntity()
                    .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                    .body(
                        mapOf(
                            "status" to 422,
                            "error" to "Unprocessable Entity",
                            "message" to "Idempotency-Key was already used with a different request body"
                        )
                    )
            }
        }
    }

    private fun buildResponse(
        status: HttpStatus,
        body: OrderResponseDto,
        idempotencyKey: String?
    ): ResponseEntity<Any> {
        val builder = ResponseEntity.status(status)
        if (idempotencyKey != null) {
            builder.header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
        }
        return builder.body(body)
    }
}
