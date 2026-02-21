package com.example.orderservice.service

import com.example.orderservice.client.InventoryClient
import com.example.orderservice.client.PaymentClient
import com.example.orderservice.model.*
import com.example.orderservice.repository.OrderEntity
import com.example.orderservice.repository.OrderRepository
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
 * 注文サービスのユニットテスト (Unit Test レイヤー)
 *
 * 観点:
 * 1. ビジネスロジックが正しく機能するか
 * 2. 外部依存（Client, Repository）をMock化し、対象クラス（OrderService）単体の振る舞いを高速に検証する
 */
@ExtendWith(MockKExtension::class)
class OrderServiceUnitTest {

    @MockK
    private lateinit var inventoryClient: InventoryClient

    @MockK
    private lateinit var paymentClient: PaymentClient

    @MockK
    private lateinit var orderRepository: OrderRepository

    @InjectMockKs
    private lateinit var orderService: OrderService

    @BeforeEach
    fun setUp() {
        // いつでもRepositoryのsaveが呼ばれたら引数をそのまま返すようにモック
        every { orderRepository.save(any<OrderEntity>()) } returnsArgument 0
    }

    @Nested
    inner class CreateOrder {

        @Test
        fun `正常系_在庫があり決済も成功した場合_CONFIRMEDステータスで注文が作成されること`() {
            // Arrange
            val request = OrderRequest(productId = "PROD-001", quantity = 2, customerId = "CUST-001")
            val unitPrice = BigDecimal("1500.00")
            
            // 在庫ありのモック
            every { inventoryClient.checkInventory(request.productId) } returns InventoryResponse(
                productId = request.productId,
                productName = "テスト商品",
                available = true,
                quantity = 10,
                unitPrice = unitPrice
            )
            
            // 決済成功のモック
            every { paymentClient.processPayment(any()) } returns PaymentResponse(
                paymentId = "PAY-001",
                orderId = "dummy",
                status = PaymentStatus.SUCCESS,
                transactionId = "TXN-12345"
            )

            // Act
            val response = orderService.createOrder(request)

            // Assert
            response.status shouldBe OrderStatus.CONFIRMED
            response.productId shouldBe request.productId
            response.quantity shouldBe request.quantity
            response.totalAmount shouldBe unitPrice.multiply(BigDecimal(request.quantity))
            
            // 呼び出し回数の検証
            verify(exactly = 1) { inventoryClient.checkInventory(request.productId) }
            verify(exactly = 1) { paymentClient.processPayment(any()) }
            verify(exactly = 1) { orderRepository.save(any()) }
        }

        @Test
        fun `異常系_在庫が足りない場合_OUT_OF_STOCKステータスとなり決済は呼ばれないこと`() {
            // Arrange
            val request = OrderRequest(productId = "PROD-002", quantity = 5, customerId = "CUST-001")
            
            // 在庫不足のモック (要求数5に対して在庫2)
            every { inventoryClient.checkInventory(request.productId) } returns InventoryResponse(
                productId = request.productId,
                productName = "テスト商品",
                available = true,
                quantity = 2,
                unitPrice = BigDecimal("1000.00")
            )

            // Act
            val response = orderService.createOrder(request)

            // Assert
            response.status shouldBe OrderStatus.OUT_OF_STOCK
            
            // 検証：決済クライアントは呼ばれていないこと
            verify(exactly = 0) { paymentClient.processPayment(any()) }
            // 検証：DBには保存されていること
            verify(exactly = 1) { orderRepository.save(any()) }
        }

        @Test
        fun `異常系_決済が失敗した場合_PAYMENT_FAILEDステータスで注文が保存されること`() {
            // Arrange
            val request = OrderRequest(productId = "PROD-003", quantity = 1, customerId = "CUST-001")
            
            every { inventoryClient.checkInventory(request.productId) } returns InventoryResponse(
                productId = request.productId,
                productName = "テスト商品",
                available = true,
                quantity = 10,
                unitPrice = BigDecimal("2000.00")
            )
            
            // 決済失敗のモック
            every { paymentClient.processPayment(any()) } returns PaymentResponse(
                paymentId = "PAY-002",
                orderId = "dummy",
                status = PaymentStatus.FAILED,
                transactionId = null
            )

            // Act
            val response = orderService.createOrder(request)

            // Assert
            response.status shouldBe OrderStatus.PAYMENT_FAILED
            verify(exactly = 1) { orderRepository.save(match { it.status == OrderStatus.PAYMENT_FAILED }) }
        }
    }
}
