package com.example.orderservice.presentation.controller

import com.example.orderservice.application.dto.OrderRequestDto
import com.example.orderservice.application.dto.OrderResponseDto
import com.example.orderservice.application.service.OrderApplicationService
import com.example.orderservice.domain.model.OrderStatus
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

@WebMvcTest(OrderController::class)
class OrderControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var orderApplicationService: OrderApplicationService

    @Test
    fun `正常なリクエストの場合_200 OKと注文レスポンスが返ること`() {
        val request = OrderRequestDto(
            productId = "PROD-100",
            quantity = 3,
            customerId = "CUST-100"
        )
        val response = OrderResponseDto(
            orderId = "ORDER-888",
            productId = "PROD-100",
            quantity = 3,
            customerId = "CUST-100",
            totalAmount = BigDecimal("4500.00"),
            status = OrderStatus.CONFIRMED
        )
        
        every { orderApplicationService.createOrder(any()) } returns response

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

        verify(exactly = 1) { 
            orderApplicationService.createOrder(match { 
                it.productId == "PROD-100" && it.quantity == 3 
            }) 
        }
    }
}
