# 택배 추적 연동 BE 설계서

- 작성일: 2026-10-04 (rev.3 — 스마트택배 조회 API · 폴링 방식으로 확정)
- 선행 문서: `dev/브랜드 주문관리/34_파트너센터_주문관리_BE_설계서.md` (0-4 · 3-3 · P6 · §34-13 #16 · N1)
- 범위: `DeliveryTrackerPort` 실구현(스마트택배) → 감시 배치 폴링 일정 변경 → 활성화 절차
- 범위 밖: 웹훅 · 추적 API(연 계약), 송장 자동 발번, 반품 회수 접수, 소비자 앱의 배송 이벤트 타임라인 노출

34 설계서가 「연동 업체 스펙 미확정(#16)」으로 남긴 자리를 채우는 문서다. 포트·감시 배치·전이 쿼리는 이미 구현돼 있고(`NoopDeliveryTracker`가 붙은 상태), 이 문서는 **그 포트 뒤에 무엇을 꽂을지**만 다룬다. 이행 상태 전이 규칙은 34 설계서 3-3을 그대로 따른다.

---

## 0. 결정 사항

| 항목 | 결정 |
| --- | --- |
| 업체 | **스마트택배 API(스윗트래커) — 조회 API** |
| 요금제 | **FREE**(0원)로 시작. 같은 송장 일 최대 조회 10회 |
| 방식 | **폴링만.** 웹훅 · 추적 API를 쓰지 않는다 |
| 폴링 일정 | **02 ~ 06시를 빼고 2시간 간격** — 00 · 06 · 08 · 10 · 12 · 14 · 16 · 18 · 20 · 22시, 하루 10회 |

하루 10회 폴링은 FREE의 송장당 일 한도 10회를 정확히 다 쓴다. 여유가 0이라는 점이 이 설계의 전제이고, 1절에서 그 결과를 다룬다.

**FREE는 조회 가능 송장이 100건이다.** 송장당 10회와 별개인 총량 한도이고, 이를 넘으면 조회가 전부 막힌다(에러 103). 이 한도가 런칭 물량을 감당하는지가 가장 먼저 확인할 일이다(1-2 · 7절 #1).

---

## 1. 한도와 폴링 일정

### 1-1. 송장당 일 10회

| 조회 발생원 | 하루 횟수 |
| --- | --- |
| 감시 배치 | 10 (00 · 06 ~ 22시 짝수 시각) |
| 송장 등록 시 형식 검증 | 1 (등록일 하루만, 검증을 켰을 때) |

- 등록일에는 등록 시각 이전의 폴링이 없으므로 「검증 1 + 남은 폴링」이 10을 넘지 않는다. 가장 이른 경우(00시 폴링 직후 등록)도 검증 1 + 06 ~ 22시 9회 = 10이다.
- 넘는 경우는 **같은 송장을 하루에 두 번 이상 검증할 때**뿐이다(등록 실패 후 재시도 등). 이때는 그날 마지막 폴링이 에러 105(동일 송장 일 한도 초과)를 받는다. 어댑터는 105를 「이번 회차 판정 없음」으로 처리하고 다음 날 00시 폴링이 이어받는다.
- 일 한도가 자정(KST)에 초기화된다고 가정한 계산이다. 초기화 시각은 공개 문서에 없어 실호출로 확인한다(6절 1단계).
- 운영자가 수동으로 재조회하는 기능은 만들지 않는다. 여유가 없다.

유료 등급은 송장당 일 20회라 1시간 간격까지 줄일 수 있다. 폴링 일정을 cron 설정값으로 빼 두므로(4절) 등급을 올릴 때 코드를 고치지 않는다.

### 1-2. 조회 가능 송장 수

| 등급 | 월 요금 | 조회 가능 송장 | 같은 송장 일 최대 |
| --- | --- | --- | --- |
| FREE | 0원 | 100건 | 10회 |
| STARTER | 50,000원 | 1,000건 | 20회 |
| BASIC | 80,000원 | 5,000건 | 20회 |
| PREMIUM | 150,000원 | 10,000건 | 20회 |
| PLATINUM | 300,000원 | 20,000건 | 20회 |
| VIP | 450,000원 | 30,000건 | 20회 |

2026-10-04 검색 결과 기준이다. 「조회 가능 송장 100건」이 하루 단위인지 이용권 기간(월) 단위인지 자료마다 달라 확정하지 못했다. API에 이용권 사용량 조회(`/api/v1/key/usage` — `totalAmount` · `leftAmount` · `startDate` · `endDate`)가 있으므로 가입 직후 호출해 단위를 확인한다.

어느 쪽이든 **FREE로 감시할 수 있는 송장은 100건 안팎이 상한**이다. 한도를 넘으면:

- 에러 103(키 사용량 초과)이 오고, 그 뒤 조회는 전부 실패한다.
- 배송완료 자동 전환이 멈추고 배송중에 쌓인다 — Noop 스텁 상태와 같아진다.
- 구매확정 타이머가 시작되지 않는다(배송완료가 기점이므로).

대응은 3-4의 사용량 감시와 등급 상향뿐이다. 송장 형식 검증도 조회 1건을 쓰므로, 검증을 켜면 한도가 그만큼 빨리 찬다.

### 1-3. 폴링 공백의 영향

| 구간 | 반영 지연 |
| --- | --- |
| 06 ~ 24시 | 최대 2시간 |
| 00 ~ 06시 | 최대 6시간 (00시 폴링 뒤의 변화는 06시에 반영) |

`delivered_at`은 폴링 시각이 아니라 택배사 이벤트 시각으로 기록되므로 구매확정 D+7 계산은 지연의 영향을 받지 않는다. 지연되는 것은 화면의 상태 표시뿐이다.

---

## 2. 스마트택배 API 스펙

Base URL `https://info.sweettracker.co.kr` · UTF-8 · 시각은 KST.

| API | 경로 | 용도 |
| --- | --- | --- |
| 운송장 조회 | `POST /api/v1/trackingInfo` — `t_key` · `t_code` · `t_invoice` (GET은 deprecated) | 추적 · 형식 검증 |
| 택배사 목록 | `POST /api/v1/companylist` | 택배사 코드 확정(1회) |
| 이용권 사용량 | `POST /api/v1/key/usage` | 한도 감시 |

**응답 주요 필드** — `level`(진행 단계), `complete`(배송완료 여부), `trackingDetails[]`(이력), `lastDetail` · `lastStateDetail` · `firstDetail`. 이력 항목은 `time`(long) · `timeString` · `level` · `kind`(진행 상태 문구) · `code`(배송 상태 코드) · `where`.

| `level` | 의미 |
| --- | --- |
| 0 | 택배사에 스캔 정보 없음 |
| 1 | 배송 준비중 |
| 2 | 집화 완료 |
| 3 | 배송중 |
| 4 | 지점 도착 |
| 5 | 배송 출발 |
| 6 | 배송 완료 |

| 에러 코드 | 의미 |
| --- | --- |
| 101 | 발급된 고유키가 존재하지 않음 |
| 102 | 만료된 키 |
| 103 | 키 사용량 초과 |
| 104 | 유효하지 않은 운송장 번호 혹은 택배사 코드 |
| 105 | 동일한 운송장의 하루 요청 제한 건수 초과 |
| 106 | 운송장 번호 조회 에러 |

### 택배사 코드

`DeliveryCarrier.trackerCode`(현재 전부 `null`)에 스마트택배의 `t_code`를 넣는다. 코드 목록은 API 키가 있어야 조회되므로 아래 값은 **검증하지 못한 참고값**이다. 가입 후 `companylist` 응답으로 11종 전부를 확정한다.

| enum | 라벨 | t_code (미검증) |
| --- | --- | --- |
| `CJ` | CJ대한통운 | `04` |
| `LOTTE` | 롯데택배 | `08` |
| `HANJIN` | 한진택배 | `05` |
| `EPOST` | 우체국택배 | `01` |
| `KYUNGDONG` | 경동택배 | `23` |
| `DAESIN` | 대신택배 | `22` |
| `LOGEN` | 로젠택배 | `06` |
| `HAPDONG` | 합동택배 | `32` |
| `COUPANG` | 쿠팡택배 | 미확인 |
| `WOORI` | 우리택배 | 미확인 |
| `CU` | CU편의점택배 | `46` |

`companylist`에 없는 택배사가 나오면 34 설계서 원칙(「연동 지원 택배사만 노출」)에 따라 enum에서 빼야 하고, 이는 기획 확인 사항이다(7절 #3). `trackerCode == null`인 택배사는 어댑터가 `validateInvoice → UNAVAILABLE`, `track → empty`를 돌려주며, 기동 시 WARN 로그를 한 번 남긴다.

---

## 3. 어댑터

### 3-1. 구성

```
global/delivery/tracker/
├── DeliveryTrackerPort.java              (기존 — 변경 없음)
├── NoopDeliveryTracker.java              (기존)
└── sweettracker/
    ├── SweetTrackerDeliveryTracker.java  포트 구현 — 응답 → ValidationResult / TrackSnapshot 변환
    ├── SweetTrackerClient.java           HTTP 호출만 (RestClient)
    ├── SweetTrackerUsageMonitor.java     이용권 사용량 감시
    └── dto/
```

- `SweetTrackerDeliveryTracker`는 `@ConditionalOnProperty(delivery.tracker.enabled=true)` — Noop과 정확히 반대 조건이라 빈이 항상 하나다.
- HTTP 클라이언트는 전용 `RestClient` 빈을 만든다(connect 3초 · read 10초). 기존 공용 `RestTemplate` 빈은 타임아웃이 없어서 쓰지 않는다.
- API 키는 폼 본문(`application/x-www-form-urlencoded`)의 `t_key`로 나간다. URL에 싣지 않으므로 통신 오류 메시지에 섞이지 않는다. 업체가 폼 본문을 받는지는 실호출로 확인한다(6절 1단계) — 쿼리 파라미터만 받으면 `SweetTrackerClient`를 바꾸고 URL 로깅을 막는다.
- 포트 시그니처는 그대로다(`DeliveryTrackerBlockedException`만 추가). 기존 테스트 스텁에 영향이 없다.
- 이력 시각은 `timeString`(KST 문자열)을 먼저 읽고, 없으면 `time`(epoch · 초/밀리초 자동 판별)으로 읽는다.

### 3-2. `track` → `TrackSnapshot`

| 응답 | `lastEventAt` | `deliveredAt` |
| --- | --- | --- |
| `level` 0 또는 이력 0건 | `null` | `null` |
| `level` 1 ~ 5 | 마지막 이력의 `time` | `null` |
| `level` 6 또는 `complete == true` | 〃 | 마지막 이력의 `time` |

| 에러 · 장애 | 반환 |
| --- | --- |
| 104 (유효하지 않은 운송장) | `lastEventAt == null`인 스냅샷 — 집화 전 송장이 이 코드로 올 수 있다. 24시간 뒤 「집화 확인 필요」로 이어진다 |
| 105 (송장 일 한도) | `Optional.empty()` — 판정하지 않고 다음 회차에 맡긴다 |
| 103 (키 사용량 초과) · 101 · 102 | `Optional.empty()` + **회차 중단**(3-4) |
| 106 · 타임아웃 · 5xx | `Optional.empty()` |

- `time`은 epoch 값이다. `Asia/Seoul` 기준 `LocalDateTime`으로 바꾼다. 단위(초 / 밀리초)는 실응답으로 확인한다.
- `returnDetected` · `returnCompleted`는 3-5 참조.

### 3-3. `validateInvoice`

조회 API를 한 번 호출해 판정한다. 전용 검증 API는 없다.

| 조회 결과 | 반환 |
| --- | --- |
| 정상 응답(`level` 0 포함) | `VALID` |
| 104 | `INVALID` — 하드 차단(§34-5) |
| 그 외 에러 · 타임아웃 · `trackerCode == null` | `UNAVAILABLE` — 「검증 생략」으로 기록 |

**기본값은 끔(`validation-enabled: false`)이다.** 꺼져 있으면 호출 없이 `UNAVAILABLE`을 돌려준다. 이유는 둘이다.

1. **104가 「형식 오류」만 뜻하는지 확인되지 않았다.** 에러 문구가 「유효하지 않은 운송장 번호 혹은 택배사 코드」인데, 집화 전의 정상 송장에도 104를 주는 택배사가 있다면 **정상 송장 등록이 하드 차단된다.** 택배사별로 실호출해 `level` 0과 104가 구분되는지 본 뒤에 켠다(6절 1단계).
2. **검증 1회가 한도를 쓴다.** 송장당 일 10회 중 1회, 조회 가능 송장 100건 중 1건이다.

꺼 둔 동안 오타 송장은 등록을 통과하고, 24시간 뒤 「집화 확인 필요」 배지로 드러난다 — 34 설계서의 기존 안전망이 그대로 받는다.

검증을 켤 경우 엑셀 일괄 등록(최대 1,000행)은 행마다 순차 호출하면 요청이 타임아웃 난다. `SellerOrderCommandService`가 행 루프 앞에서 병렬 선조회(동시 10 · 전체 20초 예산, 초과분은 `UNAVAILABLE`)하도록 바꾼다. FREE에서는 100건 한도 때문에 일괄 검증 자체가 성립하지 않으므로, 이 작업은 검증을 켜는 시점으로 미룬다.

### 3-4. 사용량 감시

한도 초과가 조용히 지나가면 배송완료 전환이 멈춘 것을 아무도 모른다.

- **회차 중단**: `track`이 103 · 101 · 102를 받으면 어댑터가 `DeliveryTrackerBlockedException`을 던지고, 감시 배치는 그 회차의 남은 대상을 호출하지 않고 끝낸다. 다음 회차는 처음부터 다시 돈다. `log.error` 1회(Sentry로 간다). `validateInvoice`는 이 예외를 던지지 않고 `UNAVAILABLE`을 돌려준다.
- **사전 경고**: `SweetTrackerUsageMonitor`가 매일 09시에 `/api/v1/key/usage`를 호출해 `leftAmount / totalAmount`가 20% 밑이면 `log.error`를 남긴다. 사용량 조회가 한도를 쓰는지는 실호출로 확인한다.

### 3-5. 반송 감지

`level`에 반송 단계가 없고 API 문서에도 반송 · 미배달 언급이 없다. **1차에서는 `returnDetected` · `returnCompleted`를 항상 `false`로 둔다.**

- 반송된 송장은 이벤트가 멈추거나 계속 갱신되다가 발송지 「배송완료」로 끝난다. 앞의 경우는 7일 뒤 「추적 정지」 배지로 드러난다. 뒤의 경우는 **배송완료로 자동 전환돼 구매확정 타이머가 돈다** — 소비자가 받지 못한 주문이 구매확정될 수 있다. 이 구간은 소비자 문의 · 운영자 수동 처리로 받아야 한다.
- 이력의 `kind` 문구나 `code` 값으로 반송을 추정하는 방법이 있으나, 택배사마다 값이 달라 실제 반송 송장 샘플 없이는 만들 수 없다. `returnCompleted` 오탐은 환불 큐를 잘못 세우므로 추정으로 붙이지 않는다.

34 설계서는 반송 자동 감지를 런칭 범위로 적고 있어 기획 확인이 필요하다(7절 #2).

---

## 4. 감시 배치 변경 — `OrderDeliveryTrackingScheduler`

| 항목 | 현재 | 변경 |
| --- | --- | --- |
| 기동 | `fixedDelay` 30분 | **cron `0 0 0,6-22/2 * * *` · zone `Asia/Seoul`** |
| 대상 | id 오름차순 첫 300건 | **대상 전량** — id 커서로 300건씩 끝까지 돈다 |
| 건별 처리 | `track` → `applyTracking` | 같음. 건 사이 100ms 간격 |

**기동을 cron으로 바꾸는 이유.** 「하루 정확히 10회」가 한도와 맞물려 있다. `fixedDelay`는 회차 소요 시간만큼 뒤로 밀리고 재배포 때마다 기준 시각이 바뀌어 횟수를 보장하지 못한다. `poll-interval-ms` · `initial-delay-ms` 설정은 없앤다.

**대상 전량 순회는 현재 코드의 결함 수정이다.** `findTrackingTargets`가 매 회차 id 오름차순 첫 `batchSize`건만 가져오므로, 배송중이 300건을 넘으면 뒤쪽 송장은 영원히 조회되지 않는다(주석의 「남은 건 다음 회차가 처리한다」가 성립하지 않는다). 리포지토리 쿼리에 `g.id > :afterId` 조건을 더하고, 스케줄러가 마지막 id를 넘기며 빈 페이지가 나올 때까지 반복한다. 컬럼 추가는 없다.

**회차 소요 시간.** 순차 호출이라 건당 약 0.5초로 잡으면 1,000건에 8분, 10,000건에 80여 분이다. 2시간 간격 안에 끝나지만, 22시 회차가 02시를 넘길 일은 없는지 등급을 PREMIUM 이상으로 올릴 때 다시 본다(그때는 동시 호출을 넣는다). 한 인스턴스에서만 돈다는 전제는 기존 스케줄러들과 같다.

**시간 경과 판정은 그대로다.** 「등록 후 24시간 이벤트 없음」 · 「마지막 이벤트 후 7일」은 `applyTracking`이 매 회차 판정한다.

### 설정

```yaml
delivery:
  tracker:
    enabled: ${DELIVERY_TRACKER_ENABLED:false}
    poll-cron: "0 0 0,6-22/2 * * *"            # 02~06시 제외 2시간 간격 — 하루 10회(FREE 송장당 일 한도)
    batch-size: 300                             # 페이지 크기 — 한 회차에 대상 전량을 이 크기로 나눠 돈다
    call-gap-ms: 100                            # 건 사이 호출 간격
    pickup-alert-hours: 24
    stall-alert-days: 7
    api-url: https://info.sweettracker.co.kr
    api-key: ${DELIVERY_TRACKER_API_KEY:}
    validation-enabled: false                   # 104 판정 검증 후 켠다(3-3)
    usage-alert-ratio: 0.2                      # 잔여 사용량 경고 기준
```

`enabled=true`인데 `api-key`가 비면 **기동을 실패시킨다.** 조용히 Noop처럼 도는 것보다 낫다.

---

## 5. 테스트

| 대상 | 방식 |
| --- | --- |
| 어댑터 매핑 | `MockRestServiceServer`로 응답 고정 — `level` 0 ~ 6 · 에러 101 ~ 106 · 타임아웃 × `track` · `validateInvoice` |
| 시각 변환 | 이력 `time` → KST `LocalDateTime` |
| 검증 스위치 | `validation-enabled=false`면 호출 0회 · `UNAVAILABLE` |
| 회차 중단 | 103 이후 남은 대상을 호출하지 않는지 · 다음 회차에 풀리는지 |
| 대상 전량 순회 | 배송중이 `batchSize`를 넘을 때 한 회차에 전량 조회되는지(기존 `OrderFulfillmentSchedulerIntegrationTest` 확장) |
| cron | 표현식이 하루 10회 · 02 ~ 05시 미포함인지(`CronExpression`으로 24시간 전개) |

기존 테스트는 포트를 스텁으로 갈아 끼우는 구조라 영향이 없다. `IntegrationTest`의 `delivery.tracker.enabled` 기본값(false)도 그대로 둔다. 스케줄러 생성자 인자가 바뀌면 `OrderFulfillmentSchedulerIntegrationTest`의 `trackingScheduler(...)` 헬퍼만 맞춘다.

---

## 6. 구현 순서

2 · 3단계는 구현됐다(2026-10-04). 1단계 스파이크는 API 키가 없어 하지 못했고, 코드는 API 명세만 보고 짰다 — **연동을 켜기 전에 1단계를 반드시 거친다.**

| 단계 | 작업 | 산출 |
| --- | --- | --- |
| 1 | **스파이크(미완)** — FREE 키 발급 후 실호출로 ① `companylist`로 택배사 11종 코드(코드에 넣은 9종 대조 · 쿠팡 · 우리택배 확인) ② `key/usage`로 한도 단위(일 / 월)와 사용량 조회의 차감 여부 ③ 집화 전 정상 송장이 `level` 0인지 104인지(택배사별) ④ 이력 `timeString` 형식 · `time` 단위 ⑤ 송장 일 한도 초기화 시각 ⑥ 폼 본문 요청 수용 여부 · 에러 응답 형식(`status=false`) | 2 · 3절 미확정 항목 확정 |
| 2 | **(완료)** `DeliveryCarrier.trackerCode` 채움 · `DeliveryTrackerProperties` 변경 · `SweetTrackerClient` · `SweetTrackerDeliveryTracker` | 포트 실구현 |
| 3 | **(완료)** 스케줄러 cron 전환 · 대상 전량 순회 · 회차 중단 · `SweetTrackerUsageMonitor` | 4절 · 3-4 |
| 4 | 스테이징에서 실송장으로 검증 후 `DELIVERY_TRACKER_ENABLED=true` | 런칭 게이트(P6) 해제 |
| (후속) | 104 판정이 확인되면 `validation-enabled=true` + 엑셀 일괄 등록 병렬 선조회 | 3-3 |

---

## 7. 확인이 필요한 것

| # | 내용 | 누가 | 막는 것 |
| --- | --- | --- | --- |
| 1 | **FREE의 조회 가능 송장 100건이 런칭 물량을 감당하는지.** 넘으면 추적이 전부 멈춘다. 물량 예상에 따라 시작 등급을 정한다 | 사용자 | 런칭 |
| 2 | 반송 자동 감지를 1차에서 빼는 것(3-5) — 반송 후 발송지 배송완료가 구매확정으로 이어질 수 있다 | 기획 | 런칭 |
| 3 | `companylist`에 없는 택배사가 나오면 enum에서 뺄지 | 기획 | 런칭 |
| 4 | FREE 키의 상업적 이용 가능 여부 — 이용약관 확인 | 사용자 | 런칭 |
| 5 | §34-13 #17(24시간 · 7일) — 여전히 대기 중이면 설정값 그대로 간다 | 기획 | 없음 |

## 8. 34 설계서와의 차이

이 문서가 확정되면 34 설계서에 반영할 항목이다.

- 0-4 · 3-3: 「연동 업체 스펙 미확정」 → 스마트택배 조회 API 확정, 본 문서 참조.
- 3-3 감시 배치: 주기 「30분」 → 「02 ~ 06시 제외 2시간 간격(cron)」, 대상 「첫 300건」 → 「전량 순회」.
- 3-3 표의 `returnDetected` · `returnCompleted` 행: 「1차 미연동」 표기(#2 확정 시).
- §34-5 송장 형식 검증: 「1차 검증 생략 — 104 판정 확인 후 활성화」 표기.
- §34-13 #16 종결, N1 종결.

## 9. 검토했던 다른 업체

| 업체 | 제외 이유 |
| --- | --- |
| Delivery Tracker (tracker.delivery) | 달러 결제 |
| 택배API (deliveryapi.co.kr) | 원화 · 반송 상태 · 형식 판정 · HMAC 웹훅을 갖췄으나 개인사업자 운영 · SLA 없음. 스마트택배로 결정 |
| 스마트택배 추적 API | 연 단위 계약 · 후불. 조회 API 폴링으로 대체 |

포트 뒤 어댑터만 바꾸면 업체를 교체할 수 있다.

## 참고 자료

- 스마트택배 API: https://tracking.sweettracker.co.kr/
- 스마트택배 배송조회 API 문서: https://info.sweettracker.co.kr/apidoc
- API 명세(Swagger JSON): https://info.sweettracker.co.kr/v2/api-docs
- 스마트택배 API 사용기: https://weezip.treefeely.com/post/use-sweettracker-api-for-delivery-information
