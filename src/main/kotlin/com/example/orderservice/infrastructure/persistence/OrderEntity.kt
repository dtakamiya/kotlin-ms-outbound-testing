package com.example.orderservice.infrastructure.persistence

import com.example.orderservice.domain.model.OrderStatus
import jakarta.persistence.*
import java.math.BigDecimal

/**
 * 注文エンティティ（インフラストラクチャ層・DBマッピング用）
 */
@Entity
@Table(name = "orders")
class OrderEntity(
    @Id
    val orderId: String,
    
    val productId: String,
    
    val quantity: Int,
    
    val customerId: String,
    
    val totalAmount: BigDecimal,
    
    @Enumerated(EnumType.STRING)
    var status: OrderStatus
)
