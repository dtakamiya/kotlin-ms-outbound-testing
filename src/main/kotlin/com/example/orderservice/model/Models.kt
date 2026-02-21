package com.example.orderservice.model

import java.math.BigDecimal
import java.util.UUID

/**
 * 注文リクエスト
 */
data class OrderRequest(
    val productId: String,
    val quantity: Int,
    val customerId: String
)

/**
 * 注文レスポンス
 */
data class OrderResponse(
    val orderId: String,
    val productId: String,
    val quantity: Int,
    val customerId: String,
    val totalAmount: BigDecimal,
    val status: OrderStatus
)

/**
 * 注文ステータス
 */
enum class OrderStatus {
    CONFIRMED,
    OUT_OF_STOCK,
    PAYMENT_FAILED,
    ERROR
}

/**
 * 在庫確認レスポンス
 */
data class InventoryResponse(
    val productId: String,
    val productName: String,
    val available: Boolean,
    val quantity: Int,
    val unitPrice: BigDecimal
)

/**
 * 決済リクエスト
 */
data class PaymentRequest(
    val orderId: String,
    val customerId: String,
    val amount: BigDecimal,
    val currency: String = "JPY"
)

/**
 * 決済レスポンス
 */
data class PaymentResponse(
    val paymentId: String,
    val orderId: String,
    val status: PaymentStatus,
    val transactionId: String?
)

/**
 * 決済ステータス
 */
enum class PaymentStatus {
    SUCCESS,
    FAILED,
    PENDING
}
