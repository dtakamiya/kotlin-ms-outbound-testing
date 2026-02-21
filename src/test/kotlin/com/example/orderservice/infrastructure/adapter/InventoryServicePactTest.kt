package com.example.orderservice.infrastructure.adapter

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.annotations.Pact
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal

@ExtendWith(PactConsumerTestExt::class)
@PactTestFor(providerName = "InventoryService")
class InventoryServicePactTest {

    @Pact(consumer = "OrderService", provider = "InventoryService")
    fun availableProductPact(builder: PactDslWithProvider): V4Pact {
        return builder
            .given("商品 PROD-001 の在庫がある")
            .uponReceiving("在庫あり商品の在庫確認リクエスト")
            .path("/api/inventory/PROD-001")
            .method("GET")
            .willRespondWith()
            .status(200)
            .headers(mapOf("Content-Type" to "application/json"))
            .body(
                """
                {
                    "productId": "PROD-001",
                    "productName": "テスト商品",
                    "available": true,
                    "quantity": 100,
                    "unitPrice": 1500.00
                }
                """.trimIndent()
            )
            .toPact(V4Pact::class.java)
    }

    @Pact(consumer = "OrderService", provider = "InventoryService")
    fun outOfStockProductPact(builder: PactDslWithProvider): V4Pact {
        return builder
            .given("商品 PROD-999 の在庫がない")
            .uponReceiving("在庫なし商品の在庫確認リクエスト")
            .path("/api/inventory/PROD-999")
            .method("GET")
            .willRespondWith()
            .status(200)
            .headers(mapOf("Content-Type" to "application/json"))
            .body(
                """
                {
                    "productId": "PROD-999",
                    "productName": "在庫切れ商品",
                    "available": false,
                    "quantity": 0,
                    "unitPrice": 2000.00
                }
                """.trimIndent()
            )
            .toPact(V4Pact::class.java)
    }

    @Test
    @PactTestFor(pactMethod = "availableProductPact")
    fun `在庫あり商品の在庫確認ができること`(mockServer: MockServer) {
        val webClient = WebClient.builder()
            .baseUrl(mockServer.getUrl())
            .build()
        val client = InventoryClientAdapter(webClient)

        val response = client.checkInventory("PROD-001")

        response.available shouldBe true
        response.quantity shouldBe 100
        response.unitPrice shouldBe BigDecimal("1500.00")
    }

    @Test
    @PactTestFor(pactMethod = "outOfStockProductPact")
    fun `在庫なし商品の在庫確認ができること`(mockServer: MockServer) {
        val webClient = WebClient.builder()
            .baseUrl(mockServer.getUrl())
            .build()
        val client = InventoryClientAdapter(webClient)

        val response = client.checkInventory("PROD-999")

        response.available shouldBe false
        response.quantity shouldBe 0
    }
}
