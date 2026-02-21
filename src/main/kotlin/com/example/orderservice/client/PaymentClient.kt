package com.example.orderservice.client

import com.example.orderservice.model.PaymentRequest
import com.example.orderservice.model.PaymentResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * 決済サービス（外部SaaS）クライアント
 */
@Component
class PaymentClient(
    @Qualifier("paymentWebClient") private val webClient: WebClient
) {
    private val log = LoggerFactory.getLogger(PaymentClient::class.java)

    /**
     * 決済を実行する
     */
    fun processPayment(paymentRequest: PaymentRequest): PaymentResponse {
        log.info("Processing payment for order: {}", paymentRequest.orderId)
        return try {
            webClient.post()
                .uri("/api/payments")
                .bodyValue(paymentRequest)
                .retrieve()
                .bodyToMono(PaymentResponse::class.java)
                .block()
                ?: throw RuntimeException("Empty response from payment service")
        } catch (e: WebClientResponseException) {
            log.error("Payment service error: {} {}", e.statusCode, e.responseBodyAsString)
            throw RuntimeException("Payment service error: ${e.message}", e)
        }
    }
}
