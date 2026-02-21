# kotlin-ms-outbound-testing

マイクロサービスの外部サービス結合テスト サンプルプロジェクト

## 概要

Spring Boot + Kotlin で構築されたマイクロサービス（Order Service）が、外部MS（Inventory Service）や外部SaaS（Payment Service）と連携するシナリオにおいて、**WireMock** と **Pact** を使って結合テストを行うサンプルです。

## アーキテクチャ

```
Order Service（自サービス）
  ├── → Inventory Service（外部MS）: 在庫確認
  └── → Payment Service（外部SaaS）: 決済処理
```

## 技術スタック

| 技術 | バージョン | 用途 |
|------|-----------|------|
| Kotlin | 2.1.x | 開発言語 |
| Spring Boot | 3.4.3 | アプリケーションFW |
| Kotest | 5.9.x | テストフレームワーク |
| WireMock | 3.x | 外部サービスモック |
| Pact-JVM | 4.6.x | 契約テスト |

## テスト種類

### WireMock 結合テスト
外部サービスをWireMockでモックし、E2Eに近い結合テストを実行します。

- ✅ 正常系：在庫あり → 決済成功 → 注文確定
- ❌ 異常系：在庫なし
- ❌ 異常系：決済失敗
- ❌ 異常系：外部サービスエラー

### Pact コンシューマー契約テスト
コンシューマー駆動契約テスト（CDC）により、プロバイダーとの契約を定義・検証します。

- Inventory Service との契約（在庫あり/なし）
- Payment Service との契約（決済成功/失敗）

## ビルド・テスト実行

```bash
# ビルド
./gradlew build

# 全テスト実行
./gradlew test

# WireMock テストのみ
./gradlew test --tests "*WireMockTest"

# Pact テストのみ
./gradlew test --tests "*PactTest"
```

## プロジェクト構成

```
src/
├── main/kotlin/com/example/orderservice/
│   ├── Application.kt           # エントリーポイント
│   ├── config/
│   │   └── WebClientConfig.kt   # WebClient設定
│   ├── model/
│   │   └── Models.kt            # データモデル
│   ├── client/
│   │   ├── InventoryClient.kt   # 在庫サービスクライアント
│   │   └── PaymentClient.kt     # 決済サービスクライアント
│   ├── service/
│   │   └── OrderService.kt      # 注文処理サービス
│   └── controller/
│       └── OrderController.kt   # REST APIコントローラー
├── main/resources/
│   └── application.yml           # アプリケーション設定
└── test/kotlin/com/example/orderservice/
    ├── integration/
    │   └── OrderServiceWireMockTest.kt  # WireMock結合テスト
    └── pact/
        ├── InventoryServicePactTest.kt  # Pact契約テスト（在庫）
        └── PaymentServicePactTest.kt    # Pact契約テスト（決済）
```
