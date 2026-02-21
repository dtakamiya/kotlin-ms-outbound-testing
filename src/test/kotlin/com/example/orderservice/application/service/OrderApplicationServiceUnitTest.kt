package com.example.orderservice.application.service

import com.example.orderservice.application.dto.OrderRequestDto
import com.example.orderservice.application.port.out.InventoryPort
import com.example.orderservice.application.port.out.InventoryResult
import com.example.orderservice.application.port.out.PaymentPort
import com.example.orderservice.application.port.out.PaymentResult
import com.example.orderservice.application.port.out.PaymentResultStatus
import com.example.orderservice.domain.model.Order
import com.example.orderservice.domain.model.OrderStatus
import com.example.orderservice.domain.repository.OrderRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal

/**
 * 注文アプリケーションサービスのユニットテスト (Unit Test レイヤー)
 */
@ExtendWith(MockKExtension::class)
class OrderApplicationServiceUnitTest {

    @MockK
    private lateinit var inventoryPort: InventoryPort

    @MockK
    private lateinit var paymentPort: PaymentPort

    @MockK
    private lateinit var orderRepository: OrderRepository

    @InjectMockKs
    private lateinit var orderApplicationService: OrderApplicationService

    @BeforeEach
    fun setUp() {
        // いつでもRepositoryのsaveが呼ばれたら引数をそのまま返すようにモック
        every { orderRepository.save(any<Order>()) } returnsArgument 0
    }

    @Nested
    inner class CreateOrder {

        @Test
        fun `正常系_在庫があり決済も成功した場合_CONFIRMEDステータスで注文が作成されること`() {
            // Arrange
            val request = OrderRequestDto(productId = "PROD-001", quantity = 2, customerId = "CUST-001")
            val unitPrice = BigDecimal("1500.00")
            
            // 在庫ありのモック
            every { inventoryPort.checkInventory(request.productId) } returns InventoryResult(
                available = true,
                quantity = 10,
                unitPrice = unitPrice
            )
            
            // 決済成功のモック
            every { paymentPort.processPayment(any(), any(), any()) } returns PaymentResult(
                status = PaymentResultStatus.SUCCESS,
                transactionId = "TXN-12345"
            )

            // Act
            val response = orderApplicationService.createOrder(request)

            // Assert
            response.status shouldBe OrderStatus.CONFIRMED
            response.productId shouldBe request.productId
            response.quantity shouldBe request.quantity
            response.totalAmount shouldBe unitPrice.multiply(BigDecimal(request.quantity))
            
            // 呼び出し回数の検証
            verify(exactly = 1) { inventoryPort.checkInventory(request.productId) }
            verify(exactly = 1) { paymentPort.processPayment(any(), any(), any()) }
            verify(exactly = 1) { orderRepository.save(any()) }
        }

        @Test
        fun `異常系_在庫が足りない場合_OUT_OF_STOCKステータスとなり決済は呼ばれないこと`() {
            // Arrange
            val request = OrderRequestDto(productId = "PROD-002", quantity = 5, customerId = "CUST-001")
            
            // 在庫不足のモック (要求数5に対して在庫2)
            every { inventoryPort.checkInventory(request.productId) } returns InventoryResult(
                available = true,
                quantity = 2,
                unitPrice = BigDecimal("1000.00")
            )

            // Act
            val response = orderApplicationService.createOrder(request)

            // Assert
            response.status shouldBe OrderStatus.OUT_OF_STOCK
            
            // 検証：決済ポートは呼ばれていないこと
            verify(exactly = 0) { paymentPort.processPayment(any(), any(), any()) }
            // 検証：DBには保存されていること
            verify(exactly = 1) { orderRepository.save(any()) }
        }

        @Test
        fun `異常系_決済が失敗した場合_PAYMENT_FAILEDステータスで注文が保存されること`() {
            // Arrange
            val request = OrderRequestDto(productId = "PROD-003", quantity = 1, customerId = "CUST-001")
            
            every { inventoryPort.checkInventory(request.productId) } returns InventoryResult(
                available = true,
                quantity = 10,
                unitPrice = BigDecimal("2000.00")
            )
            
            // 決済失敗のモック
            every { paymentPort.processPayment(any(), any(), any()) } returns PaymentResult(
                status = PaymentResultStatus.FAILED,
                transactionId = null
            )

            // Act
            val response = orderApplicationService.createOrder(request)

            // Assert
            response.status shouldBe OrderStatus.PAYMENT_FAILED
            verify(exactly = 1) { orderRepository.save(match { it.status == OrderStatus.PAYMENT_FAILED }) }
        }
    }
}
