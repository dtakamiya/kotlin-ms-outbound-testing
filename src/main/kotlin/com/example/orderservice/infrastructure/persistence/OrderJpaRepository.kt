package com.example.orderservice.infrastructure.persistence

import com.example.orderservice.domain.model.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA 用の注文リポジトリ
 */
@Repository
interface OrderJpaRepository : JpaRepository<OrderEntity, String> {
    fun findByCustomerId(customerId: String): List<OrderEntity>
    fun findByStatus(status: OrderStatus): List<OrderEntity>
}
