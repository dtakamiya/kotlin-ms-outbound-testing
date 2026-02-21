package com.example.orderservice.application.port.out

import java.math.BigDecimal

enum class PaymentResultStatus {
    SUCCESS,
    FAILED,
    PENDING
}

/**
 * 決済結果
 */
data class PaymentResult(
    val status: PaymentResultStatus,
    val transactionId: String?
)

/**
 * 決済サービス操作用ポート（出力ポート）
 */
interface PaymentPort {
    fun processPayment(orderId: String, customerId: String, amount: BigDecimal): PaymentResult
}
