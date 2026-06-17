# Procurement 추가 API 명세

> 사진(Notion 표)에 정리된 API(vendors, purchase-orders, items)를 **제외한** 나머지 구현 API 명세입니다.
> 실제 컨트롤러·DTO·ErrorCode 기준으로 작성했습니다.

## 공통 사항

### 인증 헤더
게이트웨이에서 인증 후 아래 헤더를 전달합니다.

| 헤더 | 설명 |
| --- | --- |
| `X-User-Id` | 사용자 사번 |
| `X-User-Role` | 사용자 역할 (`HQ_MANAGER`, `HQ_STAFF`, `BRANCH`) |

### 성공 응답 공통 포맷 (`ApiResponse<T>`)
이 서비스의 모든 성공 응답은 아래 래퍼로 감싸집니다. (`data`가 `null`이면 해당 필드는 숨겨짐)

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": { }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| code | String | 항상 `SUCCESS` |
| message | String | `OK` 또는 액션별 메시지 |
| data | T | 실제 응답 본문 |

### 실패 응답 공통 포맷 (Spring `ProblemDetail`)
> items 예시(타 서비스)와 달리, 이 서비스는 `ProblemDetail` 표준을 사용합니다.
> `title`에 **에러코드(W001 등)**가 들어가며 별도 `code` 필드 대신 `timestamp`가 있습니다.

