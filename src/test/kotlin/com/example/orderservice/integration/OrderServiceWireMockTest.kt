package com.example.orderservice.integration

import com.example.orderservice.model.OrderRequest
import com.example.orderservice.model.OrderResponse
import com.example.orderservice.model.OrderStatus
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * WireMock を使った結合テスト
 *
 * 外部 MS（Inventory Service）と外部 SaaS（Payment Service）を
 * WireMock でモックして、Order Service の結合テストを行う
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderServiceWireMockTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    companion object {
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @BeforeAll
        fun setupAll() {
            wireMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            wireMockServer.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("external-services.inventory.base-url") { wireMockServer.baseUrl() }
            registry.add("external-services.payment.base-url") { wireMockServer.baseUrl() }
        }
    }

    @BeforeEach
    fun setUp() {
        wireMockServer.resetAll()
    }

    @Nested
    inner class `POST _api_orders - 注文作成API` {

        @Nested
        inner class `正常系_在庫あり_決済成功` {
            @Test
            fun `注文が正常に作成されること`() {
                // Arrange: 在庫サービスのモック設定
                wireMockServer.stubFor(
                    get(urlPathMatching("/api/inventory/.*"))
                        .willReturn(
                            aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .withBody(
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
                        )
                )

                // Arrange: 決済サービスのモック設定
                wireMockServer.stubFor(
                    post(urlEqualTo("/api/payments"))
                        .willReturn(
                            aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .withBody(
                                    """
                                    {
                                        "paymentId": "PAY-001",
                                        "orderId": "dummy",
                                        "status": "SUCCESS",
                                        "transactionId": "TXN-12345"
                                    }
                                    """.trimIndent()
                                )
                        )
                )

                // Act
                val request = OrderRequest(
                    productId = "PROD-001",
                    quantity = 2,
                    customerId = "CUST-001"
                )
                val response = restTemplate.postForEntity(
                    "/api/orders",
                    request,
                    OrderResponse::class.java
                )

                // Assert
                response.statusCode.value() shouldBe 200
                response.body shouldNotBe null
                response.body!!.status shouldBe OrderStatus.CONFIRMED
                response.body!!.productId shouldBe "PROD-001"
                response.body!!.quantity shouldBe 2

                // WireMock のリクエスト検証
                wireMockServer.verify(1, getRequestedFor(urlPathMatching("/api/inventory/.*")))
                wireMockServer.verify(1, postRequestedFor(urlEqualTo("/api/payments")))
            }
        }

        @Nested
        inner class `異常系_在庫なし` {
            @Test
            fun `注文が在庫切れステータスになること`() {
                // Arrange: 在庫なしレスポンス
                wireMockServer.stubFor(
                    get(urlPathMatching("/api/inventory/.*"))
                        .willReturn(
                            aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .withBody(
                                    """
                                    {
                                        "productId": "PROD-002",
                                        "productName": "在庫切れ商品",
                                        "available": false,
                                        "quantity": 0,
                                        "unitPrice": 2000.00
                                    }
                                    """.trimIndent()
                                )
                        )
                )

                // Act
                val request = OrderRequest(
                    productId = "PROD-002",
                    quantity = 1,
                    customerId = "CUST-001"
                )
                val response = restTemplate.postForEntity(
                    "/api/orders",
                    request,
                    OrderResponse::class.java
                )

                // Assert
                response.statusCode.value() shouldBe 200
                response.body!!.status shouldBe OrderStatus.OUT_OF_STOCK

                // 決済サービスは呼ばれないことを検証
                wireMockServer.verify(0, postRequestedFor(urlEqualTo("/api/payments")))
            }
        }

        @Nested
        inner class `異常系_決済失敗` {
            @Test
            fun `注文が決済失敗ステータスになること`() {
                // Arrange: 在庫あり
                wireMockServer.stubFor(
                    get(urlPathMatching("/api/inventory/.*"))
                        .willReturn(
                            aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .withBody(
                                    """
                                    {
                                        "productId": "PROD-003",
                                        "productName": "テスト商品3",
                                        "available": true,
                                        "quantity": 50,
                                        "unitPrice": 3000.00
                                    }
                                    """.trimIndent()
                                )
                        )
                )

                // Arrange: 決済失敗レスポンス
                wireMockServer.stubFor(
                    post(urlEqualTo("/api/payments"))
                        .willReturn(
                            aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .withBody(
                                    """
                                    {
                                        "paymentId": "PAY-002",
                                        "orderId": "dummy",
                                        "status": "FAILED",
                                        "transactionId": null
                                    }
                                    """.trimIndent()
                                )
                        )
                )

                // Act
                val request = OrderRequest(
                    productId = "PROD-003",
                    quantity = 1,
                    customerId = "CUST-001"
                )
                val response = restTemplate.postForEntity(
                    "/api/orders",
                    request,
                    OrderResponse::class.java
                )

                // Assert
                response.statusCode.value() shouldBe 200
                response.body!!.status shouldBe OrderStatus.PAYMENT_FAILED
            }
        }

        @Nested
        inner class `異常系_外部サービスタイムアウト` {
            @Test
            fun `在庫サービスがタイムアウトした場合_エラーステータスになること`() {
                // Arrange: 在庫サービスが遅延レスポンス
                wireMockServer.stubFor(
                    get(urlPathMatching("/api/inventory/.*"))
                        .willReturn(
                            aResponse()
                                .withStatus(500)
                                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .withBody("""{"error": "Internal Server Error"}""")
                        )
                )

                // Act
                val request = OrderRequest(
                    productId = "PROD-004",
                    quantity = 1,
                    customerId = "CUST-001"
                )
                val response = restTemplate.postForEntity(
                    "/api/orders",
                    request,
                    OrderResponse::class.java
                )

                // Assert
                response.statusCode.value() shouldBe 200
                response.body!!.status shouldBe OrderStatus.ERROR
            }
        }
    }
}
