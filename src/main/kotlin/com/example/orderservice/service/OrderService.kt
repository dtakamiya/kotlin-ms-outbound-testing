package com.example.orderservice.service

import com.example.orderservice.client.InventoryClient
import com.example.orderservice.client.PaymentClient
import com.example.orderservice.model.*
import com.example.orderservice.repository.OrderEntity
import com.example.orderservice.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

/**
 * 注文処理サービス
 *
 * 在庫確認 → 決済 → 注文確定の一連の処理を行う
 */
@Service
class OrderService(
    private val inventoryClient: InventoryClient,
    private val paymentClient: PaymentClient,
    private val orderRepository: OrderRepository
) {
    private val log = LoggerFactory.getLogger(OrderService::class.java)

    /**
     * 注文を作成する
     *
     * 1. 在庫サービスで在庫確認
     * 2. 決済サービスで決済処理
     * 3. 注文を確定して返却
     */
    fun createOrder(request: OrderRequest): OrderResponse {
        val orderId = UUID.randomUUID().toString()
        log.info("Creating order: {} for product: {}", orderId, request.productId)

        val saveOrder = { amount: BigDecimal, status: OrderStatus ->
            val entity = OrderEntity(
                orderId = orderId,
                productId = request.productId,
                quantity = request.quantity,
                customerId = request.customerId,
                totalAmount = amount,
                status = status
            )
            orderRepository.save(entity)
            OrderResponse(
                orderId = orderId,
                productId = request.productId,
                quantity = request.quantity,
                customerId = request.customerId,
                totalAmount = amount,
                status = status
            )
        }

        // 1. 在庫確認
        val inventory = try {
            inventoryClient.checkInventory(request.productId)
        } catch (e: Exception) {
            log.error("Failed to check inventory: {}", e.message)
            return saveOrder(BigDecimal.ZERO, OrderStatus.ERROR)
        }

        // 在庫なしの場合
        if (!inventory.available || inventory.quantity < request.quantity) {
            log.warn("Product {} is out of stock", request.productId)
            return saveOrder(BigDecimal.ZERO, OrderStatus.OUT_OF_STOCK)
        }

        // 2. 決済処理
        val totalAmount = inventory.unitPrice.multiply(BigDecimal(request.quantity))
        val paymentResponse = try {
            paymentClient.processPayment(
                PaymentRequest(
                    orderId = orderId,
                    customerId = request.customerId,
                    amount = totalAmount
                )
            )
        } catch (e: Exception) {
            log.error("Failed to process payment: {}", e.message)
            return saveOrder(totalAmount, OrderStatus.ERROR)
        }

        // 決済失敗の場合
        if (paymentResponse.status != PaymentStatus.SUCCESS) {
            log.warn("Payment failed for order: {}", orderId)
            return saveOrder(totalAmount, OrderStatus.PAYMENT_FAILED)
        }

        // 3. 注文確定
        log.info("Order confirmed: {}", orderId)
        return saveOrder(totalAmount, OrderStatus.CONFIRMED)
    }
}
