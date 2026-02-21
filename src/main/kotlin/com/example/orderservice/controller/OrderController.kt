package com.example.orderservice.controller

import com.example.orderservice.model.OrderRequest
import com.example.orderservice.model.OrderResponse
import com.example.orderservice.service.OrderService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 注文 REST API コントローラー
 */
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {

    /**
     * 注文を作成する
     */
    @PostMapping
    fun createOrder(@RequestBody request: OrderRequest): ResponseEntity<OrderResponse> {
        val response = orderService.createOrder(request)
        return ResponseEntity.ok(response)
    }
}
