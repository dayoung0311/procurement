# CLAUDE.md — Procurement Service

> Hyundai AutoEver 3기 4차 프로젝트 / **Procurement(구매) 도메인** 백엔드
> 이 문서는 Claude Code가 이 서비스를 작업할 때 반드시 따라야 할 컨텍스트와 규칙이다.
> `⚠️ 확인 필요` 표시는 아직 팀 결정이 안 된/내가 확정 못 한 항목이니 임의로 코드에 박지 말 것.
>
> **연관 문서 (이 문서보다 우선하는 조직 계약):**
> - [BBD-AES EDA 이벤트 계약](./eda-event-spec.md) v3 — 토픽·DTO·컨슈머 규칙의 단일 진실원천. **스펙 변경은 그 문서 PR로만.**
> - [가격 정책](./price-policy.md) v3 — 기준단가 · 거래가 스냅샷 · 재고 단가 3종 구분 원칙.
>
> 충돌 시 우선순위: **eda-event-spec.md / price-policy.md > 이 문서**. 이 문서에는 Procurement 관점 요약만 둔다.
> ⚠️ 위 두 문서는 **이 레포에 없는 외부(조직 공유) 문서**다. 링크만 두며, 레포에서 파일을 찾지 말 것.

> **최근 큰 변경 요약 (최신순):**
> - **(이슈 #46)** MessageRelay — **Kafka 발행 ack 확인 후** outbox `processed_at` 기록하도록 수정 (send().get() 성공 시에만 markProcessed → at-least-once 보장).
> - **(이슈 #44)** local / public 프로파일 설정 정합성 확보 — local에 Kafka 브로커 설정 추가, public 프로파일 env 방식 정리.
> - **(이슈 #42)** Item·Sales 연동을 **RestClient → Spring HTTP Interface(`@HttpExchange`)** 로 전환.
> - **(이슈 #40)** **자체 인증/인가 코드 전면 삭제** — `@HasRole`·AUTH001/AUTH002 ErrorCode 제거, PO 컨트롤러는 사번을 **`X-User-Id` 헤더**로 수령 (Gateway 인증 전제).
> - **(이슈 #38)** 헥사고날 **포트-어댑터 분리 완료** — Outbox 포트화, Item·Sales 호출 포트-어댑터화, MessageRelay를 adapter 계층으로 이동.
> - **(이슈 #36)** **PO 변경이력(History) 기능** 추가 — 생성·수정·완료·취소 시 변경 전/후 스냅샷 기록 + 조회 API.
> - **(이슈 #31)** **Sales SO 상세 조회 중계 API** + SalesHttpService 구현. PO 작성 시 Item 단가·부품명 자동 조회 적용.
> - **(이슈 #29 — 완료)** Item 연동 — PO 작성 시 SKU별 단가·부품명 자동 조회. (#42에서 HTTP Interface로 리팩토링됨)
> - **(이슈 #28)** Kafka 연동 — `spring-boot-starter-kafka` 추가, `MessageRelay`가 `KafkaTemplate.send()` 사용.
> - **(이슈 #27)** `VendorCreated` · `DomainEventOutboxRelay` · `DomainEvent` 인터페이스 제거 — Vendor 이벤트 Kafka 발행 없음.
> - **(이슈 #25)** `CONFIRMED` 상태 제거 → 상태 머신 **DRAFT → RECEIVED** 2단계. `/confirm` + `/receive` → **`/complete` 1개** 통합.

---

## 1. 이 서비스가 하는 일 (한 줄 요약)

**"어떤 공급사에게 + 어떤 부품을 + 몇 개 + 얼마에 주문했고 + 입고했는지"** 를 관리하는 서비스.

- 사용자: 발주처(본사) 직원
- Procurement가 직접 책임지는 것:
  - 공급사(Vendor) 정보 관리 — 코드, 이름, 연락처, 거래 조건, 활성 여부
  - 구매 주문(PO) 작성(DRAFT) · 완료(RECEIVED) · 취소
  - 완료 처리 — PO를 RECEIVED로 전이(동기)하면서 **`StockInRequested` 이벤트 발행 예약(outbox)** → 재고 반영은 Inventory가 비동기로 수행
  - 구매 단가(vendor 협상가)의 **거래 시점 스냅샷** 보존
  - **PO 변경이력 기록·조회** — 모든 변경(생성/헤더수정/라인교체/완료/취소)을 전/후 스냅샷으로 남김 (이슈 #36)
  - SO 연계 발주 시 `soNumber`를 이벤트에 실어 **Sales 백오더 충족 트리거** 제공
  - **Sales SO 상세 조회 중계** — 발주 요청 기반 PO 작성을 돕기 위해 Sales의 SO 상세를 동기 조회해 전달 (이슈 #31)
  - (확장 예약) 안전재고 미달 시 PO 자동 생성 — `inventory.purchase-requested`는 아직 비계약. 현재 PO는 사람이 생성

---

## 2. 아키텍처 컨텍스트

- 6개 마이크로서비스, **서비스마다 별도 DB** (공유 DB 금지, 직접 테이블 접근 금지)
- Client(React) → **Gateway**(Spring Cloud Gateway, On-Premise) → 각 서비스(ECS Fargate)
- 메시징: **Kafka** (plain spring-kafka, SCS 미사용 — 4팀 합의)
- **CI/CD: GitHub Actions** (`.github/workflows/workflow.yml`) — main push 시 Docker 이미지 빌드 → Docker Hub push → infra 레포(`BBD-AES/infra`) deploy 워크플로를 repository-dispatch로 트리거. (ECR push 단계는 주석 상태로 대기)
- 브로커: `kafka.inwoohub.com:9092` (PLAINTEXT) · UI: https://kafka-ui.inwoohub.com/
- 다른 서비스 데이터가 필요하면 **반드시 그 서비스의 API/이벤트를 통해서**만 접근

### 인터페이스 (다른 서비스와의 관계)

| 방향 | 대상 | 목적 | 통신 방식 |
|---|---|---|---|
| Procurement → Inventory | 재고 증가 요청 | 완료 처리(DRAFT → RECEIVED 전이) 시 | **순수 비동기 Kafka — `procurement.stock-in-requested` (Outbox 경유)** |
| Procurement → Sales | 백오더 충족 트리거 | SO 연계 PO 완료 시 (`soNumber` 포함) | 같은 토픽 — sales가 별도 그룹(`sales-backorder`)으로 구독. **발행 코드는 동일, 추가 작업 없음** |
| Procurement → Item | 부품명·현재 기준단가 조회 | PO 작성/라인 수정 시 (라인별 SKU) | **Spring HTTP Interface 동기 호출** (`@HttpExchange`, RestClient 기반) — `GET /api/v1/items/{sku}` (`item.base-url`) |
| Procurement → Sales | SO 상세 조회 (중계) | 발주 요청 기반 PO 작성 보조 | **Spring HTTP Interface 동기 호출** — `GET /api/v1/sales-orders/{soNumber}` (`sales.base-url`) |
| Gateway → Procurement | 인증/인가 후 사용자 식별 전달 | 모든 요청 | **`X-User-Id`(사번) / `X-User-Role`(역할) 헤더** 전달. 서비스는 헤더만 신뢰 (자체 인증 없음, 이슈 #40) |
| Sales → Procurement | 백오더 발생 알림 수신 → PO 작성 | Sales에서 백오더가 발생하면 알림을 보내고, Procurement가 이를 받아 해당 품목으로 PO를 작성 | **Sales가 백오더 알림을 발행 → Procurement가 수신해 PO 작성** (수신 = Kafka 컨슈머 필요). SO 연계 발주는 PO가 `soNumber` 보유로 계약 반영됨. ⚠️ 수신 컨슈머는 아직 미구현 |

> **변경 이력 주의:**
> - Inventory 연동: "동기 Feign" → "이벤트 기반(v3)" → (이슈 #25) **순수 비동기 Kafka**로 확정. Inventory에 동기 HTTP 코드 작성 금지.
> - Item 연동: (이슈 #25) "도입 안 함" → (이슈 #29) "RestClient 도입" → (이슈 #42) **Spring HTTP Interface(`@HttpExchange`)로 확정**.
> - Sales 연동: 비동기 백오더 트리거(이벤트)에 더해, (이슈 #31) **동기 SO 조회 중계 API**가 별도로 추가됨. 둘은 별개 채널.
> - 인증: (이슈 #40) **자체 인증/인가 전면 삭제**. Gateway가 검증하고 헤더로 신원 전달하는 모델로 전환.

---

## 3. 도메인 모델

### Vendor (공급사)
```
Vendor
├ code        // 유일 식별자
├ name
├ contact     // 연락처
├ terms       // 거래 조건(지불 기한 등)
└ active      // 활성 여부
```

### PurchaseOrder (PO)
```
PurchaseOrder
├ id                 // PK (IDENTITY)
├ poNumber           // 유일. 형식 PO-YYYY-NNNNNN (이벤트 메시지 key로도 사용, updatable=false)
├ vendorCode         // Vendor 참조 (FK 아님, 코드 참조)
├ warehouseCode      // 입고 창고 (inventory Warehouse.code 형식, 예: WH-HQ-001) — 이벤트 라인에 실림
├ status             // DRAFT / RECEIVED / CANCELED  ← CONFIRMED 없음 (이슈 #25)
├ totalAmount        // 라인 subtotal 합 (BigDecimal, 자동 재계산)
├ expectedArrival    // 예상 입고일 (nullable, LocalDate)
├ note               // 비고 (nullable, TEXT)
├ soNumber           // nullable. SO 연계 발주일 때만 보유 → StockInRequested.soNumber로 전달 (백오더 트리거)
├ createdBy / receivedBy   // 사번 (confirmedBy/At는 V5 마이그레이션으로 제거)
├ createdAt / receivedAt   // createdAt/updatedAt은 BaseTimeEntity 상속
└ lines (1:N)              // PurchaseOrderLine, lineOrder ASC 정렬
```

> ❌ `confirmedBy` / `confirmedAt` 컬럼과 `po.confirm()` 도메인 메서드는 **제거됨** — 새 코드에서 참조 금지. 잔재 발견 시 정리 대상.
> ℹ️ 완료 전이 도메인 메서드명은 **`po.markReceived(receivedBy)`** (서비스의 `complete()`가 호출). 취소는 `po.cancel()`.

### PurchaseOrderLine (주문 항목 한 줄)
```
PurchaseOrderLine
├ id
├ lineOrder        // 라인 순서 (int)
├ sku / partName   // 작성 시점 스냅샷
├ unitPrice        // 작성 시점 vendor 협상가 스냅샷 (BigDecimal)
├ quantity
└ subtotal         // unitPrice × quantity (생성 시 계산)
```
> PO status = **PO 전체의 단계**, line = **개별 품목 줄**. 둘을 헷갈리지 말 것.
> line의 부품명/단가는 마스터를 참조하지 않고 **작성 시점 값을 자체 저장**한다.

### PurchaseOrderHistory (변경이력 — 이슈 #36)
```
PurchaseOrderHistory
├ id
├ poNumber
├ changeType       // PurchaseOrderChangeType: CREATED / HEADER_UPDATED / LINES_REPLACED / COMPLETED / CANCELED
├ beforePayload    // 변경 전 PO 전체 스냅샷 JSON (CREATED는 NULL)
├ afterPayload     // 변경 후 PO 전체 스냅샷 JSON
├ changedBy        // 사번
└ changedAt
```
> 모든 PO 변경 유스케이스(register/updateHeader/updateLines/complete/cancel)가 `recordHistory()`로 같은 트랜잭션 안에서 이력을 남긴다. 스냅샷 직렬화는 `PurchaseOrderSnapshot` + ObjectMapper.

### 가격 — 3종 구분 (→ [가격 정책](./price-policy.md))

| 가격 | 위치 | Procurement 관점 |
|---|---|---|
| 현재 기준단가 | `item.unitPrice` (int, 진실원천) | PO 작성/라인수정 시 HTTP Interface(`GET /api/v1/items/{sku}`)로 조회 → 라인 스냅샷. 이후 마스터 변경과 무관 |
| **거래가 스냅샷** | **PO 라인 `unitPrice`** | **우리가 책임지는 값.** vendor 협상가, 작성 후 불변 (상사문서 — 감사·3-way match 근거) |
| 재고 단가 | `stock.unitPrice` | inventory 소관. 우리 거래가로 절대 덮어쓰이지 않음 (이벤트의 unitPrice는 movement 이력용) |

- **와이어(이벤트)의 단가는 원화 정수(int)** — PO 라인 BigDecimal은 `intValueExact()` 변환 (원화라 안전). ✓ 코드 반영됨.
- Item 응답의 `unitPrice`(int)는 라인 저장 시 `new BigDecimal(int)` 로 승격.

---

## 4. 상태 머신 (State Machine) — 이슈 #25 기준

```
DRAFT ──완료(complete / markReceived)──> RECEIVED
  │
  └────── 취소(cancel) ──────> CANCELED

(RECEIVED, CANCELED 모두 종료 상태 — 이후 어떤 전이도 불가)
```

| 상태 | 설명 | 비고 |
|---|---|---|
| DRAFT | 임시 저장 (작성·수정·삭제 가능) | 헤더/라인 수정은 DRAFT에서만 (`ensureDraft()`) |
| RECEIVED | 완료 — 입고 확정 + `StockInRequested` outbox 기록 완료. 재고는 inventory가 비동기 반영 (최종 일관성) | `markReceived()`는 DRAFT일 때만, 라인 1개 이상 + receivedBy 필수 |
| CANCELED | DRAFT에서 취소 종료 | RECEIVED는 취소 불가, 이미 CANCELED면 멱등(no-op) |

**규칙**
- 상태 전이는 **도메인 객체가 스스로 검증**한다(`markReceived`/`cancel`/`ensureDraft`). 서비스/컨트롤러에서 `if (status == ...)` 식 우회 금지.
- 잘못된 전이는 예외(`ApiException`): RECEIVED 재완료 → `PO_ALREADY_RECEIVED`, 그 외 비정상 전이 → `PO_INVALID_STATE_TRANSITION`, DRAFT 아닌 수정 → `PO_NOT_EDITABLE`.
- 취소는 **DRAFT에서만** 가능 (RECEIVED → CANCELED 예외).
- PO 수정(헤더/라인)은 DRAFT에서만.
- `CONFIRMED`라는 단어가 enum, 메서드, 테스트, 주석 어디에도 새로 등장하면 안 된다.

> ⚠️ **권한 강제는 현재 코드에 없음**(이슈 #40로 자체 인가 제거). §6의 역할 표는 *의도된 정책*이며, 실제 인가는 Gateway/추후 구현 몫. (일부 Swagger `@Operation` 설명의 권한 표기는 정책과 다를 수 있으니 §6을 정본으로 본다.)

---

## 5. 핵심 비즈니스 규칙 (절대 깨지면 안 됨)

1. **단가 스냅샷** — PO 라인 단가는 작성 시점 값을 *복사*. 이후 마스터가 바뀌어도 PO 단가는 불변.
2. **멱등성** — 같은 PO를 **중복 완료 처리할 수 없다**. 재고가 두 번 늘어나면 안 됨.
3. **원자성(로컬)** — 완료 처리는 "PO 상태 변경(DRAFT→RECEIVED) + 이벤트 발행 예약(outbox INSERT) + 이력 기록"이 **한 DB 트랜잭션**으로 성립해야 한다. 어느 한쪽만 커밋되는 경우는 존재할 수 없다. ✓ `complete()`가 `@Transactional`로 보장.

### 완료 처리 흐름 (`/complete`) — 상태 변경은 동기, 이벤트 발행은 비동기

```
① 사전 검증: PO가 DRAFT인지 도메인이 확인(markReceived). RECEIVED/CANCELED면 예외 (재진입 방어)
② 한 트랜잭션 안에서 (동기 — API 응답 전에 완료):
   - po.markReceived(receivedBy)  → 상태 RECEIVED + receivedBy/At 기록
   - StockInRequested DTO 직렬화 → outbox_event INSERT (topic, key=poNumber, payload)
   - recordHistory(COMPLETED)     → purchase_order_history INSERT (before/after 스냅샷)
   - 커밋 → 클라이언트에 응답
③ MessageRelay(1초 폴링, 비동기)가 outbox 행을 읽어 KafkaTemplate.send(topic, key, payload)
   → 발행 ack(.get(timeout)) 성공 시에만 markProcessed (이슈 #46)
④ inventory가 수신 → 재고 증가 + movement(IN, 거래가) 기록
   sales(sales-backorder 그룹)가 수신 → soNumber 있으면 백오더 충족 흐름
```

**멱등성 구조**
- 동기 호출 실패 → 롤백 모델이 아니다. **로컬 트랜잭션이 원자성을 보장**하고(상태 변경·outbox·이력이 같이 성공/실패), Kafka 전달은 at-least-once.
- 중복 방어는 양쪽에 건다:
  - **Procurement(생산자):** 상태 머신이 1차 방어 — RECEIVED인 PO는 complete 재호출 시 예외 → 새 이벤트가 만들어지지 않는다.
  - **컨슈머(inventory/sales):** at-least-once로 같은 이벤트가 중복 배달될 수 있으므로 `eventId` 기반 `processed_event` 멱등 가드 필수 (계약 의무, **컨슈머 책임 — Procurement 레포에는 컨슈머/가드 없음이 정상**).
- relay 재시도로 같은 outbox 행이 두 번 발행돼도 `eventId`가 동일하므로 컨슈머에서 무시된다 — **이벤트 1건당 eventId는 outbox INSERT 시점에 한 번만 생성**한다. ✓ `publishStockInRequested()`에서 `UUID.randomUUID()` 1회 생성, relay는 재생성 안 함.
- **MessageRelay 견고성 메모(이슈 #46):** 발행은 `send().get(timeout)`로 ack를 기다린 뒤 성공 시에만 `markProcessed()`. 실패 시 행은 미처리로 남아 다음 폴링에 재시도된다. ⚠️ 현재 **retry cap / DLQ / failed_count 없음** — 영구 실패(poison) 이벤트는 무한 재시도된다. (운영 보강 후보, 미구현)
- "완벽한 분산 원자성"이 아니라 **로컬 원자성(outbox) + at-least-once + 컨슈머 멱등 = 최종 일관성**이 목표임을 기억할 것.

### `StockInRequested` 발행 스펙 (요약 — 정본은 [이벤트 계약 §3-2](./eda-event-spec.md))

- 토픽 `procurement.stock-in-requested` · key = `poNumber` · JSON 문자열 직렬화
- 발행 시점: **PO RECEIVED 전이 시점** (= `/complete` 트랜잭션)
- envelope: `eventId`(UUID) / `source`="procurement" / `eventType`="STOCK_IN_REQUESTED" / `occurredAt`=**UTC Instant 문자열** (`Instant.toString()`, LocalDateTime 금지)
  - ✓ 검증됨: Boot 4.0.6 = Jackson 3, 커스텀 ObjectMapper/`spring.jackson` 설정 없음 → 기본값(`WRITE_DATES_AS_TIMESTAMPS=false`)으로 `Instant`가 **ISO-8601 문자열**로 직렬화됨(epoch 숫자 아님). 컨슈머는 `Instant`/ISO-8601로 역직렬화할 것.
- body: `poNumber`, `soNumber`(nullable — SO 연계 발주만), `lines[]`(sku, quantity, warehouseCode, unitPrice **원화 int**)
- DTO record는 계약 문서에서 **그대로 복붙** (공유 jar 없음, 구독 레포와 동일 정의 유지). 현재 정의: `StockInRequested`(+ 중첩 `Line`).
- `DomainEvent` 인터페이스는 이슈 #27에서 제거됨 — DTO를 직접 직렬화해 payload 저장.

---

## 6. 권한 (정책 — 현재 코드 강제 없음, Gateway/추후 구현 몫)

| 역할 | 권한 |
|---|---|
| HQ_MANAGER | PO 작성·수정·**완료(complete)**·취소(DRAFT만) + 공급사 관리 |
| HQ_STAFF | PO 작성·수정·취소(DRAFT만). **complete 불가** |
| BRANCH | 구매 권한 없음 (구매는 본사 영역) |

> 이전 설계의 "STAFF 취소는 매니저 승인 필요"는 **폐기** — 취소 규칙은 두 역할 모두 "DRAFT에서만"으로 단순화됨.
> ⚠️ 이슈 #40로 `@HasRole`·자체 인가 로직이 제거되어 **이 표는 현재 런타임에 강제되지 않는다.** 역할은 `X-User-Role` 헤더로 전달받는 전제이며, 실제 인가 지점(Gateway 또는 서비스 재검증)은 **미확정**.

---

## 7. API 명세

> Base path: `/api/v1` · 담당: Dayoung
> 경로 변수는 `{poNumber}`로 통일. 사번은 `X-User-Id` 헤더(미지정 시 기본 `SYSTEM`).

### Vendor (공급사)

| 메서드 | 엔드포인트 | 설명 | 권한(정책) |
|---|---|---|---|
| POST | `/api/v1/vendors` | 공급사 등록 | HQ_MANAGER |
| PATCH | `/api/v1/vendors/{code}` | 공급사 수정 | HQ_MANAGER |
| PATCH | `/api/v1/vendors/{code}/active` | 공급사 활성/비활성 전환 | HQ_MANAGER |
| GET | `/api/v1/vendors/{code}` | 공급사 상세 조회 | HQ_MANAGER, HQ_STAFF |
| GET | `/api/v1/vendors` | 공급사 목록 조회 | HQ_MANAGER, HQ_STAFF |

### PurchaseOrder (PO)

| 메서드 | 엔드포인트 | 설명 | 권한(정책) |
|---|---|---|---|
| POST | `/api/v1/purchase-orders` | PO 작성 (DRAFT) | HQ_MANAGER, HQ_STAFF |
| PATCH | `/api/v1/purchase-orders/{poNumber}` | PO 헤더 수정 (DRAFT만) | HQ_MANAGER, HQ_STAFF |
| PUT | `/api/v1/purchase-orders/{poNumber}/lines` | PO 라인 교체 (DRAFT만) | HQ_MANAGER, HQ_STAFF |
| POST | `/api/v1/purchase-orders/{poNumber}/complete` | **완료 (DRAFT → RECEIVED + StockInRequested outbox + 이력)** | **HQ_MANAGER** |
| POST | `/api/v1/purchase-orders/{poNumber}/cancel` | PO 취소 (DRAFT → CANCELED) | HQ_MANAGER, HQ_STAFF |
| GET | `/api/v1/purchase-orders/{poNumber}` | PO 상세 조회 | HQ_MANAGER, HQ_STAFF |
| GET | `/api/v1/purchase-orders` | PO 목록 조회 | HQ_MANAGER, HQ_STAFF |
| GET | `/api/v1/purchase-orders/{poNumber}/history` | **PO 변경이력 조회 (시간순, 이슈 #36)** | HQ_MANAGER, HQ_STAFF |

### SalesOrder 중계 (이슈 #31)

| 메서드 | 엔드포인트 | 설명 | 권한(정책) |
|---|---|---|---|
| GET | `/api/v1/sales-orders/{soNumber}` | Sales 서비스의 SO 상세를 동기 조회·중계 (PO 작성 보조) | HQ_MANAGER, HQ_STAFF |

> ❌ `/confirm`, `/receive` 엔드포인트는 **만들지 않는다** (이슈 #25). 기존 명세/스프레드시트에 남아 있어도 무시.

**API ↔ 규칙 연결 메모**
- `PATCH .../{poNumber}` · `PUT .../{poNumber}/lines` (수정 계열)는 **DRAFT 상태에서만** 허용 (§4). 다른 상태면 예외.
- `complete` / `cancel`은 직접 status를 바꾸는 게 아니라 **도메인 객체의 전이 메서드**(`markReceived`/`cancel`)를 호출하는 형태로 구현 (§4 규칙).
- `complete`는 §5의 outbox 흐름을 따른다 — 응답은 "완료 확정(상태 변경 + 발행 예약까지 동기 보장)"이지 "재고 반영 완료"가 아님 (재고는 비동기 반영).
- Item·Sales 호출 실패 시 `ITEM_NOT_FOUND`/`ITEM_SERVICE_ERROR`, `SO_NOT_FOUND`/`SALES_SERVICE_ERROR`로 매핑.

---

## 8. 기술 스택 / 컨벤션

- Language / Framework: Java 21 + Spring Boot 4.0.6
- Build: **Gradle**
- DB: **PostgreSQL** (Procurement 전용) · 스키마 변경은 Flyway 마이그레이션으로 관리
  - V1 vendor / V2 outbox / V3 purchase_order / V4 so_id 추가 / **V5 confirmed 컬럼·enum 제거** / **V6 outbox topic 컬럼** / **V7 so_id → so_number** / **V8 purchase_order_history**
- 아키텍처 스타일: **헥사고날(포트-어댑터) 적용 완료** (이슈 #38) — `adapter/in/web`, `adapter/out/persistence`, `adapter/out/external`, `application/port/{in,out}`, `domain` 계층 분리. 도메인별 패키징(vendor / purchaseorder / shared.outbox).
- 동기 HTTP 클라이언트: **Spring HTTP Interface(`@HttpExchange`)** — `HttpServiceConfig`에서 `HttpServiceProxyFactory` + `RestClientAdapter`로 `ItemHttpService`·`SalesHttpService` 빈 생성. Feign 미사용. **Inventory 연동은 Kafka only** (동기 HTTP 금지).
- Kafka: **`org.springframework.boot:spring-boot-starter-kafka`** — raw `org.springframework.kafka:spring-kafka`만 넣으면 Boot 4.0 모듈 분리로 `KafkaTemplate` 빈이 안 생겨 **기동 실패** (sales에서 실증). 현재 **producer만 설정**(key/value String serializer), **컨슈머(KafkaListener) 미구현** — 현재는 사실상 발행 전용. 단, **Sales 백오더 알림 수신 → PO 작성** 흐름(§9)이 추가되면 그 용도의 컨슈머가 들어온다.
- 직렬화: JSON 문자열 (String serializer, 타입 헤더 없음). ObjectMapper는 starter-json 기본 빈(커스텀 설정 없음) = Jackson 3.
- 인증: **자체 인증 없음**(이슈 #40). Gateway가 JWT 검증 후 `X-User-Id`/`X-User-Role` 헤더 전달 전제. ⚠️ **Gateway JWT 검증 방식(게이트웨이 단독 vs 서비스 재검증)은 미확정** — 관련 보안 코드 임의 생성 금지.

### 프로파일 (설정)

- `application.yaml` (기본) — **gitignore 대상, 로컬 개인용.** datasource·item·sales를 `${ENV}` placeholder로 둠. `ddl-auto: update`.
- `application-local.yaml` — `local` 프로파일, **추적됨.** localhost DB, `ddl-auto: validate`, Flyway·Kafka 설정 포함. 팀 공유 로컬 표준.
- `application-public.yml` — `public` 프로파일(운영), **추적됨.** DB/Kafka/URL 전부 `${ENV}` 방식, `ddl-auto: validate`. 운영 컨테이너가 이 프로파일로 기동, 값은 infra/AWS에서 주입.

> ⚠️ `⚠️ 확인 필요` 항목 확정 전까지 Claude는 해당 스택 관련 코드를 임의로 생성하지 말고 먼저 물어볼 것.

---

## 9. 구현 현황 / 체크리스트 (procurement 분)

- [x] `outbox_event` 테이블 + MessageRelay(1초 폴링) 완비
- [x] V5 마이그레이션: `confirmed_by`/`confirmed_at` 컬럼 제거 + status enum CONFIRMED 제거 (이슈 #25)
- [x] `po.confirm()` 제거, `markReceived()`(DRAFT→RECEIVED) + 서비스 `complete()` 구현 (이슈 #25)
- [x] `POST .../complete` 유스케이스 + 엔드포인트 (이슈 #25)
- [x] V6 마이그레이션: outbox_event `topic` 컬럼 추가 (이슈 #25)
- [x] `spring-boot-starter-kafka` + 브로커 설정 (이슈 #28)
- [x] `MessageRelay.publish()` → `KafkaTemplate.send(topic, key, payload)` 교체 (이슈 #28)
- [x] `VendorCreated`·`DomainEventOutboxRelay`·`DomainEvent` 제거 (이슈 #27)
- [x] V7 마이그레이션: `so_id` → `so_number` 컬럼 rename (이슈 #25 후속)
- [x] **Item 연동 — PO 작성/라인수정 시 SKU별 단가·부품명 자동 조회** (이슈 #29, #31)
- [x] **Sales SO 상세 조회 중계 API + SalesHttpService** (이슈 #31)
- [x] **PO 변경이력 테이블(V8) + 기록·조회 API** (이슈 #36)
- [x] **헥사고날 포트-어댑터 분리** — Outbox·Item·Sales 포트화, MessageRelay adapter 이동 (이슈 #38)
- [x] **자체 인증/인가 삭제 + `X-User-Id` 헤더 방식 전환** (이슈 #40)
- [x] **Item·Sales 연동 RestClient → HTTP Interface 전환** (이슈 #42)
- [x] **local/public 프로파일 설정 정합성 확보** (이슈 #44)
- [x] **MessageRelay — Kafka ack 확인 후 outbox 처리** (이슈 #46)
- [ ] Gateway JWT 검증 방식 확정 및 구현 (⚠️ 팀 결정 필요)
- [ ] **Sales 백오더 알림 수신 → PO 작성 흐름 구현** — Sales가 백오더 발생 알림을 보내면 Procurement가 받아 해당 품목 PO를 작성. (흐름은 확정, **수신 컨슈머 미구현**)
- [ ] (운영 보강 후보) MessageRelay retry cap / DLQ / failed_count — poison 메시지 무한 재시도 방지

### 빌드 / 실행 / 테스트 명령

```bash
./gradlew build                                            # 빌드
docker-compose up -d                                       # 로컬 PostgreSQL 기동 (테스트/로컬 실행 전)
./gradlew test                                             # 테스트 (DB 필요 — contextLoads가 실제 DataSource 생성)
./gradlew bootRun --args='--spring.profiles.active=local'  # 로컬 실행 (권장: local 프로파일 명시)
```

> ⚠️ 프로파일 없이 `bootRun`/`test`를 돌리면 기본 `application.yaml`을 쓴다. 기본 프로파일의 `${ENV}` placeholder는 값이 없으면 기본값(localhost)으로 떨어지므로 로컬 DB가 떠 있어야 한다. 운영 RDS로 띄우려면 env 주입 필요.

---

## 10. 작업 시 주의사항 (Claude에게 주는 규칙)

- 상태 머신은 **DRAFT / RECEIVED / CANCELED 3개뿐**. CONFIRMED를 enum·메서드·테스트·주석 어디에도 다시 들이지 않는다.
- 상태 전이 로직은 **반드시 도메인 객체 내부**(`markReceived`/`cancel`/`ensureDraft`)에 둔다. 서비스 레이어에서 상태를 직접 바꾸지 않는다.
- PO 라인의 부품명·단가는 **항상 스냅샷**으로 저장한다. Item을 런타임에 join하지 않는다.
- 다른 서비스 DB에 직접 접근하지 않는다. API/이벤트만 사용.
- **Inventory 연동에 동기 HTTP 코드를 만들지 않는다** — 반드시 §5의 outbox → Kafka 흐름. HTTP Interface는 Item 단가·부품명 조회와 Sales SO 조회 전용.
- 완료 처리는 "PO 상태 변경 + outbox INSERT + 이력 기록"이 **한 트랜잭션**(동기)으로 함께 성공/실패하도록 작성한다. Kafka 발행은 relay가 비동기로, **ack 확인 후 markProcessed**.
- `eventId`는 outbox INSERT 시점에 1회 생성·저장한다. relay가 발행할 때마다 새로 만들지 않는다.
- 이벤트 DTO는 [계약 문서](./eda-event-spec.md)의 record를 **그대로 복붙**한다. 필드 추가는 nullable만, 변경은 계약 PR로.
- `occurredAt`은 UTC `Instant` 문자열. LocalDateTime 직렬화 금지. `DomainEvent` 인터페이스는 제거됨 — DTO 직접 직렬화.
- 이벤트의 단가는 원화 정수(int) — BigDecimal은 `intValueExact()`.
- 인증/인가 코드를 **임의로 다시 만들지 않는다**(이슈 #40로 제거됨). 사번은 `X-User-Id` 헤더로 받고, 역할 강제는 Gateway 방식 확정 전까지 보류.
- SCS(Spring Cloud Stream) 도입 금지 (4팀 합의: plain spring-kafka 통일).
- Kafka **컨슈머는 현재 없다(발행 전용)**. 단 **Sales 백오더 알림 수신 → PO 작성** 흐름(§2, §9)을 구현할 때는 그 용도의 컨슈머를 추가한다 — 이때 at-least-once 대비 `eventId` 기반 멱등 가드(`processed_event`)를 함께 둘 것.
- `⚠️ 확인 필요` 항목은 추측해서 코드에 박지 말고 먼저 질문할 것.