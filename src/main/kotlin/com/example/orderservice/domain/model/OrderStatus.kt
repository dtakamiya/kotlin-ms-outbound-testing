package com.example.orderservice.domain.model

/**
 * 注文ステータス
 */
enum class OrderStatus {
    CONFIRMED,
    OUT_OF_STOCK,
    PAYMENT_FAILED,
    ERROR
}
