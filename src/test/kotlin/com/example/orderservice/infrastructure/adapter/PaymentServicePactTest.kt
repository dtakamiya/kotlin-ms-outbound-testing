package com.example.orderservice.infrastructure.adapter

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.annotations.Pact
import com.example.orderservice.application.port.out.PaymentResultStatus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal

@ExtendWith(PactConsumerTestExt::class)
@PactTestFor(providerName = "PaymentService")
class PaymentServicePactTest {

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
        val webClient = WebClient.builder()
            .baseUrl(mockServer.getUrl())
            .build()
        val client = PaymentClientAdapter(webClient)

        val response = client.processPayment("ORDER-001", "CUST-001", BigDecimal("3000.00"))

        response.status shouldBe PaymentResultStatus.SUCCESS
        response.transactionId shouldBe "TXN-12345"
    }

    @Test
    @PactTestFor(pactMethod = "failedPaymentPact")
    fun `決済が失敗した場合FAILEDステータスが返ること`(mockServer: MockServer) {
        val webClient = WebClient.builder()
            .baseUrl(mockServer.getUrl())
            .build()
        val client = PaymentClientAdapter(webClient)

        val response = client.processPayment("ORDER-002", "CUST-002", BigDecimal("999999.00"))

        response.status shouldBe PaymentResultStatus.FAILED
        response.transactionId shouldBe null
    }
}
