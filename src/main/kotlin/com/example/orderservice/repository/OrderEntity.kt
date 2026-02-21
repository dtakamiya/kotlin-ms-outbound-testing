package com.example.orderservice.repository

import com.example.orderservice.model.OrderStatus
import jakarta.persistence.*
import java.math.BigDecimal

/**
 * 注文エンティティ
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
