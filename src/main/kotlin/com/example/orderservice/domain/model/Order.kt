package com.example.orderservice.domain.model

import java.math.BigDecimal

/**
 * 注文ドメインエンティティ
 * 
 * インフラ層のリポジトリ（Entity）とは異なり、ビジネスロジック・振る舞いに関する知識を持ちます。
 */
class Order(
    val orderId: String,
    val productId: String,
    val quantity: Int,
    val customerId: String,
    var totalAmount: BigDecimal,
    var status: OrderStatus
) {
    // 状態遷移の振る舞い

    /**
     * 在庫切れとしてマークします
     */
    fun markAsOutOfStock() {
        this.status = OrderStatus.OUT_OF_STOCK
    }

    /**
     * 決済成功として注文を確定します
     */
    fun confirm(totalAmount: BigDecimal) {
        this.totalAmount = totalAmount
        this.status = OrderStatus.CONFIRMED
    }

    /**
     * 決済失敗としてマークします
     */
    fun markPaymentFailed(totalAmount: BigDecimal) {
        this.totalAmount = totalAmount
        this.status = OrderStatus.PAYMENT_FAILED
    }

    /**
     * エラーとしてマークします
     */
    fun markAsError(totalAmount: BigDecimal) {
        this.totalAmount = totalAmount
        this.status = OrderStatus.ERROR
    }
}
