package com.example.orderservice.controller

import com.example.orderservice.model.OrderRequest
import com.example.orderservice.model.OrderResponse
import com.example.orderservice.model.OrderStatus
import com.example.orderservice.service.OrderService
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal

/**
 * コントローラーのコンポーネントテスト (Component Test / API Test レイヤー)
 *
 * 観点:
 * 1. HTTPリクエストのマッピング (URL, Method) が正しいか
 * 2. 入力バリデーションやシリアライズ／デシリアライズが機能するか
 * 3. 適切なHTTPステータスコードが返却されるか
 * ※ Service層以降はモック化し、Web層の関心事に特化する
 */
@WebMvcTest(OrderController::class)
class OrderControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    // Spring MockKを使用してService層をモック化
    @MockkBean
    private lateinit var orderService: OrderService

    @Test
    fun `正常なリクエストの場合_200 OKと注文レスポンスが返ること`() {
        // Arrange
        val request = OrderRequest(
            productId = "PROD-100",
            quantity = 3,
            customerId = "CUST-100"
        )
        val response = OrderResponse(
            orderId = "ORDER-888",
            productId = "PROD-100",
            quantity = 3,
            customerId = "CUST-100",
            totalAmount = BigDecimal("4500.00"),
            status = OrderStatus.CONFIRMED
        )
        
        // モックの設定
        every { orderService.createOrder(any()) } returns response

        // Act & Assert
        mockMvc.post("/api/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.orderId") { value("ORDER-888") }
            jsonPath("$.productId") { value("PROD-100") }
            jsonPath("$.status") { value("CONFIRMED") }
        }

        // Serviceが正しく呼び出されたことを検証
        verify(exactly = 1) { 
            orderService.createOrder(match { 
                it.productId == "PROD-100" && it.quantity == 3 
            }) 
        }
    }
}
