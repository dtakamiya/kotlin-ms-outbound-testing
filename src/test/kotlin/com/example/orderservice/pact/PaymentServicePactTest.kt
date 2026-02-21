package com.example.orderservice.pact

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.annotations.Pact
import com.example.orderservice.client.PaymentClient
import com.example.orderservice.model.PaymentRequest
import com.example.orderservice.model.PaymentStatus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal

/**
 * Pact コンシューマー契約テスト - Payment Service
 *
 * Order Service（コンシューマー）が Payment Service（プロバイダー）に
 * 期待するインタラクションを契約として定義する
 */
@ExtendWith(PactConsumerTestExt::class)
@PactTestFor(providerName = "PaymentService")
class PaymentServicePactTest {

    /**
     * 決済成功の契約
     */
    @Pact(consumer = "OrderService", provider = "PaymentService")
    fun successfulPaymentPact(builder: PactDslWithProvider): V4Pact {
        return builder
            .given("決済サービスが利用可能")
            .uponReceiving("決済成功リクエスト")
            .path("/api/payments")
            .method("POST")
            .headers(mapOf("Content-Type" to "application/json"))
            .body(
                """
                {
                    "orderId": "ORDER-001",
                    "customerId": "CUST-001",
                    "amount": 3000.00,
                    "currency": "JPY"
                }
                """.trimIndent()
            )
            .willRespondWith()
            .status(200)
            .headers(mapOf("Content-Type" to "application/json"))
            .body(
                """
                {
                    "paymentId": "PAY-001",
                    "orderId": "ORDER-001",
                    "status": "SUCCESS",
                    "transactionId": "TXN-12345"
                }
                """.trimIndent()
            )
            .toPact(V4Pact::class.java)
    }

    /**
     * 決済失敗の契約
     */
    @Pact(consumer = "OrderService", provider = "PaymentService")
    fun failedPaymentPact(builder: PactDslWithProvider): V4Pact {
        return builder
            .given("決済サービスが利用可能だが残高不足")
            .uponReceiving("決済失敗リクエスト")
            .path("/api/payments")
            .method("POST")
            .headers(mapOf("Content-Type" to "application/json"))
            .body(
                """
                {
                    "orderId": "ORDER-002",
                    "customerId": "CUST-002",
                    "amount": 999999.00,
                    "currency": "JPY"
                }
                """.trimIndent()
            )
            .willRespondWith()
            .status(200)
            .headers(mapOf("Content-Type" to "application/json"))
            .body(
                """
                {
                    "paymentId": "PAY-002",
                    "orderId": "ORDER-002",
                    "status": "FAILED",
                    "transactionId": null
                }
                """.trimIndent()
            )
            .toPact(V4Pact::class.java)
    }

    @Test
    @PactTestFor(pactMethod = "successfulPaymentPact")
    fun `決済が正常に処理されること`(mockServer: MockServer) {
        // Arrange
        val webClient = WebClient.builder()
            .baseUrl(mockServer.getUrl())
            .build()
        val client = PaymentClient(webClient)

        val request = PaymentRequest(
            orderId = "ORDER-001",
            customerId = "CUST-001",
            amount = BigDecimal("3000.00"),
            currency = "JPY"
        )

        // Act
        val response = client.processPayment(request)

        // Assert
        response.paymentId shouldBe "PAY-001"
        response.orderId shouldBe "ORDER-001"
        response.status shouldBe PaymentStatus.SUCCESS
        response.transactionId shouldBe "TXN-12345"
    }

    @Test
    @PactTestFor(pactMethod = "failedPaymentPact")
    fun `決済が失敗した場合FAILEDステータスが返ること`(mockServer: MockServer) {
        // Arrange
        val webClient = WebClient.builder()
            .baseUrl(mockServer.getUrl())
            .build()
        val client = PaymentClient(webClient)

        val request = PaymentRequest(
            orderId = "ORDER-002",
            customerId = "CUST-002",
            amount = BigDecimal("999999.00"),
            currency = "JPY"
        )

        // Act
        val response = client.processPayment(request)

        // Assert
        response.paymentId shouldBe "PAY-002"
        response.status shouldBe PaymentStatus.FAILED
        response.transactionId shouldBe null
    }
}
