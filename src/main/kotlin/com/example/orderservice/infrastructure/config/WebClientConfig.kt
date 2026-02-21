package com.example.orderservice.infrastructure.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {

    @Bean
    fun inventoryWebClient(
        @Value("\${external-services.inventory.base-url}") baseUrl: String
    ): WebClient {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .build()
    }

    @Bean
    fun paymentWebClient(
        @Value("\${external-services.payment.base-url}") baseUrl: String
    ): WebClient {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .build()
    }
}
