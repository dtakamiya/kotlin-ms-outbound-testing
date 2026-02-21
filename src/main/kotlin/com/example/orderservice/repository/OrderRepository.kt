package com.example.orderservice.repository

import com.example.orderservice.model.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 注文リポジトリ
 */
@Repository
interface OrderRepository : JpaRepository<OrderEntity, String> {
    fun findByCustomerId(customerId: String): List<OrderEntity>
    fun findByStatus(status: OrderStatus): List<OrderEntity>
}
