package com.example.orderservice.infrastructure.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

import org.springframework.http.client.reactive.ReactorClientHttpConnector
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class WebClientConfig {

    private fun httpClientWithTimeout(): HttpClient {
        return HttpClient.create()
            .responseTimeout(Duration.ofSeconds(2))
    }

    @Bean
    fun inventoryWebClient(
        @Value("\${external-services.inventory.base-url}") baseUrl: String
    ): WebClient {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClientWithTimeout()))
            .build()
    }

    @Bean
    fun paymentWebClient(
        @Value("\${external-services.payment.base-url}") baseUrl: String
    ): WebClient {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClientWithTimeout()))
            .build()
    }
}
