package com.example.orderservice.domain.repository

import com.example.orderservice.domain.model.Order
import com.example.orderservice.domain.model.OrderStatus

/**
 * 注文リポジトリのドメインインターフェース
 * 
 * 永続化の技術要件（JPA等）に依存せず、ドメインモデル（Order）の出し入れのみを定義します。
 */
interface OrderRepository {
    fun save(order: Order): Order
    fun findById(orderId: String): Order?
    fun findByCustomerId(customerId: String): List<Order>
    fun findByStatus(status: OrderStatus): List<Order>
}
