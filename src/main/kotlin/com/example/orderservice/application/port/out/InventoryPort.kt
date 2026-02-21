package com.example.orderservice.application.port.out

import java.math.BigDecimal

/**
 * 在庫確認結果
 */
data class InventoryResult(
    val available: Boolean,
    val quantity: Int,
    val unitPrice: BigDecimal
)

/**
 * 在庫サービス操作用ポート（出力ポート）
 */
interface InventoryPort {
    fun checkInventory(productId: String): InventoryResult
}
