# 앱 주문 내역 · 주문 상세 (C10 · C10-1) — 백엔드 기능 설계서

- 근거 시안: `dev/뷰티 공동구매 앱 홈 디자인/C10 주문 내역.dc.html`(1a 목록) · `C10-1 주문 상세.dc.html`(1a~1e)
- 상류 설계: `dev/결제 기능 구현 계획서.md`(주문·결제·전액 취소) · `dev/브랜드 주문관리/34_파트너센터_주문관리_BE_설계서.md`(하위주문 이행 상태 · 취소 요청 테이블 · 환불 큐) · `dev/브랜드 주문취소, 환불/35_파트너센터_반품교환관리_BE_설계서.md`(반품·교환 클레임 · 구매확정 보류 — **이하 35 설계서**)
- 범위: 소비자 앱의 **주문 내역 목록 조회(신규)** · **주문 상세 조회(기존 API 확장)** — 항목별 표시 상태(**반품·교환 진행 상태 포함**) · 상태 보조 문구 · 취소·반품 반려 줄 · 항목별 액션 · 배송지 마스킹 · 결제 정보
- 범위 밖: 주문 취소 폼·취소 요청 생성·철회·취소 상세(C10 시안 1b·1c·1d) · 배송 조회(C10-2) · 반품·교환 **신청·회수 송장 입력·재배송비 결제·클레임 상세**(앱 클레임 설계서 — 도메인 진입점은 35 설계서 3-7에 있다) — **조회 응답은 이들을 전부 수용하는 모양으로 만들고, 쓰기 흐름은 후속 설계서로 뺀다**(7절에 지금 보이는 충돌을 적어 둔다)
- 작성일: 2026-10-04 · 개정: 2026-10-04(35 설계서 반영 — 반품·교환 표시 상태 · 구매확정 보류 · 쿼리 1회 추가) · 2026-10-04(배송중 「도착 예정」 확정 — U1 · 컬럼 1개 추가)

---

## 0. 먼저 정한 6가지

### 0-1. 이 설계가 만드는 것은 컬럼 하나다 — 마이그레이션 1건(`picked_up_at`)

