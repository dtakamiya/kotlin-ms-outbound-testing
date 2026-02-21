package com.example.orderservice.application.dto

import com.example.orderservice.domain.model.OrderStatus
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

/**
 * 注文リクエストDTO
 */
data class OrderRequestDto(
    @field:NotBlank(message = "productId must not be blank")
    val productId: String,
    @field:Min(value = 1, message = "quantity must be at least 1")
    val quantity: Int,
    @field:NotBlank(message = "customerId must not be blank")
    val customerId: String
)

/**
 * 注文レスポンスDTO
 */
data class OrderResponseDto(
    val orderId: String,
    val productId: String,
    val quantity: Int,
    val customerId: String,
    val totalAmount: BigDecimal,
    val status: OrderStatus
)
