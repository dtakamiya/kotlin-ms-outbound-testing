# マイクロサービスにおけるテスト戦略ベストプラクティス

アジャイル開発現場で高い変更容易性と品質を維持するための、テスト自動化戦略のガイドラインです。
本プロジェクトでは、テストピラミッド（あるいはマイクロサービス向けテストハニカム）に基づき、各レイヤーに責務を分割したテストを実装しています。

## 1. テストの全体像 (Test Strategy)

| レイヤー | 目的・観点 | 対象コンポーネント | 利用技術・アプローチ |
|---|---|---|---|
| **ユニットテスト (Unit Test)** | ビジネスロジックが期待通りに単独で動作することを保証する。外部依存をモック化して高速に実行・フィードバックを得る。 | Service, Domain Models, Utils | `JUnit 5` (Runner) + `Kotest Matchers` + `MockK` |
| **コンポーネントテスト (Component / API Test)** | APIの入口（Controller）の振る舞いを検証する。ステータスコード、I/Oマッピング、バリデーションが正しいか。 | Controller | `JUnit 5` / `@WebMvcTest` + `MockMvc` + `MockK` |
| **結合テスト - DB層 (Integration Test - DB)** | データベースとの接続、O/Rマッパー（JPA）の設定、カスタムクエリが正しく動作するか。 | Repository | `JUnit 5` / `@DataJpaTest` (+ 必要に応じて `Testcontainers`) |
| **結合テスト - 外部API層 (Integration Test - External API)** | 自サービスが外部サービスに対して正しいリクエストを送り、異常系を含めた様々なレスポンスを正しく処理できるか。 | External Client, Service | `JUnit 5` / `@SpringBootTest` + `WireMock` |
| **契約テスト (Contract Test)** | マイクロサービス間でAPIインターフェースの互換性が保たれていることを保証する。 | Consumer & Provider API | `Pact` (JUnit 5 拡張) |
| **E2E（ブラウザ）テスト (E2E Test)** | ユーザー視点でシステム全体が正しく動作するか検証する（少なめに保つ）。 | UI ~ Backend連携 | `Playwright` / `Selenium` |

---

## 2. 各テストレイヤーの実装サンプルと解説

### 2.1 ユニットテスト
`OrderServiceUnitTest.kt` を参照。

- **Why**: もっとも変更頻度が高く、また複雑な条件分岐を持つのがビジネスロジックです。ここを高速にテストし、リファクタリングの安全網とします。
- **How**:
  - 対象クラス (`OrderService`) に対しては `@InjectMockKs` を使用します。
  - 外部依存 (`InventoryClient` や `OrderRepository` など) は `@MockK` を使用し、`every { ... } returns ...` で振る舞いを定義します。
  - DBアクセスを挟まないため、数ミリ秒で実行完了することが理想です。

### 2.2 API / コンポーネントテスト
`OrderControllerTest.kt` を参照。

- **Why**: APIのエンドポイントごとの仕様（パス、HTTPメソッド、リクエストボディ、レスポンス）が正しいかを確認します。ロジックのテストはユニットテストで行うため、Service層以降は全てモック化します。
- **How**:
  - `@WebMvcTest(OrderController::class)` を利用することで、Web層のコンテキスト（Controller, Filter, バリデーターなど）のみを軽量に起動します。
  - Springコンテキスト内でモック（`@MockkBean`）を利用し、Serviceの戻り値を制御してHTTPステータスの分岐やJSONの構造を検証します。

### 2.3 DB層 結合テスト
`OrderRepositoryTest.kt` を参照。

- **Why**: 複雑なSQLクエリ（またはJPAによるメソッド名クエリ）が期待通りの結果を返すか、テーブルとEntityのマッピング設定に間違いがないかを保証します。
- **How**:
  - `@DataJpaTest` を利用することで、インメモリーデータベース（H2など）を用いてリポジトリ層のテストインフラを自動構成します。
  - テストごとにトランザクションが張られ、テスト終了時にロールバックされるため、他のテストに状態が干渉しません。

### 2.4 外部API層 結合テスト
`OrderServiceWireMockTest.kt` を参照。

- **Why**: マイクロサービスアーキテクチャでは、他システムに依存する処理の安定稼働が重要です。リトライ戦略やタイムアウト時のエラーハンドリングを含めてテストします。
- **How**:
  - `WireMock` を用いて依存先（InventoryService, PaymentService）をスタブ化します。
  - `@SpringBootTest` でアプリケーション全体を起動し、実際のHTTP通信に近い形で振る舞うかを結合検証します。

### 2.5 契約テスト (Pact)
`PaymentServicePactTest.kt`, `InventoryServicePactTest.kt` を参照。

- **Why**: `WireMock` のテストでは「我々が想定する外部サービスの振る舞い」しかテストできません。「彼ら（Provider）が本当にその仕様を守っているか」を保証するために、契約（Pact）を共有し検証サイクルを回します。

---

## 3. アジャイル現場でのテストサイクル

1. **Test Driven Development (TDD) / BDD**: 要件を理解したら、まずテストケースを記述し、そのレッド（失敗）を確認してから実装に入ります。
2. **CI/CD（継続的インテグレーション）でのパイプライン自動化**:
   - `Pull Request` 作成時: ユニットテスト、コンポーネントテスト、DB結合テストが数分以内で完了するように設定。
   - `Merge` 時: より重いE2EテストやWireMockテストを実行し、PactBroker等へ契約(Pact)をパブリッシュする。
3. **Flaky（不安定）なテストの排除**: 外部環境に依存しすぎるテストや待機時間（sleep等）に依存するテストは無くし、WireMockやMockKで制御可能なテストを主体（ピラミッドの下の構造）にします。

この戦略を徹底することで、「安心してリリースできる状態」を常に担保しつつ、高速なデリバリー（アジリティ）を実現可能にします。
