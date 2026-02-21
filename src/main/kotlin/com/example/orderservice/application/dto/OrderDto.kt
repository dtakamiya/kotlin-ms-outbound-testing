package com.example.orderservice.application.dto

import com.example.orderservice.domain.model.OrderStatus
import java.math.BigDecimal

/**
 * 注文リクエストDTO
 */
data class OrderRequestDto(
    val productId: String,
    val quantity: Int,
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
