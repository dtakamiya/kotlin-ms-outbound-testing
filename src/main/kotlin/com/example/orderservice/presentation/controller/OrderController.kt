package com.example.orderservice.presentation.controller

import com.example.orderservice.application.dto.OrderRequestDto
import com.example.orderservice.application.dto.OrderResponseDto
import com.example.orderservice.application.service.OrderApplicationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid

/**
 * 注文 REST API コントローラー
 */
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderApplicationService: OrderApplicationService
) {

    /**
     * 注文を作成する
     */
    @PostMapping
    fun createOrder(@Valid @RequestBody request: OrderRequestDto): ResponseEntity<OrderResponseDto> {
        val response = orderApplicationService.createOrder(request)
        return ResponseEntity.ok(response)
    }
}
