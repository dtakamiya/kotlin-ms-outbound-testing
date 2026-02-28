package com.example.orderservice.application.port.out

import java.time.Instant

/**
 * 冪等性レコードのステータス
 */
enum class IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}

/**
 * 冪等性レコード
 */
data class IdempotencyRecord(
    val idempotencyKey: String,
    val status: IdempotencyStatus,
    val requestFingerprint: String,
    val responseBody: String?,
    val responseStatus: Int?,
    val createdAt: Instant,
    val expiresAt: Instant
)

/**
 * 冪等性ストレージ操作用ポート（出力ポート）
 */
interface IdempotencyPort {

    /**
     * 冪等性レコードを新規作成する（PROCESSING状態で）。
     * UNIQUE制約違反の場合は false を返す。
     */
    fun tryCreate(
        idempotencyKey: String,
        requestFingerprint: String,
        expiresAt: Instant
    ): Boolean

    /**
     * キーで冪等性レコードを検索する。
     */
    fun findByKey(idempotencyKey: String): IdempotencyRecord?

    /**
     * レコードを COMPLETED に更新し、レスポンスを保存する。
     */
    fun markCompleted(idempotencyKey: String, responseBody: String, responseStatus: Int)

    /**
     * レコードを FAILED に更新する。
     */
    fun markFailed(idempotencyKey: String)

    /**
     * 期限切れレコードを削除する。
     */
    fun deleteExpired(now: Instant): Int
}
