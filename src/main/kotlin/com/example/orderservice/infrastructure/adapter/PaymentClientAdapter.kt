package com.example.orderservice.infrastructure.adapter

import com.example.orderservice.application.port.out.PaymentPort
import com.example.orderservice.application.port.out.PaymentResult
import com.example.orderservice.application.port.out.PaymentResultStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.math.BigDecimal

data class PaymentClientRequest(
    val orderId: String,
    val customerId: String,
    val amount: BigDecimal,
    val currency: String = "JPY"
)

enum class ExternalPaymentStatus {
    SUCCESS,
    FAILED,
    PENDING
}

data class PaymentClientResponseTyped(
    val paymentId: String,
    val orderId: String,
    val status: ExternalPaymentStatus,
    val transactionId: String?
)

@Component
class PaymentClientAdapter(
    @Qualifier("paymentWebClient") private val webClient: WebClient
) : PaymentPort {
    private val log = LoggerFactory.getLogger(PaymentClientAdapter::class.java)

    override fun processPayment(orderId: String, customerId: String, amount: BigDecimal): PaymentResult {
        log.info("Processing payment for order: {}", orderId)
        val request = PaymentClientRequest(orderId, customerId, amount)

        val response = try {
            webClient.post()
                .uri("/api/payments")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PaymentClientResponseTyped::class.java)
                .block()
                ?: throw RuntimeException("Empty response from payment service")
        } catch (e: WebClientResponseException) {
            log.error("Payment service error: {} {}", e.statusCode, e.responseBodyAsString)
            throw RuntimeException("Payment service error: ${e.message}", e)
        }

        val mappedStatus = when (response.status) {
            ExternalPaymentStatus.SUCCESS -> PaymentResultStatus.SUCCESS
            ExternalPaymentStatus.FAILED -> PaymentResultStatus.FAILED
            ExternalPaymentStatus.PENDING -> PaymentResultStatus.PENDING
        }

        return PaymentResult(
            status = mappedStatus,
            transactionId = response.transactionId
        )
    }
}
