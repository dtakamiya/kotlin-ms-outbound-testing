package com.example.orderservice.infrastructure.adapter

import com.example.orderservice.application.port.out.InventoryPort
import com.example.orderservice.application.port.out.InventoryResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.math.BigDecimal

data class InventoryClientResponse(
    val productId: String,
    val productName: String,
    val available: Boolean,
    val quantity: Int,
    val unitPrice: BigDecimal
)

@Component
class InventoryClientAdapter(
    @Qualifier("inventoryWebClient") private val webClient: WebClient
) : InventoryPort {
    private val log = LoggerFactory.getLogger(InventoryClientAdapter::class.java)

    override fun checkInventory(productId: String): InventoryResult {
        log.info("Checking inventory for product: {}", productId)
        val response = try {
            webClient.get()
                .uri("/api/inventory/{productId}", productId)
                .retrieve()
                .bodyToMono(InventoryClientResponse::class.java)
                .block()
                ?: throw RuntimeException("Empty response from inventory service")
        } catch (e: WebClientResponseException) {
            log.error("Inventory service error: {} {}", e.statusCode, e.responseBodyAsString)
            throw RuntimeException("Inventory service error: ${e.message}", e)
        }

        return InventoryResult(
            available = response.available,
            quantity = response.quantity,
            unitPrice = response.unitPrice
        )
    }
}
