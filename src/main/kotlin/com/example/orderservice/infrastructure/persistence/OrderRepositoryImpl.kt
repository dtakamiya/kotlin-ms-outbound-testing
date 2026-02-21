package com.example.orderservice.infrastructure.persistence

import com.example.orderservice.domain.model.Order
import com.example.orderservice.domain.model.OrderStatus
import com.example.orderservice.domain.repository.OrderRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

/**
 * 注文リポジトリの実装クラス（アダプター）
 * ドメイン層の抽象をインフラ層（Spring Data JPA）で実現する。
 */
@Repository
class OrderRepositoryImpl(
    private val jpaRepository: OrderJpaRepository
) : OrderRepository {

    override fun save(order: Order): Order {
        val entity = toEntity(order)
        val savedEntity = jpaRepository.save(entity)
        return toDomain(savedEntity)
    }

    override fun findById(orderId: String): Order? {
        val entity = jpaRepository.findByIdOrNull(orderId)
        return entity?.let { toDomain(it) }
    }

    override fun findByCustomerId(customerId: String): List<Order> {
        return jpaRepository.findByCustomerId(customerId).map { toDomain(it) }
    }

    override fun findByStatus(status: OrderStatus): List<Order> {
        return jpaRepository.findByStatus(status).map { toDomain(it) }
    }

    // ドメインモデルとインフラエンティティのマッピング
    private fun toEntity(order: Order): OrderEntity {
        return OrderEntity(
            orderId = order.orderId,
            productId = order.productId,
            quantity = order.quantity,
            customerId = order.customerId,
            totalAmount = order.totalAmount,
            status = order.status
        )
    }

    private fun toDomain(entity: OrderEntity): Order {
        return Order(
            orderId = entity.orderId,
            productId = entity.productId,
            quantity = entity.quantity,
            customerId = entity.customerId,
            totalAmount = entity.totalAmount,
            status = entity.status
        )
    }
}