```json
{
  "type": "about:blank",
  "title": "W001",
  "status": 404,
  "detail": "작업지시를 찾을 수 없습니다.",
  "instance": "/api/v1/work-orders/WO-2026-000001",
  "timestamp": "2026-06-17T10:00:00.123+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| type | String | 문제 유형 URI (기본 `about:blank`) |
| title | String | 비즈니스 에러코드 (`W001`, `S001` 등) |
| status | int | HTTP status 숫자 |
| detail | String | 사용자에게 보여줄 메시지 |
| instance | String | 에러가 발생한 요청 경로 |
| timestamp | String | 서버 발생 시각 (OffsetDateTime) |

### 공통 에러 케이스
| status | title(code) | detail |
| --- | --- | --- |
| 401 | C002 | 인증이 필요합니다. |
| 403 | C003 | 권한이 없습니다. |
| 400 | C001 | 잘못된 요청입니다. |
| 500 | C999 | 서버 오류가 발생했습니다. |

---

# WorkOrder — 작업지시(생산) 관리 API

`@RequestMapping("/api/v1/work-orders")`

## POST /api/v1/work-orders — 작업지시 생성

### 설명
- 생산 요청 알림 기반으로 작업 지시 생성 (초기 상태 `PLANNED`)
- 라인별 SKU로 Item 가격을 조회해 단가·소계·총액 계산
- 성공 시 **HTTP 201 Created**

### Request
**Headers**

| 헤더 | 타입 | 설명 |
| --- | --- | --- |
| X-User-Id | String | 작업자 사번 (없으면 `SYSTEM`) |

**Body**

| 파라미터 | 타입 | 설명 |
| --- | --- | --- |
| soNumber | String | SO 번호 (필수, 최대 30자) |
| warehouseCode | String | 창고 코드 (필수, 최대 20자) |
| lines | Array | 작업 라인 목록 |
| lines[].lineOrder | int | 라인 순번 (1 이상) |
| lines[].sku | String | 부품 식별자 (필수, 최대 50자) |
| lines[].quantity | int | 수량 (1 이상) |

```json
{
  "soNumber": "SO-2026-0001",
  "warehouseCode": "WH-001",
  "lines": [
    { "lineOrder": 1, "sku": "SKU-1001", "quantity": 10 }
  ]
}
```

### Response
**Success - 201**

```json
{
  "code": "SUCCESS",
  "message": "작업 지시가 생성되었습니다.",
  "data": {
    "workOrderNumber": "WO-2026-000001",
    "soNumber": "SO-2026-0001",
    "warehouseCode": "WH-001",
    "status": "PLANNED",
    "totalAmount": 1500000,
    "createdBy": "10001",
    "completedBy": null,
    "createdAt": "2026-06-17T10:00:00",
    "completedAt": null,
    "lines": [
      {
        "lineOrder": 1,
        "sku": "SKU-1001",
        "partName": "엔진 필터",
        "unitPrice": 150000,
        "quantity": 10,
        "subtotal": 1500000
      }
    ]
  }
}
```

### 주요 에러 케이스
| status | title | detail | 발생 조건 |
| --- | --- | --- | --- |
| 400 | C001 | 잘못된 요청입니다. | soNumber/warehouseCode 누락, lineOrder·quantity ≤ 0 등 검증 실패 |
| 404 | I001 | 해당 SKU의 부품을 찾을 수 없습니다. | 존재하지 않는 SKU |
| 500 | I002 | Item 서비스 호출에 실패했습니다. | Item 서비스 다운/타임아웃 |

---

## POST /api/v1/work-orders/{workOrderNumber}/start — 작업 지시 착수

### 설명
- 상태 전이: `PLANNED → IN_PRODUCTION`

### Request
본문 없음

| 파라미터 | 타입 | 설명 |
| --- | --- | --- |
| workOrderNumber | String | 작업 지시 번호 (필수) 예: `WO-2026-000001` |

### Response
**Success - 200**

```json
{
  "code": "SUCCESS",
  "message": "작업 지시가 착수되었습니다.",
  "data": {
    "workOrderNumber": "WO-2026-000001",
    "soNumber": "SO-2026-0001",
    "warehouseCode": "WH-001",
    "status": "IN_PRODUCTION",
    "totalAmount": 1500000,
    "createdBy": "10001",
    "completedBy": null,
    "createdAt": "2026-06-17T10:00:00",
    "completedAt": null,
    "lines": [ ]
  }
}
```

### 주요 에러 케이스
| status | title | detail | 발생 조건 |
| --- | --- | --- | --- |
| 404 | W001 | 작업지시를 찾을 수 없습니다. | 존재하지 않는 workOrderNumber |
| 409 | W002 | 허용되지 않는 작업지시 상태 전이입니다. | `PLANNED`가 아닌 상태에서 착수 시도 |

---

## POST /api/v1/work-orders/{workOrderNumber}/complete — 작업 지시 완료

### 설명
- 상태 전이: `IN_PRODUCTION → COMPLETED`
- 완료 시 **`StockInRequested` 이벤트 발행** (Outbox)

### Request
**Headers**

| 헤더 | 타입 | 설명 |
| --- | --- | --- |
| X-User-Id | String | 작업자 사번 (없으면 `SYSTEM`) |

**Path**

| 파라미터 | 타입 | 설명 |
| --- | --- | --- |
| workOrderNumber | String | 작업 지시 번호 (필수) 예: `WO-2026-000001` |

### Response
**Success - 200**

```json
{
  "code": "SUCCESS",
  "message": "작업 지시가 완료되었습니다.",
  "data": {
    "workOrderNumber": "WO-2026-000001",
    "soNumber": "SO-2026-0001",
    "warehouseCode": "WH-001",
    "status": "COMPLETED",
    "totalAmount": 1500000,
    "createdBy": "10001",
    "completedBy": "10002",
    "createdAt": "2026-06-17T10:00:00",
    "completedAt": "2026-06-17T15:30:00",
    "lines": [ ]
  }
}
```

### 주요 에러 케이스
| status | title | detail | 발생 조건 |
| --- | --- | --- | --- |
| 404 | W001 | 작업지시를 찾을 수 없습니다. | 존재하지 않는 workOrderNumber |
| 409 | W002 | 허용되지 않는 작업지시 상태 전이입니다. | `IN_PRODUCTION`이 아닌 상태에서 완료 시도 |
| 400 | W003 | 작업지시에 최소 1개 이상의 라인이 필요합니다. | 라인이 비어 있는 작업지시 완료 시도 |

---

## GET /api/v1/work-orders/{workOrderNumber} — 작업 지시 단건 조회

### 설명
- 작업 지시 번호로 단건 상세 조회

### Request
본문 없음

| 파라미터 | 타입 | 설명 |
| --- | --- | --- |
| workOrderNumber | String | 작업 지시 번호 (필수) 예: `WO-2026-000001` |

### Response
**Success - 200** — `data`는 작업지시 단건 (위 생성 응답의 `data`와 동일 구조)

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "workOrderNumber": "WO-2026-000001",
    "soNumber": "SO-2026-0001",
    "warehouseCode": "WH-001",
    "status": "PLANNED",
    "totalAmount": 1500000,
    "createdBy": "10001",
    "completedBy": null,
    "createdAt": "2026-06-17T10:00:00",
    "completedAt": null,
    "lines": [
      {
        "lineOrder": 1,
        "sku": "SKU-1001",
        "partName": "엔진 필터",
        "unitPrice": 150000,
        "quantity": 10,
        "subtotal": 1500000
      }
    ]
  }
}
```

### 주요 에러 케이스
| status | title | detail | 발생 조건 |
| --- | --- | --- | --- |
| 404 | W001 | 작업지시를 찾을 수 없습니다. | 존재하지 않는 workOrderNumber |

---

## GET /api/v1/work-orders — 작업 지시 목록 조회

### 설명
- 전체 작업 지시 목록 조회

### Request
본문 없음 / 파라미터 없음

