package com.example.orderservice.integration

import com.example.orderservice.model.OrderRequest
import com.example.orderservice.model.OrderResponse
import com.example.orderservice.model.OrderStatus
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
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
class OrderServiceWireMockTest(
    @LocalServerPort private val port: Int,
    private val restTemplate: TestRestTemplate
) : DescribeSpec() {

    companion object {
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        init {
            wireMockServer.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("external-services.inventory.base-url") { wireMockServer.baseUrl() }
            registry.add("external-services.payment.base-url") { wireMockServer.baseUrl() }
        }
    }

    init {
        beforeEach {
            wireMockServer.resetAll()
        }

        afterSpec {
            wireMockServer.stop()
        }

        describe("POST /api/orders - 注文作成API") {

            context("正常系: 在庫あり → 決済成功") {
                it("注文が正常に作成されること") {
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

            context("異常系: 在庫なし") {
                it("注文が在庫切れステータスになること") {
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

            context("異常系: 決済失敗") {
                it("注文が決済失敗ステータスになること") {
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

            context("異常系: 外部サービスタイムアウト") {
                it("在庫サービスがタイムアウトした場合、エラーステータスになること") {
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
}
