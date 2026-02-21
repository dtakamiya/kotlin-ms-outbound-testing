package com.example.orderservice.application.service

import com.example.orderservice.application.dto.OrderRequestDto
import com.example.orderservice.application.dto.OrderResponseDto
import com.example.orderservice.application.port.out.InventoryPort
import com.example.orderservice.application.port.out.PaymentPort
import com.example.orderservice.application.port.out.PaymentResultStatus
import com.example.orderservice.domain.model.Order
import com.example.orderservice.domain.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

/**
 * 注文処理のユースケースを実現するアプリケーションサービス
 */
@Service
class OrderApplicationService(
    private val inventoryPort: InventoryPort,
    private val paymentPort: PaymentPort,
    private val orderRepository: OrderRepository
) {
    private val log = LoggerFactory.getLogger(OrderApplicationService::class.java)

    /**
     * 注文を作成する
     *
     * 1. 在庫サービスで在庫確認
     * 2. 決済サービスで決済処理
     * 3. 注文を確定して保存・返却
     */
    fun createOrder(request: OrderRequestDto): OrderResponseDto {
        val orderId = UUID.randomUUID().toString()
        log.info("Creating order: {} for product: {}", orderId, request.productId)

        val saveOrderWithState = { amount: BigDecimal, stateAction: (Order) -> Unit ->
            val order = Order(
                orderId = orderId,
                productId = request.productId,
                quantity = request.quantity,
                customerId = request.customerId,
                totalAmount = amount,
                status = com.example.orderservice.domain.model.OrderStatus.ERROR // 初期状態として設定、すぐに上書きされる
            )
            stateAction(order)
            orderRepository.save(order)
            toDto(order)
        }

        // 1. 在庫確認
        val inventory = try {
            inventoryPort.checkInventory(request.productId)
        } catch (e: Exception) {
            log.error("Failed to check inventory: {}", e.message)
            return saveOrderWithState(BigDecimal.ZERO) { it.markAsError(BigDecimal.ZERO) }
        }

        // 在庫なしの場合
        if (!inventory.available || inventory.quantity < request.quantity) {
            log.warn("Product {} is out of stock", request.productId)
            return saveOrderWithState(BigDecimal.ZERO) { it.markAsOutOfStock() }
        }

        // 2. 決済処理
        val totalAmount = inventory.unitPrice.multiply(BigDecimal(request.quantity))
        val paymentResponse = try {
            paymentPort.processPayment(orderId, request.customerId, totalAmount)
        } catch (e: Exception) {
            log.error("Failed to process payment: {}", e.message)
            return saveOrderWithState(totalAmount) { it.markAsError(totalAmount) }
        }

        // 決済失敗の場合
        if (paymentResponse.status != PaymentResultStatus.SUCCESS) {
            log.warn("Payment failed for order: {}", orderId)
            return saveOrderWithState(totalAmount) { it.markPaymentFailed(totalAmount) }
        }

        // 3. 注文確定
        log.info("Order confirmed: {}", orderId)
        return saveOrderWithState(totalAmount) { it.confirm(totalAmount) }
    }

    private fun toDto(order: Order): OrderResponseDto {
        return OrderResponseDto(
            orderId = order.orderId,
            productId = order.productId,
            quantity = order.quantity,
            customerId = order.customerId,
            totalAmount = order.totalAmount,
            status = order.status
        )
    }
}
