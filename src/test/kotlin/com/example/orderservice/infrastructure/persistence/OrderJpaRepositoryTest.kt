package com.example.orderservice.infrastructure.persistence

import com.example.orderservice.domain.model.OrderStatus
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.optional.shouldBePresent
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.math.BigDecimal
import java.util.UUID

@DataJpaTest
class OrderJpaRepositoryTest {

    @Autowired
    private lateinit var orderJpaRepository: OrderJpaRepository

    @Test
    fun `エンティティを保存して検索できること`() {
        val orderId = UUID.randomUUID().toString()
        val entity = OrderEntity(
            orderId = orderId,
            productId = "PROD-001",
            quantity = 2,
            customerId = "CUST-001",
            totalAmount = BigDecimal("3000.00"),
            status = OrderStatus.CONFIRMED
        )

        orderJpaRepository.save(entity)
        val found = orderJpaRepository.findById(orderId)

        found.shouldBePresent()
        found.get().productId shouldBe "PROD-001"
        found.get().status shouldBe OrderStatus.CONFIRMED
    }

    @Test
    fun `customerIdによって複数の注文を取得できること`() {
        val customerId = "CUST-TARGET"
        val order1 = OrderEntity(UUID.randomUUID().toString(), "PROD-A", 1, customerId, BigDecimal("100"), OrderStatus.CONFIRMED)
        val order2 = OrderEntity(UUID.randomUUID().toString(), "PROD-B", 2, customerId, BigDecimal("200"), OrderStatus.PAYMENT_FAILED)
        val order3 = OrderEntity(UUID.randomUUID().toString(), "PROD-C", 1, "OTHER-CUST", BigDecimal("300"), OrderStatus.CONFIRMED)
        
        orderJpaRepository.saveAll(listOf(order1, order2, order3))

        val results = orderJpaRepository.findByCustomerId(customerId)

        results.shouldHaveSize(2)
        results.map { it.productId }.shouldContainExactlyInAnyOrder("PROD-A", "PROD-B")
    }
}