목록·상세가 그리는 값은 전부 이미 저장돼 있다 — `orders` / `order_delivery_group`(이행 상태·시각) / `order_product`(취소 메타) / `order_cancel_request`(+item) / `order_refund_task`. 반품·교환이 읽는 `order_claim` · `order_product.returned_quantity` · `order_delivery_group.confirm_restart_at`은 **35 설계서의 마이그레이션**(번호는 V157 이후 — V154~V156은 이미 쓰였다) 이 만든다 — 클레임 표시(1-1의 #2~#4)는 그 배포와 함께 켜진다. 이 설계는 **조회 조립만** 더한다 — 예외는 도착 예정일의 기준인 `order_delivery_group.picked_up_at`(V156 · 1-2) 하나다.

`orders (user_id, …)` 전용 인덱스도 걸지 않는다 — `uk_orders_user_idempotency_key`가 `user_id` 선두라 「내 주문」 범위 스캔을 받고, 한 사람의 최근 6개월 주문은 정렬이 파일소트로 떨어져도 작다.

### 0-2. 목록의 행은 **주문 항목(`order_product`)**, 묶음은 **주문(`orders`)**

시안: 「주문일·주문번호 그룹 머리 아래로 항목(옵션) 단위 행」. 서버 3층 중 가운데 층(하위주문 = `order_delivery_group`)은 **응답 구조에 드러내지 않는다** — 항목이 자기 그룹의 이행 상태를 물려받아 행마다 상태를 갖는다. 브랜드가 다른 두 항목의 상태가 갈리는 것(C10-1 1c)이 이렇게 자연히 나온다.

브랜드명은 항목 행마다 `order_delivery_group.market_name` 스냅샷을 반복해 싣는다.

### 0-3. 항목 「표시 상태」는 **저장하지 않고 조회 때 유도**한다

34 설계서 0-2·1-6과 35 설계서 0-1이 정한 대로 상태 축이 넷이다 — 이행은 그룹(`fulfillment_status`), 취소·반품의 사실은 항목(`order_product.status`), 취소 요청은 별도 테이블, 반품·교환은 또 별도 테이블(`order_claim`). 소비자 화면의 한 줄짜리 상태는 이 넷의 **합성값**이라 컬럼으로 두면 넷 중 하나가 바뀔 때마다 동기화가 필요해진다. 유도 규칙은 1-1 한 곳(`UserOrderItemAssembler`)에만 두고 목록·상세가 같이 쓴다.

### 0-4. 라벨·색·보조 문구·버튼은 **서버가 내린다**

공구·계약·파트너센터 주문과 같은 규칙(`statusLabel` · `statusTone` · `actions`). 앱이 상태→색·버튼 매핑을 따로 들면 「상품준비중에 취소 요청 버튼을 두느냐」 같은 기획 변경(7절 미결 다수)이 앱 배포가 된다.

색은 2단뿐이다(시안 「굵기 통일 · 색으로만 층」) — `ACTIVE`(로즈 — 지금 볼·할 것이 있다) / `MUTED`(#737373 — 개입 불가). 파트너센터용 `OrderBadgeTone` 5종을 재사용하지 않는다(의미 축이 다르다).

### 0-5. 액션은 **API가 실재하는 것만** 내린다

시안의 버튼 중 지금 서버에 있는 것은 **전액 주문 취소 하나**다(`POST /v1/user/orders/{orderId}/cancel`). 나머지(취소 요청 · 배송 조회 · 반품/교환 · ○○ 상세)는 enum과 노출 규칙은 여기서 확정하되, **해당 API가 생기는 배포에서 켠다** — 조립기의 `ENABLED_ACTIONS`(EnumSet) 한 줄. 앱이 눌러도 갈 곳 없는 버튼을 그리지 않는다.

### 0-6. 목록에는 **결제된 적 있는 주문만** 나온다

`orders.paid_at IS NOT NULL`. 결제 대기(30분 뒤 만료)·만료·결제 전 취소 주문은 시안에 상태 자체가 없다. 파트너센터 목록(34 설계서 1-2)과 같은 전제다. 결제 후 취소된 주문은 `paid_at`이 남아 있으므로 「취소」 행으로 나온다.

상세(`GET /{orderId}`)는 결제 전 주문도 지금처럼 열린다 — 결제 완료 화면·결제 재시도가 쓴다.

---

## 1. 표시 모델

### 1-1. `UserOrderItemStatus` — 항목 표시 상태

`domain/order/type/UserOrderItemStatus.java`. 유도 순서는 **위에서부터 먼저 맞는 것**.

| # | 조건 | enum | 라벨 | 톤 | 탈색(`dimmed`) |
| --- | --- | --- | --- | --- | --- |
| 1 | `order_product.status = CANCELLED` | `CANCELLED` | 취소 | MUTED | ✅ |
| 2 | `order_product.status = RETURNED`(전량 반품 — 검수 통과 시점) | `RETURNED` | 반품 | MUTED | ✅ |
| 3 | 항목에 **진행 중**(`status <> COMPLETED`) 반품 클레임 | `RETURN_IN_PROGRESS` | 반품 | ACTIVE | ❌ |
| 4 | 항목에 **진행 중** 교환 클레임 | `EXCHANGE_IN_PROGRESS` | 교환 | ACTIVE | ❌ |
| 5 | 항목이 `PENDING` 취소 요청의 대상(`order_cancel_request_item`) | `CANCEL_REQUESTED` | 취소 요청중 | MUTED | ❌ |
| 6 | 그룹 `NEW` | `PAID` | 결제완료 | ACTIVE | ❌ |
| 7 | 그룹 `PREPARING` | `PREPARING` | 상품준비중 | MUTED | ❌ |
| 8 | 그룹 `SHIPPING` | `SHIPPING` | 배송중 | ACTIVE | ❌ |
| 9 | 그룹 `RETURNING` | `RETURNING` | 반송중 | MUTED | ❌ |
| 10 | 그룹 `DELIVERED` | `DELIVERED` | 배송완료 | ACTIVE | ❌ |
| 11 | 그룹 `CONFIRMED` 또는 항목 `PURCHASE_CONFIRMED` | `CONFIRMED` | 구매확정 | MUTED | ❌ |
| 12 | 그 외(결제 전 — 상세에서만 도달) | `PAYMENT_PENDING` | 결제 대기 | MUTED | ❌ |

- #5가 #6~7보다 앞이다 — 요청이 걸린 항목은 이행 상태가 NEW/PREPARING이어도 「취소 요청중」이다. **같은 그룹의 요청 밖 항목은 원래 상태 그대로** 나온다(부분 요청).
- #9 `RETURNING`(택배 반송 — 배송 실패. 소비자가 신청하는 **반품과 다른 사건**이다)은 시안에 없다 — 발생은 하므로 라벨만 두고 액션 없이 내린다(미결 U3).
- 그룹 없는 옛 행(리뷰 시드 백필 전 · `delivery_group_id IS NULL`)은 항목 상태만으로 판정한다 — `PURCHASE_CONFIRMED` → #11, `PAID` → #6(액션 없음).
- **#2~#4는 35 설계서의 클레임이 원천이다**(시안의 「반품 · 진행 중」 「교환 · 재발송」 행 — 라벨이 앞, 1-2의 보조 문구가 뒤).
  - #3·#4가 #10보다 앞이다 — 클레임은 배송완료(`DELIVERED`) 위의 오버레이라, 진행 중이면 「배송완료」 대신 클레임을 말한다. **같은 그룹의 신청 밖 항목은 「배송완료」 그대로**다.
  - #2가 #3보다 앞이다 — 전량 반품 항목은 검수 통과 순간 `RETURNED`가 되고 클레임은 환불 집행 전(`REFUND_PENDING`)이라 아직 진행 중이다. 그때의 표시는 「반품 · 환불 처리 중」(1-2)이다.
  - **수량 부분 신청**(2개 중 1개)도 항목 행은 하나라 상태도 하나다 — 진행 중 클레임이 있으면 그것이 이긴다. 한 항목에 진행 중 클레임이 둘이면(1개 반품 + 1개 교환) **가장 최근 신청 건**으로 표시한다.
  - 클레임이 종결되면 항목은 원래 줄로 돌아간다 — 교환 완료·거절 종결 → #10(→ #11), 수량 일부만 환불된 항목 → #10(`returnedQuantity`만 남는다 · 1-5).
  - 톤이 `ACTIVE`인 이유 — 진행 중 클레임에는 소비자가 볼·할 것이 있다(회수 송장 입력 · 재배송비 결제 · 진행 확인).

### 1-2. 상태 보조 문구 — `statusSub`

「그 상태에서 궁금한 날짜 하나」(시안). 서버가 완성 문자열로 내리고, 앱이 다른 형식을 원할 때를 위해 원시 시각(1-5의 `dates`)도 같이 싣는다. 날짜는 **KST 기준 `MM.dd`**.

| 상태 | 목록 | 상세 | 출처 |
| --- | --- | --- | --- |
| `PAID` · `PREPARING` | `09.15 발송 예정` | 같음 | `ship_due_at` — 없으면 null |
| `SHIPPING` | `09.17 도착 예정` / null | 같음 | 아래 「도착 예정일」 — 집화 전·예정일 경과는 null(「배송중」만) |
| `RETURNING` | `반송 처리 중` | 같음 | 고정 |
| `DELIVERED` | `09.23 구매확정 예정` | `09.16 14:20`(배송완료 시각) | 목록 = `confirmDueAt`(아래) · 상세 = `delivered_at` |
| `CONFIRMED` | `09.23 확정` | 같음 | `confirmed_at` — 옛 행은 null |
| `CANCEL_REQUESTED` | `브랜드 확인 중` | 같음 | 고정 |
| `CANCELLED` | `완료` / `환불 처리 중` | 같음 | 아래 |
| `RETURN_IN_PROGRESS` · `EXCHANGE_IN_PROGRESS` | 클레임 단계 문구 | 같음 | 아래 — `order_claim.status` |
| `RETURNED` | `환불 처리 중` / `완료` | 같음 | 그 항목의 반품 클레임에 `REFUND_PENDING`이 남아 있으면 `환불 처리 중`, 전부 `COMPLETED`면 `완료` |

`CANCELLED`의 완료 판정 — 항목 `cancel_type`으로 가른다.

| cancel_type | 판정 |
| --- | --- |
| `CONSUMER`(PG 자동) | 항상 `완료` — 항목이 CANCELLED로 내려간 시점이 곧 PG 취소 확인 뒤다(결제 계획서 5-6) |
| `REQUEST_APPROVED` · `SELLER_DIRECT` | 그 그룹의 `order_refund_task`에 `PENDING`이 남아 있으면 `환불 처리 중`, 아니면 `완료` — 환불은 운영자 집행 큐를 탄다(34 설계서 1-9) |

**클레임 단계 문구** — 35 설계서 1-2의 저장 상태 9종을 소비자 말로 옮긴다. 시안이 보여 준 것은 「진행 중」 「재발송」 둘뿐이라 나머지는 잠정이다(미결 U9).

| `order_claim.status` | 반품 | 교환 |
| --- | --- | --- |
| `REQUESTED` | `상품을 보내 주세요` | 같음 |
| `COLLECTING` | `회수 중` | 같음 |
| `ARRIVED` · `RECEIVED` | `검수 중` | 같음 |
| `REFUND_PENDING` | `환불 처리 중` | — |
| `RESHIP_READY` · `RESHIPPING`(거절 아님) | — | `재발송 준비 중` · `재발송` |
| `REJECT_HOLD` | `반려 · 재배송비 결제 대기` | 같음 |
| `RESHIP_READY` · `RESHIPPING`(거절 반송 — `rejected_at` 존재) | `반려 · 반송 준비 중` · `반려 · 반송 중` | 같음 |

**도착 예정일(`arrivalDueDate`)** — 택배 연동이 도착 예정을 주지 않으므로 직접 센다(`DeliveryArrivalEstimator`).

| 상황 | 보조 문구 | `dates.arrivalDueDate` |
| --- | --- | --- |
| 집화 전(`picked_up_at` 없음) | null — 「배송중」만 | null |
| 집화 후 | `MM.dd 도착 예정` — 집화일 + N배송일 | 그 날짜 |
| 예정일이 지났는데 아직 배송중 | null — 틀린 날짜를 계속 보여 주지 않는다 | null |

- **집화 시각** — `order_delivery_group.picked_up_at`. 추적 배치가 본 **첫 이벤트 시각**을 1회만 적는다(집화 전에는 추적 데이터가 없다 — `TrackSnapshot.lastEventAt`). 포트에 필드를 더하지 않는다. 송장이 수정되면 NULL 로 돌아간다.
- **배송일** — 일요일과 공휴일(`showroomz.business-calendar.holidays`)을 뺀다. **토요일은 센다** — 영업일(`BusinessCalendar.addBusinessDays`)과 다른 달력이다. 집화일은 세지 않는다.
- **N** — 기본 3(`order.arrival-default-days`). 택배사별로 **실제 소요일(집화 → 배송완료, 배송일 기준)** 의 평균(올림)이 있으면 그것으로 보정한다.
  - 집계 테이블은 없다 — 하위주문의 `picked_up_at` · `delivered_at`이 곧 쌓이는 데이터다. 최근 90일 · 추적이 확인한 배송완료(`delivered_source = TRACKER`)만 본다.
  - 표본 30건 미만인 택배사는 보정하지 않는다(기본값). 집화와 완료를 같은 날 본 표본은 버린다.
  - 평균은 메모리에 두고 6시간마다 다시 읽는다 — 목록 조회마다 집계하지 않는다.

**구매확정 예정일(`confirmDueAt`)의 기준** — 35 설계서 3-6·5-1의 공용 계산을 그대로 쓴다. 셀러 목록의 `confirmDueDays`와 다른 날짜가 나오면 안 된다.

| 그룹의 상태 | `confirmDueAt` | 목록 보조 문구 |
| --- | --- | --- |
| 진행 중 클레임 없음 | `COALESCE(confirm_restart_at, delivered_at)` + `order.purchase-confirm-days` — 교환 완료 뒤에는 재발송 도착일부터 다시 센다 | `09.23 구매확정 예정` |
| **진행 중 클레임 있음**(그 그룹의 어느 항목이든) | `null` — 클레임이 구매확정을 그룹째 세운다. 보류 중인 날짜를 약속하지 않는다 | null(신청 밖 「배송완료」 항목도 날짜가 사라진다) |

시안의 「공구 마감 후 09.26 발송 예정」은 **발송 방식(마감 후 일괄 발송)이 미결**(34 설계서 7절 #3)이라 접두 문구 없이 `ship_due_at`만 쓴다. 확정되면 이 표 한 줄이 바뀐다.

### 1-3. 금액 표기

| 상태 | `amount` | `amountLabel` |
| --- | --- | --- |
| `CANCELLED` | `price × quantity`(환불 대상액) | `환불 18,900원` |
| `RETURNED` | 그 항목 반품 클레임의 환불액 합 — 집행됐으면 `refunded_amount`, 아니면 `refund_expected_amount` | `환불 17,800원` |
| 그 외 | `price × quantity` | `17,500원` |

「환불은 배지가 아니라 결과」(시안). 그룹 전체 취소 때 함께 돌아가는 배송비는 항목 행에 얹지 않는다 — 항목의 금액이 아니다(취소 상세의 몫).

반품 환불액은 `price × quantity`와 다를 수 있다 — 소비자 부담 사유는 반품비가 빠진다(35 설계서 1-8). 그래서 `RETURNED`는 단가 계산이 아니라 **클레임의 금액**을 쓴다. 진행 중(#3·#4)과 수량 일부 반품은 `그 외` 줄이다 — 항목 금액을 그대로 두고, 환불 사실은 `returnedQuantity`와 클레임 상세가 말한다(미결 U10).

### 1-4. 반려 줄 — `cancelRejection` · `claimRejection`

시안: 반려되면 상태 라벨은 실제 상태 그대로, 그 아래 회색 한 줄 「취소 요청 반려 · 사유 보기 ›」.

- 대상: 이 항목이 든 `REJECTED` 요청 중 **가장 최근 것**. 같은 항목에 그 뒤 `PENDING`·`APPROVED` 요청이 있으면 싣지 않는다(상태가 이미 그것을 말한다).
- 노출 구간: 표시 상태가 `PAID`·`PREPARING`·`SHIPPING`·`DELIVERED`일 때만. `CONFIRMED`에서 사라진다 — 「반려 사실은 받은 뒤 반품할 수 있다로 이어질 때만 의미」(시안)이고, 반품 창이 닫히는 시점이 곧 구매확정이다.
- 내용: `{ cancelRequestId, rejectedAt }` — 사유 본문은 싣지 않는다. 「사유 보기」의 목적지는 취소 상세(C10-5)다.

**반품·교환 반려 줄 — `claimRejection`** — 같은 원리다. 검수 거절이 **종결**(`COMPLETED(REJECTED)` — 반송 완료 또는 보관 종료)되면 항목은 「배송완료」로 돌아가고, 그 아래 회색 한 줄 「반품 요청 반려 · 사유 보기 ›」(교환이면 「교환 요청 반려」)를 싣는다.

- 대상: 이 항목의 거절 종결 클레임 중 **가장 최근 것**. 그 뒤에 신청한 진행 중 클레임이 있으면 싣지 않는다(상태가 이미 말한다). 거절 **보류**(`REJECT_HOLD`) 동안은 줄이 아니라 상태 자체가 「반품 · 반려 · 재배송비 결제 대기」다.
- 노출 구간: 표시 상태 `DELIVERED`일 때만 — `CONFIRMED`에서 사라진다(취소 반려 줄과 같은 이유).
- 내용: `{ claimId, type, rejectedAt }` — 거절 사유·브랜드 증빙은 클레임 상세의 몫이다(약관 제20조③의 전달 창구).
- 취소 반려 줄과 겹치면 **클레임 쪽 하나만** 싣는다 — 더 나중 사건이다.

### 1-5. 항목 행 DTO — 목록·상세 공용

`api/app/order/dto/UserOrderDto.ItemRow`

```
orderProductId · productId · variantId
brandName(market_name 스냅샷) · productName · optionName · quantity · returnedQuantity · thumbnailUrl
status(UserOrderItemStatus) · statusLabel · statusTone(ACTIVE|MUTED) · statusSub(nullable)
dimmed
amount · amountLabel
cancelRejection: { cancelRequestId, rejectedAt } | null
cancelRequestId(nullable)      — CANCEL_REQUESTED 일 때 검토 중 요청. [취소 상세]의 대상
claim: { claimId, type(RETURN|EXCHANGE), claimStatus, quantity, exchangeOptionName } | null
                               — RETURN_IN_PROGRESS · EXCHANGE_IN_PROGRESS · RETURNED 일 때. [반품·교환 상세]의 대상
claimRejection: { claimId, type, rejectedAt } | null
dates: { shipDueAt, shippedAt, arrivalDueDate, deliveredAt, confirmDueAt, confirmedAt, cancelledAt }   — 전부 nullable
actions: [ { type, label, enabled } ]
```

- 시각은 기존 주문 API와 같은 규약(`OrderDto.TIME_PATTERN` · KST).
- `returnedQuantity`는 환불로 끝난 수량(`order_product.returned_quantity`)이다 — 0이면 앱은 그리지 않는다. `claim.quantity`는 **신청 수량**이라 주문 수량과 다를 수 있다(2개 중 1개).
- `confirmDueAt`은 진행 중 클레임이 있는 그룹에서 null이다(1-2).
- `optionName`과 `quantity`는 따로 내린다 — 「단품 · 1개」 조합은 앱이 한다(장바구니·주문서와 같다).

### 1-6. 액션 — `UserOrderAction`

| type | 라벨 | 목적지 | API 현황 |
| --- | --- | --- | --- |
| `CANCEL` | 주문 취소 | C10-4 주문 취소 | **있음** — 전액 취소만 |
| `CANCEL_REQUEST` | 취소 요청 | C10-4b | 없음(후속) |
| `TRACK_DELIVERY` | 배송 조회 | C10-2 | 없음(후속) |
| `RETURN_EXCHANGE` | 반품 · 교환 | 목록 전용 → 주문 상세 | 화면 이동뿐 — 반품·교환 모듈과 함께 켠다 |
| `RETURN_REQUEST` / `EXCHANGE_REQUEST` | 반품 요청 / 교환 요청 | 상세 전용 | 도메인 진입점만 있다(35 설계서 3-7 `request`) — 앱 신청 API와 함께 켠다 |
| `CLAIM_DETAIL` | 반품 상세 / 교환 상세(유형으로 라벨이 갈린다) | 클레임 상세 | 없음(앱 클레임 설계서) |
| `CANCEL_DETAIL` | 취소 상세 | C10-5 | 없음(후속) |

상태별 노출(목표 상태 — 0-5의 `ENABLED_ACTIONS`로 걸러서 나간다):

| 상태 | 목록 | 상세 |
| --- | --- | --- |
| `PAID` | `CANCEL` | `CANCEL` |
| `PREPARING` | `CANCEL_REQUEST` | `CANCEL_REQUEST` |
| `SHIPPING` | `TRACK_DELIVERY` | `TRACK_DELIVERY` |
| `DELIVERED` | `TRACK_DELIVERY` · `RETURN_EXCHANGE` | `TRACK_DELIVERY` · `RETURN_REQUEST` · `EXCHANGE_REQUEST` |
| `CONFIRMED` | `TRACK_DELIVERY` | `TRACK_DELIVERY` |
| `CANCEL_REQUESTED` · `CANCELLED` | `CANCEL_DETAIL` | `CANCEL_DETAIL` |
| `RETURN_IN_PROGRESS` · `EXCHANGE_IN_PROGRESS` · `RETURNED` | `CLAIM_DETAIL` | `CLAIM_DETAIL` |
| `RETURNING` · `PAYMENT_PENDING` | — | — |

- 이 표는 **시안의 목업 데이터** 기준이다. 시안 주석은 같은 파일 안에서 다르게 말한다(결제완료·상품준비중에 [배송 조회]를 두느냐 — 미결 U2). 서버가 표를 소유하므로 확정되면 여기만 고친다.
- 인라인 액션은 목록 2개 · 상세 3개까지(시안). [구매확정] 버튼은 없다(시안이 뺐다).
- `enabled=false` + 사유 라벨(「교환 불가 (재고 없음)」)은 `EXCHANGE_REQUEST` 전용이다 — **같은 상품의 다른 옵션 중 재고가 있는 것이 하나도 없을 때**. 교환은 신청 때 재고를 선점하므로(35 설계서 3-8) 이 판정이 신청 API의 `CLAIM_EXCHANGE_OUT_OF_STOCK`과 같은 기준이어야 한다. **상세에서만** 판정한다(쿼리 1회 · 3-1) — 목록은 `RETURN_EXCHANGE` 하나라 필요 없다.
- `DELIVERED` 줄의 반품·교환 버튼에 **잔여 수량 조건을 따로 걸지 않는다** — 표시 상태가 `DELIVERED`라는 것이 이미 「진행 중 클레임 없음 · 전량 반품 아님」이고, 그러면 잔여(`quantity − returned_quantity`)는 1 이상이다. 구매확정 뒤(`CONFIRMED`)에는 붙지 않는다 — 사후 클레임을 받지 않는다(35 설계서 3-1).
- 회수 송장 입력·재배송비 결제는 **항목 행의 버튼으로 두지 않는다** — `CLAIM_DETAIL`로 들어간 클레임 상세 안의 액션이다(인라인 상한 2·3개를 지킨다).

**`CANCEL`의 게이트는 지금의 취소 API와 같아야 한다.** 현행 취소는 주문 전체 · 전 그룹 NEW일 때만이다(`CheckoutService.claimUserCancel` — `countPreparedByOrder == 0`). 그래서 `PAID` 항목이라도 `CANCEL`은 **주문 단위 `cancellable`이 참일 때만** 붙인다(`OrderAssembler.isCancellable`을 그대로 쓴다). 한 주문 안에 준비 시작된 그룹이 섞이면 NEW 쪽 항목은 「결제완료」로 보이되 버튼이 없다 — 앱이 버튼을 그리고 서버가 409를 내는 것보다 낫다. 항목 단위 부분 취소가 생기면(후속) 이 게이트가 항목 단위로 내려간다.

---

## 2. 주문 내역 목록 — `GET /v1/user/orders` (신규)

### 2-1. 요청

| 파라미터 | 기본 | 비고 |
| --- | --- | --- |
| `page` | 1 | 앱 목록 공통 규약(`PageResponse` · `pageInfo`) |
| `size` | 20 | 1~50 — 밖이면 400 `ORDER_PAGE_SIZE_INVALID`(기존 코드 재사용) |

- 페이지 단위는 **주문**이다(행이 아니다) — 그룹 머리가 페이지 경계에서 쪼개지지 않는다.
- 필터 파라미터를 두지 않는다 — 시안이 필터를 뺐다. 마이 탭 배송 상태 카운터의 「해당 상태만 보기」는 미결(U5)이라 지금 만들지 않는다.

### 2-2. 조회 범위·정렬

```
WHERE o.user_id = :userId
  AND o.paid_at IS NOT NULL                 -- 0-6
  AND o.created_at >= :now - 6개월          -- order.user-list-months (기본 6)
ORDER BY o.order_id DESC
```

6개월은 설정값으로 뺀다(`OrderProperties.userListMonths`) — 「6개월 이전 주문 조회 방식」이 시안 미결이라 상수로 박지 않는다. 마지막 페이지 하단 문구(「최근 6개월 주문까지 보여드려요」)는 앱 고정 문구다.

### 2-3. 응답

```json
{
  "content": [
    {
      "orderId": 812,
      "orderNumber": "20260912-000201",
      "orderedAt": "2026-09-12T10:21:07",
      "items": [
        {
          "orderProductId": 1501, "productId": 77, "variantId": 301,
          "brandName": "라보에이치", "productName": "시카 리페어 앰플 30ml 리필 2개 세트 기획",
          "optionName": "30ml + 리필 2개", "quantity": 1, "thumbnailUrl": "https://…",
          "status": "SHIPPING", "statusLabel": "배송중", "statusTone": "ACTIVE", "statusSub": "09.17 도착 예정",
          "dimmed": false, "amount": 24900, "amountLabel": "24,900원",
          "cancelRejection": { "cancelRequestId": 31, "rejectedAt": "2026-09-14T16:20:05" },
          "cancelRequestId": null,
          "dates": { "shipDueAt": "…", "shippedAt": "…", "arrivalDueDate": "2026-09-17", "deliveredAt": null, "confirmDueAt": null, "confirmedAt": null, "cancelledAt": null },
          "actions": []
        }
      ]
    }
  ],
  "pageInfo": { "currentPage": 1, "totalPages": 1, "totalResults": 7, "limit": 20, "hasNext": false }
}
```

- 빈 상태 = `content: []` · `totalResults: 0`. 빈 화면 문구·[공구 구경하기]는 앱 몫이다.
- 주문번호는 저장값 그대로(`yyyyMMdd-NNNNNN`) — 시안의 `20260912030201`은 목업 표기다. 목록과 상세가 같은 문자열을 쓰는 것이 요건이고(시안 「표기가 어긋나면 같은 주문인지 확인하는 단계가 생긴다」), 둘 다 `orders.order_number`를 그대로 내리므로 충족된다.
- 항목 순서는 `order_product_id` 오름차순(주문서에 담긴 순서).
- 배송지·결제 정보는 목록에 싣지 않는다.

### 2-4. 쿼리 계획 — 고정 6회 · N+1 없음

| # | 쿼리 | 비고 |
| --- | --- | --- |
| 1 | 주문 페이지(id · 번호 · 시각 · 상태 · `paid_payment_id`) + count | `OrderRepository.findPaidByUser(userId, since, pageable)` |
| 2 | 항목 — `order_product` JOIN FETCH `delivery_group` WHERE `order_id IN (…)` | 신규 `OrderProductRepository.findByOrderIdsWithGroup` |
| 3 | 취소 요청 — `order_cancel_request` + items WHERE `order_id IN (…)` AND `status IN (PENDING, REJECTED)` | 신규 `findOpenOrRejectedByOrderIds`. `order_id` 비정규화 컬럼이 이 용도로 있다(34 설계서 1-5) |
| 4 | 환불 큐 — `order_refund_task` WHERE `order_id IN (…)` AND `status = PENDING` → 그룹 id 집합 | 신규. 「환불 처리 중」 판정(1-2) |
| 5 | 결제 — `payment` WHERE `payment_id IN (paid_payment_id…)` | `cancellable` 판정(결제 상태 PAID 여부) |
| 6 | 클레임 — `order_claim` WHERE `order_id IN (…)` | 신규 `OrderClaimRepository.findByOrderIds`. 상태 조건 없이 전부 읽는다 — 진행 중(#3·#4) · 거절 종결(`claimRejection`) · 환불액(1-3) · 그룹의 구매확정 보류(1-2)를 한 번에 푼다. `order_id` 비정규화 컬럼이 이 용도다(35 설계서 1-1) |

#3~#5는 대상이 있을 때만 돈다(취소·요청이 없는 페이지는 빈 IN을 보내지 않는다). #6은 `DELIVERED`·`CONFIRMED` 그룹이나 `RETURNED` 항목이 있는 페이지에서만 돈다 — 클레임은 배송완료 뒤에만 생긴다.

---

## 3. 주문 상세 — `GET /v1/user/orders/{orderId}` (기존 확장)

### 3-1. 엔드포인트를 새로 만들지 않고 **필드를 더한다**

같은 리소스에 엔드포인트 둘을 두지 않는다. 기존 응답(`OrderDto.OrderDetailResponse`)은 결제 완료 화면·문의 주문 카드가 쓰고 있으므로 **기존 필드는 전부 그대로 두고** 아래를 추가한다 — 앱 구버전이 깨지지 않는다.

| 추가 필드 | 내용 |
| --- | --- |
| `items[]` | 1-5의 `ItemRow` 평면 목록(상세용 `statusSub`·`actions`) — 시안 「주문 상품 N」이 쇼룸 그룹 없이 평면이다. 기존 `groups[].items[]`는 유지 |
| `itemCount` | `items.size()` — 「주문 상품 2」 |
| `maskedAddress` | 3-2 |
| `notices[]` | 3-3 |
| `summary.discountRate` | 3-4 |

상세의 `items[]`는 목록과 같은 조립기를 타되 `EXCHANGE_REQUEST`의 재고 판정(1-6)만 더 한다 — `DELIVERED` 항목이 있을 때 그 상품들의 옵션 재고를 한 번에 읽는다.

소유 검증은 지금 그대로다 — 남의 주문은 403 `ORDER_ACCESS_DENIED`, 없으면 404 `ORDER_NOT_FOUND`.

### 3-2. 배송지 마스킹 — 서버가 한다

시안: 「김수* · 010-\*\*\*\*-5678 · 주소 뒷부분 \*\*\*\*\*\*」(스크린샷 공유 대비). 앱에서 가리면 원문이 응답에 그대로 실려 다니므로 **마스킹된 값을 따로 내린다.**

```
maskedAddress: { recipientName, phoneNumber, address, detailAddress, memo }
```

| 값 | 규칙 |
| --- | --- |
| 수취인 | 마지막 글자 → `*`(김수진 → 김수\*). 1글자면 그대로 |
| 연락처 | 가운데 블록 → `****`(010-\*\*\*\*-5678). 하이픈 없는 값은 뒤 4자리만 남긴다 |
| 주소 | 도로명 주소(`address`)는 그대로, 상세 주소(`detail_address`)는 통째로 `******`. 상세 주소가 비면 null |
| 요청사항 | 그대로(`delivery_memo`) |

- 유틸은 `api/app/order/service/OrderAddressMasker`로 둔다. `AdminUserMasker`는 어드민 회원 화면의 규칙(가운데 마스킹 등)이 달라 공유하지 않는다.
- 기존 `deliveryAddress`(원문)는 **남긴다** — 결제 완료 화면이 쓴다. C10-1은 `maskedAddress`만 그린다. 원문 필드를 응답에서 빼는 것은 앱이 `maskedAddress`로 옮긴 뒤의 별도 정리다(미결 U6).

### 3-3. 상단 안내 블록 — `notices[]`

시안 1b(회색 · 준비중 취소는 요청으로 접수)·1d(로즈 · 구매확정 기한). 조건 판정은 서버, 문구는 앱.

| type | 조건 | 부가값 | 톤 |
| --- | --- | --- | --- |
| `CONFIRM_DUE` | `DELIVERED` 항목 중 **`confirmDueAt`이 있는 것**이 1개 이상 | `date` = 그중 가장 이른 `confirmDueAt` | ACTIVE |
| `CANCEL_BY_REQUEST` | `PREPARING` 항목이 1개 이상 **그리고 `CANCEL_REQUEST` 액션이 켜져 있다** | — | MUTED |

- 둘 다 해당하면 둘 다 내린다(순서: `CONFIRM_DUE` 먼저 — 기한이 있는 쪽).
- `CONFIRM_DUE`는 반품·교환이 진행 중인 그룹의 항목을 **세지 않는다**(1-2 — 날짜가 null이다). 전 그룹이 보류면 안내 자체가 없다. 기한이 멈춘 동안 날짜를 내리면 거짓이 된다.
- `CANCEL_BY_REQUEST`를 액션에 묶는 이유 — 취소 요청 API가 없는 동안 「요청으로 접수돼요」라고 안내하면 거짓이다(0-5).

### 3-4. 결제 정보

| 시안 줄 | 필드 | 출처 |
| --- | --- | --- |
| 상품 금액 | `summary.productTotal` | `orders.product_total`(정가 합) |
| 공동구매 할인 | `summary.discountTotal` | `orders.discount_total` |
| 배송비 | `summary.deliveryFeeTotal` | 0이면 앱이 「무료배송」 |
| 결제 금액 | `summary.totalAmount` | |
| 할인율(53%) | `summary.discountRate` **(추가)** | `DiscountRate.of(productTotal, productTotal − discountTotal)` — 상품가 기준, 배송비 제외 |
| 결제수단 | `payment.methodLabel` | 기존 |

금액은 **주문 시점 값**이다 — 부분 취소가 있어도 줄어들지 않는다. 부분 취소된 주문의 금액 표기 규칙은 시안 미결이고(U4), 현행 전액 취소만으로는 발생하지 않는다(브랜드 승인·직권 취소로는 발생한다 — 그때도 항목 행의 「환불 N원」으로 사실은 보인다). 반품 환불도 같은 처리다 — 결제 정보는 그대로, 사실은 항목 행(1-3)이 말한다.

### 3-5. 시안 요소 ↔ 응답 대응

| 시안 | 응답 |
| --- | --- |
| 26.09.12 (금) / 주문번호 + 복사 | `orderedAt` · `orderNumber` |
| 배송지 「주문 시점 정보」 | `maskedAddress`(주문 스냅샷) |
| 주문 상품 N + 항목 카드(상태 · 우측 보조 · 반려 줄 · 버튼) | `itemCount` · `items[]` |
| 결제 정보 | `summary` · `payment` |
| 구매확정 화면 하단 [1:1 문의] | 앱 고정 — 문의 생성 API에 `orderId`를 넘긴다(기존) |

---

## 4. 코드 구성

패키지 `showroomz.api.app.order.{controller, docs, dto, service}` — 기존 주문 패키지에 더한다.

| 대상 | 내용 |
| --- | --- |
| `domain/order/type/UserOrderItemStatus` | 신규 enum(1-1) — 라벨·톤(`UserOrderTone`) 보유 |
| `domain/order/type/UserOrderAction` | 신규 enum(1-6) — 라벨 보유. `CLAIM_DETAIL`은 유형별 라벨 2종 |
| `api/app/order/dto/UserOrderDto` | 신규 — `ItemRow` · `Action` · `CancelRejection` · `Claim` · `ClaimRejection` · `Dates` · `OrderCard`(목록 묶음) · `MaskedAddress` · `Notice` |
| `api/app/order/service/UserOrderItemAssembler` | 신규 — 상태 유도 · 보조 문구 · 반려 줄 · 액션. **목록·상세가 같은 메서드를 탄다.** 입력은 미리 읽어 둔 맵(항목 · 요청 · 환불 큐 그룹 · **항목별 클레임 · 구매확정 보류 그룹** · 주문별 cancellable) — 내부에서 쿼리하지 않는다 |
| `api/app/order/service/UserOrderQueryService` | 신규 — 목록(2-4) · `@Transactional(readOnly = true)` |
| `api/app/order/service/OrderAddressMasker` | 신규(3-2) |
| `OrderAssembler.toDetail` | 수정 — `items` · `itemCount` · `maskedAddress` · `notices` · `summary.discountRate` 추가. `isCancellable`을 목록도 쓰도록 패키지 공개로 연다 |
| `OrderDto.OrderDetailResponse` · `OrderDto.Summary` | 수정 — 필드 추가(3-1 · 3-4) |
| `OrderController` · `OrderControllerDocs` | 수정 — `GET /v1/user/orders` 추가. 문서 어노테이션은 docs 인터페이스에(프로젝트 규약) |
| `OrderRepository` · `OrderProductRepository` · `OrderCancelRequestRepository` · `OrderRefundTaskRepository` · `OrderClaimRepository` | 조회 메서드 추가(2-4) |
| 구매확정 예정일 계산 | 35 설계서 5-1이 모으는 공용 메서드를 **부른다** — 여기서 `delivered_at + N일`을 따로 계산하지 않는다 |
| `OrderProperties` · `application.yml` | `user-list-months: 6` · `user-list-page-size-max: 50` |
| `domain/order/service/DeliveryArrivalEstimator` | 신규 — 도착 예정일(1-2). 배송일 계산 · 택배사별 평균 캐시 |
| `OrderDeliveryGroup.pickedUpAt` · `OrderDeliveryGroupRepository` | `touchTracking`이 첫 이벤트 시각을 적고 `updateInvoice`가 지운다 · `findTransitSamples` 추가 |
| Flyway | `V156__alter_order_delivery_group_add_picked_up_at` — 클레임 테이블·컬럼은 35 설계서 몫이다(V154·V155는 다른 작업이 썼으므로 그 번호는 V157부터로 밀린다) |
| `@Hidden` 기획 제외 영역 | **접촉 없음** |

에러 코드는 신규가 없다 — `ORDER_NOT_FOUND` · `ORDER_ACCESS_DENIED` · `ORDER_PAGE_SIZE_INVALID` 재사용(마지막 것의 메시지 「1~100」은 셀러 기준이라, 앱 목록은 메시지를 넘겨 「1~50」으로 낸다).

---

## 5. 구현 순서

| 단계 | 내용 | 비고 |
| --- | --- | --- |
| P1 | enum 2종 · `UserOrderDto` · `UserOrderItemAssembler` + 단위 테스트(1-1 유도표 전 행) | 쿼리 없이 검증된다 |
| P2 | 목록 API(리포지토리 메서드 · `UserOrderQueryService` · 컨트롤러 · docs) | 이 시점에 화면이 선다 |
| P3 | 상세 확장(`toDetail` · 마스킹 · notices · discountRate) | 기존 필드 불변 확인 |
| P4 | 통합 테스트(6절 #1~15) | |
| P5 | 클레임 반영 — 표시 상태 #2~#4 · `claim` · `claimRejection` · `confirmDueAt` 보류 · 쿼리 #6 + 테스트 #16~24 | **35 설계서 P1·P2(테이블 · 구매확정 연동) 뒤** — 그 전에는 읽을 테이블이 없다. 35 설계서 P6과 같은 단계다 |

`ENABLED_ACTIONS`의 초기값은 `{ CANCEL }`이다. 후속 설계(취소 요청 · 배송 조회 · 반품/교환)가 각자 자기 액션을 켠다. P5는 **표시만** 넣는다 — `RETURN_EXCHANGE` · `RETURN_REQUEST` · `EXCHANGE_REQUEST` · `CLAIM_DETAIL`은 앱 클레임 API가 생기는 배포에서 켠다. 그전에도 클레임은 생길 수 있고(어드민·테스트 경로) 그때 항목이 「배송완료」로 보이면 거짓이므로 표시가 액션보다 먼저 간다.

---

## 6. 테스트

`UserOrderItemAssembler` 단위 + `UserOrderQueryIntegrationTest`(`SellerOrderQueryIntegrationTest`와 같은 방식).

| # | 시나리오 | 기대 |
| --- | --- | --- |
| 1 | 결제 대기·만료·결제 전 취소 주문 | 목록에 없다 |
| 2 | 결제 후 전액 취소 주문 | 전 항목 `CANCELLED` · `dimmed` · 「환불 N원」 · 보조 `완료` |
| 3 | 한 주문 · 두 그룹(SHIPPING / PREPARING) | 항목별 상태가 갈린다(C10-1 1c) |
| 4 | 그룹 PREPARING · 항목 2개 중 1개만 취소 요청 PENDING | 요청 항목만 `CANCEL_REQUESTED`, 나머지 `PREPARING` |
| 5 | 요청 REJECTED 후 그룹 SHIPPING | 상태 `SHIPPING` + `cancelRejection` |
| 6 | #5에서 그룹 CONFIRMED | `cancelRejection` 없음 |
| 7 | REJECTED 뒤 같은 항목 재요청 PENDING | `CANCEL_REQUESTED` · `cancelRejection` 없음 |
| 8 | 요청 승인 · 환불 큐 PENDING → DONE | 보조 `환불 처리 중` → `완료` |
| 9 | 두 그룹 중 하나가 PREPARING인 주문의 NEW 항목 | 상태 `PAID` · `CANCEL` 액션 없음(1-6 게이트) |
| 10 | 전 그룹 NEW · 결제 PAID | 전 항목 `CANCEL` 액션 · 상세 `cancellable = true`와 일치 |
| 11 | 6개월 경계 · 페이지 경계 · `size` 0/51 | 범위 밖 제외 · 주문이 쪼개지지 않음 · 400 |
| 12 | 남의 주문 상세 | 403 — 목록에는 애초에 없다 |
| 13 | 상세 마스킹 — 1글자 이름 · 하이픈 없는 번호 · 상세 주소 없음 | 3-2 규칙 |
| 14 | 상세 기존 필드 | `groups` · `deliveryAddress` · `cancellable` 등 변화 없음 |
| 15 | 목록 쿼리 수 | 주문 20건 페이지에서 6회 이하 |
| 16 | 그룹 DELIVERED · 항목 2개 중 1개만 반품 신청 | 신청 항목 `RETURN_IN_PROGRESS` · 보조 `상품을 보내 주세요`, 나머지 `DELIVERED` — **둘 다 `confirmDueAt` null** |
| 17 | 수량 2 항목에 1개 반품 → 환불 완료 | 상태 `DELIVERED` 복귀 · `returnedQuantity = 1` · 금액은 항목 금액 그대로 |
| 18 | 전량 반품 검수 통과 → 환불 집행 | `RETURNED` · `dimmed` · 보조 `환불 처리 중` → `완료` · 「환불 N원」 = 예정액 → 집행액 |
| 19 | 교환 신청 → 재발송 등록 → 도착 | `EXCHANGE_IN_PROGRESS`(보조 `재발송`) → `DELIVERED` · `confirmDueAt` = 재발송 도착 + 7일 |
| 20 | 검수 거절 → 보류 → 반송 완료 | 보조 `반려 · 재배송비 결제 대기` → … → `DELIVERED` + `claimRejection` |
| 21 | #20에서 그룹 CONFIRMED / 같은 항목 재신청 | `claimRejection` 없음 |
| 22 | 한 항목에 진행 중 반품 1개 + 교환 1개 | 나중에 신청한 쪽으로 표시 |
| 23 | 상세 — 다른 옵션 재고 0 | `EXCHANGE_REQUEST.enabled = false`(액션이 켜진 설정에서) |
| 24 | 상세 `CONFIRM_DUE` — 두 그룹 중 하나만 클레임 진행 중 / 둘 다 | 보류 아닌 그룹의 날짜 / 안내 없음 |

기획 제외 영역은 테스트 범위에 넣지 않는다.

---

## 7. 미결

### 7-1. 이 설계 안의 확정 요청

| # | 항목 | 지금의 집행 | 확정 시 변경 |
| --- | --- | --- | --- |
| ~~U1~~ | ~~배송중 「도착 예정일」의 출처~~ — **확정(2026-10-04)**: 집화일 + 3배송일(일요일·공휴일 제외) · 택배사별 실제 소요일 평균으로 보정 · 집화 전과 예정일 경과는 날짜 없이 「배송중」만(1-2) | 구현됨 | 연동 업체가 도착 예정을 주게 되면 그 값으로 교체 |
| U2 | **결제완료·상품준비중의 버튼 구성** — C10 주석은 「결제완료 = [배송 조회]·[주문 취소], 상품준비중 = [배송 조회]」, C10 목업·C10-1 1b는 「[주문 취소] / [취소 요청] 하나」, C10-1 1c는 준비중에 [배송 조회] | 목업 데이터 기준(1-6) | 1-6 표 |
| U3 | **반송중(`RETURNING`)의 소비자 표기** — 시안에 없다 | 라벨 「반송중」 · 회색 · 액션 없음 | 라벨·액션 |
| U4 | 부분 취소된 주문의 결제 정보 표기(시안 미결 ④) | 주문 시점 금액 그대로 | `summary`에 환불 합계 필드 추가 |
| U5 | 마이 탭 배송 상태 카운터 탭 동작(시안 미결 ⑤) — 상태 필터 여부 | 필터 없음 | `status` 파라미터 + 카운터 API |
| U6 | 상세 응답의 원문 배송지(`deliveryAddress`) 제거 시점 | 유지(결제 완료 화면 사용) | 필드 제거 |
| U7 | 6개월 이전 주문 조회 방식(시안 미결 ①) | 설정값 6개월 · 그 이전은 조회 불가 | 기간 파라미터 |
| U8 | 발송 예정 문구의 「공구 마감 후」 접두 — 발송 방식 미결(34 설계서 #3) | `ship_due_at`만 | 1-2 표 |
| U9 | **반품·교환 단계 문구** — 시안에는 「반품 · 진행 중」 「교환 · 재발송」 둘뿐이고 회수 대기·검수·환불 대기·거절 보류·반송의 소비자 문구가 없다 | 1-2의 단계 문구 표(잠정) | 1-2 표 |
| U10 | **수량 일부 반품의 항목 표기** — 2개 중 1개가 환불된 항목의 수량·금액을 어떻게 그리나 | 상태·금액은 원래대로, `returnedQuantity`만 내린다 | `amountLabel` 규칙 · 보조 문구 |
| U11 | **구매확정 보류 중의 안내** — 클레임이 걸린 그룹은 신청 밖 항목까지 구매확정 예정일이 사라진다(35 설계서 3-6). 소비자에게 이유를 말할지 | 날짜 null · 안내 없음 | `notices`에 type 1종 추가 |
| U12 | 한 항목에 진행 중 클레임이 둘일 때의 표시 | 최근 신청 건 하나 | 1-1 주석 |

### 7-2. 후속 설계(주문 취소 — C10 1b·1c·1d)로 넘기는 것과, 지금 보이는 충돌

조회 설계에는 영향이 없지만, 취소 흐름을 설계하기 전에 정해야 한다. C2의 「수량 쪼개기」는 반품 쪽이 먼저 답을 냈다 — 35 설계서는 항목을 나누지 않고 `returned_quantity` 컬럼으로 받는다. 취소도 수량을 받게 되면 같은 방식이 선례다.

| # | 시안 | 현행 서버 | 필요한 결정 |
| --- | --- | --- | --- |
| C1 | 결제완료에서 **항목 선택 부분 취소** · 즉시 PG 취소 | 주문 전체 전액 취소만. `PortOnePaymentGateway.cancel`도 전액 전용 | PG 부분 취소 경로 + 주문 행·결제 행의 부분 취소 금액 모델 |
| C2 | **취소 수량 스테퍼**(2개 중 1개만) | `order_cancel_request_item`은 「항목 전량 — 수량 쪼개기 기획 없음」, `order_product`는 수량 분할 불가 | 수량 부분 취소를 받을지 — 받으면 항목 분할 또는 취소 수량 컬럼 |
| C3 | 부분 취소로 무료배송 기준 미만이 되면 **배송비 차감** | 34 설계서 1-10 「부분 취소 — 배송비 재계산 없음」 | 둘 중 하나로 통일(브랜드 승인 경로의 환불 예정액도 같이 바뀐다) |
| C4 | 반려 사유 **4종 코드 + 선택 상세** | `CancelRequestRejectRequest`는 자유 문자열 `reason` 하나, `reject_reason VARCHAR(500)` | 파트너센터 거부 API에 사유 코드 추가(컬럼 1개) |
| C5 | 「장바구니에 다시 담기」 | 없음 | 취소 트랜잭션 밖 후처리로 `CartService` 호출 — 공구 종료·품절 시 조용히 건너뛸지 |
| C6 | 취소 요청 **철회** | `CancelRequestStatus`에 철회 값 없음(`VOIDED`는 시스템 종료용) | `WITHDRAWN` 추가 여부 |
| C7 | 취소 사유 4택 | `CancelRequestReason` 4종과 일치 — 즉시 취소는 자유 문자열 `reason` | 즉시 취소도 코드로 받을지 |

### 7-3. 후속 설계(앱 반품·교환 — 신청 · 클레임 상세)로 넘기는 것

도메인과 브랜드 쪽은 35 설계서가 끝냈다. 앱 쪽에 남은 것:

| # | 항목 | 35 설계서의 현황 |
| --- | --- | --- |
| R1 | 신청 API(항목·수량·사유 5종·상세 내용·사진·교환 옵션) | 도메인 `OrderClaimService.request`(3-1) — **한 신청 = 한 하위주문 · 한 유형** 제약(N3)을 앱 화면이 받는지 |
| R2 | 회수 송장 입력·정정 화면(택배사 11종 · 형식 검증) · 반품 수취 주소 안내 | `registerCollectionInvoice` / `updateCollectionInvoice` · 주소는 collection 스냅샷(N4) |
| R3 | 재배송비·교환비 결제 | `order_claim_charge` · `markReshipFeePaid` — 결제 링크·PG 경로 미정(N6) |
| R4 | 클레임 상세(진행 단계 · 거절 사유·브랜드 증빙 열람 · 환불 내역) | 조회 API 없음 — 이 설계의 `CLAIM_DETAIL` 목적지 |
| R5 | 신청 철회 | 상태 없음 — 35 설계서 A-4(소비자 미발송 종결)와 같이 정해진다 |