### Response
**Success - 200** — `data`는 작업지시 배열

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": [
    {
      "workOrderNumber": "WO-2026-000001",
      "soNumber": "SO-2026-0001",
      "warehouseCode": "WH-001",
      "status": "PLANNED",
      "totalAmount": 1500000,
      "createdBy": "10001",
      "completedBy": null,
      "createdAt": "2026-06-17T10:00:00",
      "completedAt": null,
      "lines": [ ]
    }
  ]
}
```

---

# WorkOrderRequest — 생산 요청 알림 API

`@RequestMapping("/api/v1/work-order-requests")`

## GET /api/v1/work-order-requests — 생산 요청 알림 목록 조회

### 설명
- Sales 발주 요청 중 **생산 라인 알림**을 최신순으로 조회

### Request
본문 없음 / 파라미터 없음

### Response
**Success - 200**

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": [
    {
      "eventId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "soNumber": "SO-2026-0001",
      "warehouseCode": "WH-001",
      "status": "PENDING",
      "receivedAt": "2026-06-17T09:00:00",
      "lines": [
        { "sku": "SKU-1001", "quantity": 10 }
      ]
    }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| eventId | String | 원본 이벤트 ID |
| soNumber | String | SO 번호 |
| warehouseCode | String | 창고 코드 |
| status | String | `PENDING`(미처리) / `DONE`(작업지시 생성 완료) |
| receivedAt | DateTime | 알림 수신 시각 |
| lines[].sku | String | 부품 식별자 |
| lines[].quantity | int | 수량 |

---

# PurchaseRequest — Sales 발주 요청 알림 API

`@RequestMapping("/api/v1/purchase-requests")`

## GET /api/v1/purchase-requests — 발주 요청 알림 목록 조회

### 설명
- Sales가 발행한 **발주 요청 알림**을 최신순으로 조회

### Request
본문 없음 / 파라미터 없음

### Response
**Success - 200**

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": [
    {
      "eventId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "soNumber": "SO-2026-0001",
      "warehouseCode": "WH-001",
      "status": "PENDING",
      "receivedAt": "2026-06-17T09:00:00",
      "lines": [
        { "sku": "SKU-1001", "quantity": 10 }
      ]
    }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| eventId | String | 원본 이벤트 ID |
| soNumber | String | SO 번호 |
| warehouseCode | String | 창고 코드 |
| status | String | `PENDING`(수신됨, PO 미작성) / `DONE`(해당 soNumber로 PO 작성 완료) |
| receivedAt | DateTime | 알림 수신 시각 |
| lines[].sku | String | 부품 식별자 |
| lines[].quantity | int | 수량 |

---

# SalesOrder — SO 조회 중계 API

`@RequestMapping("/api/v1/sales-orders")`

## GET /api/v1/sales-orders/{soNumber} — SO 상세 조회

### 설명
- 발주 요청 기반 PO 작성을 위해 **Sales 서비스의 SO 상세를 중계 조회**
- 권한: `HQ_MANAGER`, `HQ_STAFF`

### Request
본문 없음

| 파라미터 | 타입 | 설명 |
| --- | --- | --- |
| soNumber | String | SO 번호 (필수) 예: `SO-2026-0001` |

### Response
**Success - 200**

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "soNumber": "SO-2026-0001",
    "fromWarehouseCode": "WH-001",
    "fromWarehouseName": "중앙창고",
    "toWarehouseCode": "WH-010",
    "toWarehouseName": "지점창고",
    "status": "APPROVED",
    "priority": "NORMAL",
    "requestedBy": "10001",
    "approvedBy": "10002",
    "requestedAt": "2026-06-16T09:00:00",
    "approvedAt": "2026-06-16T11:00:00",
    "totalAmount": 1500000,
    "note": "긴급 보충",
    "lines": [
      {
        "lineNo": 1,
        "sku": "SKU-1001",
        "nameSnapshot": "엔진 필터",
        "unitPriceSnapshot": 150000,
        "quantity": 10
      }
    ]
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| soNumber | String | SO 번호 |
| fromWarehouseCode / Name | String | 출발 창고 코드/명 |
| toWarehouseCode / Name | String | 도착 창고 코드/명 |
| status | String | SO 상태 |
| priority | String | 우선순위 |
| requestedBy / approvedBy | String | 요청자/승인자 사번 |
| requestedAt / approvedAt | String | 요청/승인 시각 |
| totalAmount | long | 총액 |
| note | String | 비고 |
| lines[].lineNo | int | 라인 번호 |
| lines[].sku | String | 부품 식별자 |
| lines[].nameSnapshot | String | 부품명 스냅샷 |
| lines[].unitPriceSnapshot | int | 단가 스냅샷 |
| lines[].quantity | int | 수량 |

### 주요 에러 케이스
| status | title | detail | 발생 조건 |
| --- | --- | --- | --- |
| 404 | S001 | 해당 번호의 SO를 찾을 수 없습니다. | 존재하지 않는 soNumber |
| 500 | S002 | Sales 서비스 호출에 실패했습니다. | Sales 서비스 다운/타임아웃 |
