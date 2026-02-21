package com.example.orderservice.client

import com.example.orderservice.model.InventoryResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * 在庫管理サービス（外部MS）クライアント
 */
@Component
class InventoryClient(
    @Qualifier("inventoryWebClient") private val webClient: WebClient
) {
    private val log = LoggerFactory.getLogger(InventoryClient::class.java)

    /**
     * 商品の在庫状況を確認する
     */
    fun checkInventory(productId: String): InventoryResponse {
        log.info("Checking inventory for product: {}", productId)
        return try {
            webClient.get()
                .uri("/api/inventory/{productId}", productId)
                .retrieve()
                .bodyToMono(InventoryResponse::class.java)
                .block()
                ?: throw RuntimeException("Empty response from inventory service")
        } catch (e: WebClientResponseException) {
            log.error("Inventory service error: {} {}", e.statusCode, e.responseBodyAsString)
            throw RuntimeException("Inventory service error: ${e.message}", e)
        }
    }
}
