# 어드민 변경 요청 rev.6 → rev.7 — 인플루언서 사업자 구분 변경 편입 — 백엔드 수정 설계서

- 근거 기획: `dev/브랜드, 쇼룸 정보/브랜드 정보 변경/## 16. 어드민 변경 요청 ….txt`(§16 — `[v0.30]` 표시분: 16-0 배지 · 16-1 요청자 열 · 검색 · 유형 4종 · 16-2 제목 줄 · **16-2a 신설** · 16-5 반려 · 16-8 #9 ~ #13) · `dev/브랜드, 쇼룸 정보/스튜디오 계정관리/쇼룸 기본정보 관리 기능명세.txt`(§50-2 · **§50-4 T7 · T8** · T4 · T4a · T5 · T9 · T9a · T10)
- 근거 시안: `ui-admin-05-change-requests.html` **rev.6 → rev.7**(2026.10.14) · `ui-studio-06-account.html` rev.1 · `ui-partner-06-basic-info.html` rev.8 → rev.9(변경 요청 부분 변경 없음 — 0절)
- 기준 설계(구현 완료): `dev/완료/브랜드 기본정보/15-16_기본정보관리_BE_개발계획.md`(이하 **15-16 계획**) — 이 문서는 그 위에 얹는 **차분**이다. 15-16 계획의 규칙 중 여기서 언급하지 않은 것은 그대로 유지한다.
- 연계 설계: `53_어드민_인플루언서상세_BE_설계서.md`(0-7 · 0-9 — 「사업자 구분 변경은 자리만 둔다」 · 이 문서가 그 자리를 채운다) · `52_어드민_회원관리_브랜드상세_BE_설계서.md`(변경 요청 파생 이력) · `dev/완료/정산/44_어드민_정산관리_BE_설계서.md`(생성 시점 `creator_business_type` 스냅샷) · `44_토스페이먼츠_지급대행_연동_BE_설계서.md` 4-1(셀러 `businessType`)
- 범위: 어드민 변경 요청 큐에 **인플루언서 사업자 구분 변경 2유형**(사업자 전환 · 비사업자 전환)을 편입 — 도메인 일반화 · 어드민 목록 · 상세 · 승인 · 반려 변경 · 승인 반영 · 스튜디오 **접수 · 취소 · 확인 API(T7 · T8 최소분)** · 53 자리 채움 · 메일 2종
- 범위 밖: 스튜디오 정산 정보 탭 **조회 API 본체**(§50-2 — 이 문서의 배너 API를 거기에 끼운다) · 인플루언서 **계좌 변경**(§50-3 — 이 큐에 넣지 않는다 · 16-8 #9 ⓔ) · 국세청 사업자 상태 조회(16-8 #11 ⓖ) · 브랜드 변경 요청(M1 · M2) 동작 — **바꾸지 않는다**
- 구현 위치: `showroomz.domain.changerequest.*`(일반화) · `showroomz.api.admin.changerequest.*`(분기) · `showroomz.api.creator.changerequest.{controller, docs, dto, service}`(신설) · `Creator` 메서드 2개
- 마이그레이션: **V203** 하나 — 구현 계획서(`dev/브랜드, 쇼룸 정보/06-16-50_구현_계획서.md`) 2-1 재배정. V194 ~ V198 은 Flyway `out-of-order` 미설정이라 쓸 수 없다
- 작성일: 2026-10-16 · 기준 코드 `feat/add`(4eaec5d0 + 미커밋 작업분 — 52 · 53 구현 진행 중)
- 구현 상태: 구현 계획서 S5 로 구현 — **16 ↔ 50 이 겹치는 부분은 계획서 2-2 정본표가 우선**한다(어드민 계약 · 대조 규칙 = 16, 스튜디오 API · 마스킹 · 업로드 · 에러 코드 = 50). PG 재등록(S6)은 토스 답변 대기 — ✅ S5 구현(2026-10-16 · V203). 그 전까지 PG 셀러가 연결된 인플루언서 승인은 409 `CHANGE_REQUEST_PG_RESYNC_PENDING`(임시 가드). 메일은 `CreatorMemberNotifier` 스텁(5-4 의 `MailService` 메서드 대신 — 발송 인프라 없음)

---

## 0. 무엇이 바뀌었나 — rev.6(구현됨) ↔ rev.7 대조

시안 두 벌을 상태 단위로 비교했다(사이드바 제외 텍스트 diff). **브랜드 상세 · 미리보기 · 승인 · 반려(C3 · C4 · C5 · C6 · C7 · C8 · C9)는 글자까지 같다.** 바뀐 것은 목록 3상태(C1 · C2 · C10)와 신규 5상태(C3a · C3b · C5b · C7b · C8b)다.

| # | 영역 | rev.6 — 현재 코드 | rev.7 | BE 영향 |
| --- | --- | --- | --- | --- |
| 1 | 요청 주체 | 브랜드만(`brand_change_request.market_id NOT NULL`) | 브랜드 + **인플루언서** | 테이블 일반화(1-1 · 2-1) |
| 2 | 요청 유형 | 사업자 정보 · 정산 계좌 | + **사업자 전환**(비사업자 → 개인사업자) · **비사업자 전환**(개인사업자 → 비사업자) | `ChangeRequestType` 2값 추가 |
| 3 | 목록 1열 | 「브랜드」 — 브랜드명 | 「**요청자**」 — `브랜드` / `인플루언서` 배지 + 브랜드명 / 쇼룸명 | 행에 `requester { type, id, name }` |
| 4 | 검색 | 브랜드명 | 브랜드명 · **쇼룸명** | 검색 · 건수 쿼리 |
| 5 | 탭 건수 · GNB 배지 | 브랜드 건만(변경 요청 2 · 입점 관리 9) | 합산(**4 · 11**) | `countByStatusGroup` 조인 수정 · `summary`는 코드 그대로 의미만 확장 |
| 6 | 상세 제목 줄 | 브랜드명 + `[브랜드 상세 ↗]` | 인플루언서 건 = 쇼룸명 + `[인플루언서 상세 ↗]` | `requester.id` = `creatorId` |
| 7 | 우측 패널 · 처리 이력 주체 | 요청자 = 담당자 이름 스냅샷 · 「브랜드 파트너센터」 | 인플루언서 건 = 「인플루언서 본인」 · 「쇼룸 스튜디오」 | 분기(3-4) |
| 8 | 대조표 | 요청 항목 + 변경 없는 행 | + **파생 행** — 지급 방식 · 정산 계좌 예금주(규칙 판정) · 정산 계좌(마스킹) · 상호 · 대표자(서류로 확인) | `DiffRow.source` 추가(1-6) |
| 9 | 「**승인하면**」 카드 | 없음 | 적용 시점 · 기존 구분 유지 정산 N건(STL-) · 정산 계좌 변경 필요 여부 | `approvalImpact`(3-5) |
| 10 | 증빙 미리보기 대조 패널 | 사업자등록증(C5) · 통장 사본(C6) | + **폐업사실증명원**(C5b) — 대조 기준이 「요청 값」이 아니라 **본인확인 정보** | `compareItems` 추가(3-4) |
| 11 | 승인 모달 | C7 | + **C7b**(세무 처리가 바뀌는 승인 — 「승인하면」 재표시) | 상세 응답 재사용 · API 추가 없음 |
| 12 | 반려 정형 사유 | 사업자 정보 6종 · 정산 계좌 5종 | + **사업자 구분 축 6종**(C8b) | `ChangeRequestRejectReason` 5값 추가 · `OTHER` 4유형 지원 |
| 13 | 결과 전달 | 파트너센터 배너 + 메일(로그인 이메일) | 인플루언서 = **스튜디오 배너**(T4a · T5 · T9a · T10) + 메일(**업무 이메일**) | 스튜디오 API 신설(4절) · 메일 2종 |
| 14 | 승인 반영 대상 | `Seller` · `Market` | + `Creator` 사업자 구분 · 등록번호 · 등록증 · 주민등록번호 | 적용기 분기(3-6) |
| 15 | 회원 상세 연계 | 52 브랜드 상세 | + 53 인플루언서 상세 `pendingBusinessTypeChange` · 이력 「사업자 구분 변경 승인 / 반려」 | 53 자리 채움(5-1) |
| 16 | 페이지 설명 · 빈 상태 문구 · 규칙 문구(「브랜드가 → 요청자가 즉시 재요청」) | | 인플루언서 언급 | FE 상수 — BE 없음 |

**변경 없음(확인함)**

| 대상 | 결과 |
| --- | --- |
| 어드민 C3 · C4 · C5 · C6 · C7 · C8 · C9 | rev.6과 동일 — 브랜드 경로의 대조표 · 미리보기 · 승인 · 반려 규칙은 손대지 않는다 |
| 파트너센터 1-A ~ 2-F · M3 | rev.8 → rev.9 차이는 서브탭 「배송·반품 정책 · 준비중」 태그 제거뿐(배송 정책 — 이 문서 범위 밖) |
| 정렬 · SLA · 탭 · 부분 승인 없음 · 재요청 = 신규 행 · 의도적으로 두지 않는 것(유형 필터 등) | §16-1 · 16-4 · 16-6 그대로 — **요청 유형이 4종이 돼도 유형 필터는 두지 않는다**(§16-1 유지) |

---

## 1. 먼저 정한 12가지

### 1-1. 인플루언서 요청도 **같은 테이블**에 담는다 — `brand_change_request` 일반화

15-16 계획 1-1의 「유형별 테이블을 나누지 않는다 — 목록 · 상세 · 승인 · 반려가 같은 코드로 돈다」를 요청 주체에도 그대로 적용한다. 별도 테이블(`creator_business_type_change_request` — 53의 `creator_*` 계열과 모양은 맞는다)을 기각한 이유는 셋이다.

1. **목록 정렬과 이전/다음이 하나의 정렬이어야 한다**(§16-1 경과 내림차순 고정 · §16-2 현재 탭 순서). 두 테이블이면 목록 · 건수 · 이전/다음 id 목록이 전부 UNION 네이티브 쿼리가 된다.
2. **id 공간이 겹친다.** `GET /v1/admin/change-requests/{requestId}`가 두 테이블에서 같은 숫자를 가리키게 되어 경로를 바꿔야 한다(FE 계약 파괴).
3. **요청 코드가 공통 순번이다** — 시안 C1의 `CHG-2026-0081 ~ 0084`가 브랜드 · 인플루언서에 섞여 있다. 지금 채번(`countByRequestCodeStartingWith`)은 한 테이블 기준이다.

비용은 `market_id`가 NULL이 될 수 있다는 것 하나다 — 브랜드 전용 코드의 `getMarket()` 호출 지점을 1-12에서 전부 막는다.

### 1-2. 테이블 · 엔티티 이름은 **그대로** 둔다

`brand_change_request` · `BrandChangeRequest` · `BrandChangeRequestRepository`를 리네임하면 52 미커밋 작업(`BrandAdminHistoryReader` · `AdminBrandQueryService`)과 셀러 코드 전반이 함께 흔들린다. 클래스 주석과 마이그레이션 COMMENT에 「브랜드 + 인플루언서」를 명시하고, 리네임은 52 · 53이 머지된 뒤 별도 리팩터링으로 남긴다(9-2 #8).

### 1-3. 요청자 축은 유형에서 파생되지만 **컬럼으로도 저장**한다 — `requester_type`

`ChangeRequestType.requesterType()`만으로도 판단할 수 있지만, 검색 조인 · 건수 · CHECK 제약이 SQL에서 축을 읽어야 한다. 엔티티 팩토리가 「유형의 요청자 = 컬럼 값」을 강제하고, DB CHECK가 「BRAND ⇔ market_id · CREATOR ⇔ creator_id」를 강제한다(2-1).

### 1-4. 유형 이름은 **방향을 담는다** — `TO_BUSINESS` · `TO_INDIVIDUAL`

`CreatorBusinessType { INDIVIDUAL, BUSINESS }`의 **목표값**과 1:1이다(`targetBusinessType()`). 방향을 유형 하나(`BUSINESS_TYPE`)로 묶지 않은 이유 — 받는 값 · 증빙 라벨 · 대조 기준 · 승인 시 반영 동작이 방향마다 전부 다르고, 목록 「요청 유형」 열이 방향(`사업자 전환` / `비사업자 전환`)을 그대로 보여 준다. 라벨은 기존 A-0 규약대로 FE가 매핑한다.

### 1-5. 주민등록번호는 **항목 값에 싣지 않는다** — 암호문 컬럼 · 처리 즉시 비움

- `brand_change_request_item.requested_value`에는 **마스킹값만** 둔다(대조표 · 배너 표시용).
- 원문은 `PersonalDataCipher` 암호문으로 요청 행 `resident_number_enc`에 둔다. **승인 시 `Creator`로 옮긴 직후 · 반려 · 취소 시 즉시 `NULL`** — 엔티티의 `approve` · `reject` · `cancel` 세 전이가 스스로 비운다(호출부가 잊을 수 없게). 처리가 끝난 요청 행에는 주민번호 암호문이 남지 않는다.
- 마스킹 표기 불일치: 시안 C3b · §50-2는 「뒤 7자리 전부」(`950812-*******` — 성별 자리도 숨김), 기존 `ResidentRegistrationNumber.mask`는 `900101-1******`(성별 자리 노출)이고 온보딩이 이 형식으로 `Creator.residentRegistrationNumberMasked`에 저장 중이다. → ~~항목에는 기존 형식으로 저장하고 표시에서 가린다(`maskForDisplay`)~~ **[계획서 2-2 #10 — 50 정본]** 50 9-2 가 `ResidentRegistrationNumber.mask` 자체를 `900101-*******` 로 바꾸고 저장값을 백필한다(S3). 형식이 하나라 표시 함수를 두지 않는다.

### 1-6. 대조표의 **파생 행은 저장하지 않는다** — `DiffRow.source`로 행 성격을 내린다

저장하는 것은 요청자가 실제로 바꿔 달라고 한 값뿐이다(유형당 항목 2개). 지급 방식 · 예금주 판정 · 정산 계좌 · 상호 · 대표자는 **조회 시 계산**한다 — 브랜드 대조표가 사업자 유형 · 사업자등록번호 행을 `resolveLiveValue`로 그리는 것과 같은 방식이다. FE가 행마다 무엇을 그릴지 알 수 있게 `source`를 더한다.

| `source` | 뜻 | FE 표시 |
| --- | --- | --- |
| `REQUEST` | 요청 레코드의 항목(`brand_change_request_item`) | `changed`면 현재 값 취소선 → 요청 값 강조 |
| `LIVE` | 현재 회원 정보 — 이 요청으로 바뀌지 않음 | `변경 없음` · `locked`면 `변경 요청 불가` |
| `DERIVED` | 구분이 바뀌면 **따라 바뀌는** 값(지급 방식 · 비사업자 전환의 등록번호 「폐업」) | `REQUEST`와 같은 강조 |
| `DOCUMENT` | 서버가 **값을 갖지 않는** 행 — 서류로 운영자가 확인(상호 · 대표자) | `등록증으로 확인` 상수 + 「운영자 확인」 |
| `CHECK` | 규칙 판정 행(정산 계좌 예금주) | `holderCheck` 결과로 ✓ / ✕ |

브랜드 행은 `REQUEST` · `LIVE` 두 값만 쓴다 — **브랜드 응답은 필드 하나가 늘 뿐 값이 바뀌지 않는다.**

### 1-7. 「다음 정산부터」와 「심사 중 기존 구분 유지」는 **이미 성립한다** — 정산 코드 변경 없음

- `SettlementGenerationService`(L132 ~ 146)가 정산 **생성 시점**에 `creator.businessType`을 `settlement.creator_business_type`에 스냅샷한다(44 어드민 설계서 117행).
- 원천징수 · 부가세 · `PayoutBlockPolicy` · 인플루언서 세금계산서 문서 생성이 전부 **스냅샷**(`Settlement.isBusinessCreator()`)을 본다.
- 승인은 `Creator.businessType`만 바꾸고 기존 정산 행은 건드리지 않는다 → 승인 전에 생성된 정산(확인 기간 중 포함)은 기존 구분, 승인 뒤 생성되는 정산부터 새 구분. 심사 중에는 `Creator` 값이 그대로이므로 정산이 기존 구분으로 계속된다(§16-2a 「심사 중 정산」).

남는 일은 「승인하면」 카드에 **기존 구분이 유지되는 정산 목록을 보여 주는 것**뿐이다(3-5).

### 1-8. 승인해도 **지우지 않는 값**이 있다 — 세무 처리가 아직 그 값을 읽는다

| 방향 | 지우지 않는 값 | 이유(코드) |
| --- | --- | --- |
| 사업자 전환(`TO_BUSINESS`) | 주민등록번호 암호문 · 마스킹값 | 이미 생성된 **비사업자 스냅샷 정산**의 지급 판정(`PayoutBlockPolicy` — `RESIDENT_NUMBER_MISSING`)과 다음 해 지급명세서 제출(`AdminWithholdingReportService`)이 읽는다. `Creator.registerResidentNumber`의 「사업자는 null로 지운다」는 **온보딩 한정**이며 이 경로에서 부르지 않는다 |
| 비사업자 전환(`TO_INDIVIDUAL`) | 사업자등록번호 · 사업자등록증 URL | 이미 생성된 **사업자 스냅샷 정산**이 나중에 확정될 때 `SettlementTaxDocumentService`(L94 ~ 97)가 `creator.getBusinessRegistrationNumber()`를 **그 시점에** 읽어 세금계산서 공급자 번호로 쓴다 |

조회 화면이 이 값을 내릴지는 각 조회가 `businessType`으로 판단한다(53 `AdminCreatorQueryService`가 등록증을 `business ? url : null`로 이미 가린다).

### 1-9. 예금주 판정 — 현재 데이터 모델에서 「계좌 변경 필요」는 **발생하지 않는다**

시안 C3b는 「예금주가 상호(다은스튜디오) → ✕ 본인 실명 필요 → 승인 후 계좌 변경 전까지 지급 보류(T10)」를 그린다. 그런데 코드 사실은 이렇다.

- `Creator`에 **예금주 컬럼이 없다.** 지급 스냅샷(`SettlementPayoutTransitions` L461) · PG 셀러 등록(`SettlementPartnerSyncService.prepareCreator` L243) · 53 상세 · 44 설계서(257행 「인플루언서 예금주는 `Creator.realName`」)가 전부 **본인 실명**을 예금주로 쓴다.
- 토스 지급대행은 예금주명을 은행과 대조하므로, 실제 계좌가 상호 명의면 이미 PG 단계에서 `ACCOUNT_HOLDER_MISMATCH`로 막힌다.

따라서 「예금주 = 상호」인 인플루언서는 시스템에 존재할 수 없다. 이 설계는:

- `holderCheck`를 **「현재 예금주(= 실명) vs 전환 후 규칙」** 으로 계산한다 — 사업자 전환은 「대표자명 = 본인 실명」이 승인 조건이므로 ✓, 비사업자 전환은 「본인 실명」 규칙이므로 ✓. 두 방향 모두 통과한다.
- `approvalImpact.accountChangeRequired`를 **판정식 한 곳**(`CreatorPayeeHolderRule`)에서 내리고, 지금은 항상 `false`다.
- T10 「계좌 변경 필요 → 지급 보류」의 플래그 · `PayoutBlockReason`은 **만들지 않는다.** §55 B-9(사업자 상호 명의 예금주 수집)가 확정되어 `Creator`에 예금주가 생기면 그 판정식과 보류 사유 1개만 더한다(9-1).

### 1-10. 본인확인 실명 · 생년월일은 **검토 대기 건에서만 실값**

대조 기준(「등록증의 대표자명 = 본인확인 실명」 · 「증명원의 성명 · 생년월일 = 본인확인 정보」)을 보려면 실값이 필요하다. 그런데 53 0-6은 인플루언서 상세에서 **열람 사유 + 10분 세션** 없이 이 값을 마스킹한다. 이 화면이 조건 없이 실값을 내리면 우회 경로가 된다.

- 잠정: `status = PENDING`인 상세에서만 실값, 처리 완료 · 취소 건은 `AdminCreatorMasker` 마스킹 — 대조는 심사 중에만 필요하다. 브랜드 계좌번호를 「통장 사본 대조 목적」으로만 전체 노출하는 §16-7과 같은 논리다.
- 열람 로그는 남기지 않는다 — 53의 `creator_privacy_access_log`는 「열람 세션」이라 여기서 행을 만들면 인플루언서 상세가 10분간 풀리는 부작용이 생긴다.
- 개인정보 담당 확인 대상(9-1).

### 1-11. 이 화면의 **정산 계좌는 마스킹**한다

브랜드 C4는 계좌 변경 자체가 심사 대상이라 전체 노출이지만(§16-7), 사업자 구분 변경에서 계좌는 참고 행이고 53 0-6이 「계좌는 해제 대상이 아니다 · 정산 관리에서만」으로 정했다. 시안 C3a · C3b도 마스킹(`604***-**-2218`)이다. 53과 같은 `AdminCreatorMasker.maskAccountNumber`를 쓴다.

### 1-12. 브랜드 경로 **회귀를 먼저 막는다**

유형 enum이 늘면 브랜드 코드가 조용히 틀어지는 지점이 있다 — 대부분 `BUSINESS_INFO ? … : (정산 계좌로 간주)` 형태의 이분법이다. 전부 6-1 회귀 목록에 적고 하나씩 막는다. 핵심 둘:

- 셀러 API 3개(`POST /` · `GET /fields` · `GET /latest`)가 `type` 파라미터를 받으므로 `TO_*`를 넣으면 **브랜드가 인플루언서 유형 요청을 만들 수 있다** → 400 `CHANGE_REQUEST_TYPE_NOT_ALLOWED`.
- 셀러 소유 검사(`BrandChangeRequestService` L140 `getMarket().getId()`)가 인플루언서 요청 id에서 **NPE → 500** → `market == null`이면 403 `CHANGE_REQUEST_ACCESS_DENIED`.

---

## 2. 도메인 모델 변경

### 2-1. V203 — `brand_change_request` 일반화

```sql
-- 어드민 변경 요청 rev.7 — 인플루언서 사업자 구분 변경 편입(§16-2a · 16 rev.7 수정 설계서 2-1).
-- 테이블 이름은 그대로 둔다(1-2) — 이제 브랜드 + 인플루언서 요청을 함께 든다.
ALTER TABLE brand_change_request
    ADD COLUMN requester_type      VARCHAR(16)  NOT NULL DEFAULT 'BRAND' COMMENT 'BRAND, CREATOR' AFTER request_code,
    MODIFY COLUMN market_id        BIGINT       NULL COMMENT 'BRAND 요청만',
    ADD COLUMN creator_id          BIGINT       NULL COMMENT 'CREATOR 요청만' AFTER market_id,
    MODIFY COLUMN type             VARCHAR(20)  NOT NULL COMMENT 'BUSINESS_INFO, SETTLEMENT_ACCOUNT, TO_BUSINESS, TO_INDIVIDUAL',
    MODIFY COLUMN requester_name   VARCHAR(64)  NULL COMMENT '브랜드 담당자 이름 스냅샷 — CREATOR 요청은 NULL(본인)',
    ADD COLUMN resident_number_enc VARCHAR(255) NULL COMMENT 'TO_INDIVIDUAL 주민번호 암호문 — 승인 이관 · 반려 · 취소 시 NULL(1-5)',
    ADD COLUMN version             BIGINT       NOT NULL DEFAULT 0,
    ADD COLUMN creator_pending_key BIGINT GENERATED ALWAYS AS (CASE WHEN `status` = 'PENDING' THEN `creator_id` ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_brand_change_request_creator_pending (creator_pending_key),
    ADD KEY idx_brand_change_request_creator (creator_id, requested_at),
    ADD CONSTRAINT fk_brand_change_request_creator FOREIGN KEY (creator_id) REFERENCES creator (creator_id),
    ADD CONSTRAINT ck_brand_change_request_requester CHECK (
        (requester_type = 'BRAND'   AND market_id  IS NOT NULL AND creator_id IS NULL) OR
        (requester_type = 'CREATOR' AND creator_id IS NOT NULL AND market_id  IS NULL));
```

- 기존 행은 `requester_type` 기본값 `BRAND`로 채워지고 CHECK를 만족한다(백필 없음).
- **중복 요청 차단** — 브랜드는 기존 `UNIQUE (market_id, type, pending_key)` 그대로(인플루언서 행은 `market_id`가 NULL이라 이 제약에 걸리지 않는다). 인플루언서는 **방향과 무관하게 1건**(`creator_pending_key`) — 현재 구분이 방향을 정하므로 두 방향이 동시에 열릴 일이 없지만, 제약은 단순한 쪽을 택한다. V193 `creator_withdrawal_request.pending_key`와 같은 관례다.
- `version` — 승인 · 반려 동시 클릭 방지(3-8). 브랜드 행에도 적용된다.
- `brand_change_request_item`은 **변경 없음** — `field_key VARCHAR(40)`에 새 키(최장 `CREATOR_BUSINESS_REG_NUMBER` 27자)가 들어가고 `current_value` · `requested_value`는 이미 NULL 허용이다.
- `ddl-auto: validate` — 엔티티에 `requesterType` · `creator` · `residentNumberEnc` · `version`을 매핑해야 부팅된다. 생성 컬럼 2개(`pending_key` · `creator_pending_key`)는 지금처럼 매핑하지 않는다.

### 2-2. enum

| enum | 변경 | 비고 |
| --- | --- | --- |
| `ChangeRequestRequesterType`(신규 · `domain.changerequest.type`) | `BRAND` · `CREATOR` | |
| `ChangeRequestType` | `+ TO_BUSINESS(CREATOR)` · `+ TO_INDIVIDUAL(CREATOR)` · 기존 2값은 `(BRAND)` | `requesterType()` · `targetBusinessType()`(TO_* → `BUSINESS` / `INDIVIDUAL`, 그 밖 `null`) · `sourceBusinessType()` 추가 |
| `ChangeRequestField` | `+ CREATOR_BUSINESS_TYPE("사업자 구분", TO_BUSINESS · TO_INDIVIDUAL)` · `+ CREATOR_BUSINESS_REG_NUMBER("사업자등록번호", TO_BUSINESS)` · `+ CREATOR_RESIDENT_NUMBER("주민등록번호", TO_INDIVIDUAL)` | 한 항목이 두 유형에 속하므로 `type` 필드를 **`Set<ChangeRequestType> types`** 로 바꾼다 · `of(type)`은 포함 여부로 거른다. **브랜드 `BUSINESS_INFO`에는 여전히 등록번호가 없다** — 15-16 계획 0절 「③ 변경 불가는 enum에 없다」는 브랜드 기준으로 그대로 성립한다(인플루언서 등록번호는 다른 키) |
| `ChangeRequestRejectReason` | C8b 5값 추가(아래) · `OTHER`는 4유형 지원 | 문구는 **시안 C8b 표기 그대로** — 기존 값이 시안 표기(`서류 판독 불가 (흐림·잘림)`)를 따른 것과 같은 기준 |
| `ImageType` | `CREATOR_ALLOWED_TYPES`에 `CHANGE_REQUEST_DOCUMENT` 추가 · 주석 「브랜드 + 인플루언서 변경 요청 증빙」 | 확장자는 이미 `DOCUMENT_ALLOWED_EXTENSIONS`(jpg · jpeg · png · **pdf**) · 용량 한도 20MB ≥ 스튜디오 10MB |

**반려 정형 사유 — 사업자 구분 축(C8b)** · `supports = {TO_BUSINESS, TO_INDIVIDUAL}`

| enum | 문구 |
| --- | --- |
| `LICENSE_UNREADABLE` | 사업자등록증 판독 불가 (흐림·잘림) |
| `LICENSE_OWNER_NAME_MISMATCH` | 등록증의 대표자명이 본인 실명과 다름 |
| `BUSINESS_NUMBER_MISMATCH` | 사업자등록번호가 등록증과 다름 |
| `BUSINESS_SUSPENDED_OR_CLOSED` | 휴업 · 폐업 상태의 사업자 |
| `CLOSURE_NOT_CONFIRMED` | 폐업 사실이 확인되지 않음 (비사업자 전환) |
| `OTHER` | 기타 — 상세 사유 필수(500자) |

시안 C8b(사업자 전환 건)가 「폐업 사실이 확인되지 않음 (비사업자 전환)」까지 **6종 전부**를 한 드롭다운에 보여 주므로 두 방향에 같은 목록을 내린다. 방향별로 거를지는 9-1.

### 2-3. 엔티티 · 리졸버

**`BrandChangeRequest`**

| 변경 | 내용 |
| --- | --- |
| 필드 | `requesterType` · `creator`(`@ManyToOne LAZY`, nullable) · `residentNumberEnc` · `@Version version` · `market`의 `@JoinColumn(nullable = true)` |
| 팩토리 | 기존 `create(…Market…)`은 브랜드 전용으로 남기고(유형이 BRAND가 아니면 `IllegalArgumentException`) **`createForCreator(code, creator, type, reason, evidence…)`** 신설 — `requesterName = null` |
| 비밀값 | `attachResidentNumber(enc)` · `approve` · `reject` · `cancel`이 끝에서 `residentNumberEnc = null`(1-5) — 적용기는 `approve` **전에** 읽는다 |
| 파생 | `isCreatorRequest()` |
| 주석 | 「M1 · M2(브랜드) + T7 · T8(인플루언서) 공통 레코드 — 테이블 이름은 역사적 이유(1-2)」 |

**`ChangeRequestFieldResolver`** — 기존 `currentValue(field, seller, market)`의 `switch`는 enum 전수라 새 상수가 들어오면 **컴파일이 깨진다**(의도된 신호). 크리에이터 3키는 `IllegalArgumentException`으로 막고, `currentValue(field, creator)`를 신설한다.

| 키 | 현재값 |
| --- | --- |
| `CREATOR_BUSINESS_TYPE` | `creator.businessType.name()`(코드 저장 — 표시 라벨은 조회 시 3-4) |
| `CREATOR_BUSINESS_REG_NUMBER` | `creator.businessRegistrationNumber` |
| `CREATOR_RESIDENT_NUMBER` | `creator.residentRegistrationNumberMasked`(사업자 가입자는 보통 `null`) |

**`Creator`**(53 미커밋 작업과 같은 파일 — 머지 순서 조율)

```java
/** §16-2a 사업자 전환 승인 — 주민번호 암호문은 지우지 않는다(기존 비사업자 정산 · 지급명세서 · 1-8). */
public void convertToBusiness(String businessRegistrationNumber, String businessLicenseImageUrl)

/** §16-2a 비사업자 전환 승인 — 등록번호 · 등록증은 지우지 않는다(기존 사업자 정산의 세금계산서 · 1-8). */
public void convertToIndividual(String residentNumberEnc, String residentNumberMasked)
```

`registerResidentNumber` 주석의 「온보딩이 유일한 수집 지점이다」를 「온보딩 · 비사업자 전환 승인(§16-2a)」으로 고친다.

### 2-4. 리포지토리

| 메서드 | 변경 |
| --- | --- |
| `search`(네이티브) | keyword 조건을 `EXISTS(market.market_name LIKE) OR EXISTS(creator.showroom_name LIKE)`로 — 두 EXISTS 모두 기존처럼 인덱스 없는 LIKE(건수가 작다) |
| `countByStatusGroup`(JPQL) | `JOIN r.market m` → **`LEFT JOIN r.market m LEFT JOIN r.creator c`** · keyword는 `m.marketName` 또는 `c.showroomName`. 지금 그대로 두면 **인플루언서 건이 탭 건수에서 빠진다**(inner join) |
| `findOrderedIds` · `countByStatus` | 변경 없음 — 요청자 무관하게 센다(GNB 배지 4) |
| `findByMarket_Id…` 3종(브랜드 배너 · 52 브랜드 상세) | 변경 없음 — 인플루언서 행은 `market_id`가 NULL이라 걸리지 않는다 |
| 신규 `findFirstByCreator_IdAndStatus(creatorId, PENDING)` | 스튜디오 중복 검사 · 53 `pendingBusinessTypeChange` |
| 신규 `findCreatorBannerCandidates(creatorId, …)` | 브랜드 `findBannerCandidates`와 같은 식 · 키만 `creator.id` |
| `SettlementRepository` 신규 `findRetainedForBusinessTypeChange(creatorId, businessType, statuses)` | 「승인하면」 카드(3-5) — `settlement_number` 오름차순 |

**요청 코드 채번**은 `BrandChangeRequestService.generateRequestCode`(private)를 `domain.changerequest.service.ChangeRequestCodeGenerator`로 옮겨 셀러 · 스튜디오가 같이 쓴다. 순번 규칙(`CHG-{YYYY}-{4자리}`)은 그대로다.

---

## 3. 어드민 API 변경 — `/v1/admin/change-requests`

경로 · 파라미터는 **하나도 바꾸지 않는다.** 응답에 필드를 더하고, 서비스 안에서 요청자로 분기한다.

### 3-1. 공통 — `requester` 객체

목록 행 · 상세 · 처리 응답에 같은 모양으로 더한다.

```json
"requester": { "type": "CREATOR", "id": 31, "name": "예린 뷰티" }
```

| 필드 | BRAND | CREATOR |
| --- | --- | --- |
| `id` | `marketId` → `[브랜드 상세 ↗]` | `creatorId` → `[인플루언서 상세 ↗]`(53 `/v1/admin/creators/{creatorId}`) |
| `name` | `market.marketName`(현재값) | `creator.showroomName`(현재값) |

- 기존 `brandName` · `marketId`(상세)는 **남기되 BRAND 행에만** 채우고 Swagger에 `deprecated` — FE가 `requester`로 옮긴 뒤 제거한다. 인플루언서 행에 쇼룸명을 `brandName`으로 넣지 않는다(의미가 틀린 필드를 만들지 않는다).
- 상세의 기존 `requesterName`(우측 패널 「요청자」 = 담당자 스냅샷)은 의미를 바꾸지 않는다 — 인플루언서 건은 `null`이고 FE가 「인플루언서 본인」을 그린다.

### 3-2. 목록 · 건수 · 배지

| 항목 | 변경 |
| --- | --- |
| `GET /?status=&keyword=` | 행에 `requester` 추가 · `type` 4값 · 검색이 쇼룸명도 본다 · 정렬 · SLA(48h) · 탭 규칙 그대로 |
| `statusCounts` | 인플루언서 건 포함(2-4 조인 수정) — 시안 C1 `검토 대기 4 · 전체 21` |
| `GET /summary` | 코드 변경 없음 — `countByStatus(PENDING)`이 이미 요청자 무관 → 시안 GNB 배지 `4` · 상위 「입점 관리」 `11`은 FE 합산(브랜드 4 + 인플루언서 3 + 4) |
| `GET /reject-reasons?type=` | `TO_BUSINESS` · `TO_INDIVIDUAL` → 2-2의 6종 |

### 3-3. 상세 — 분기 구조

`AdminChangeRequestService.getDetail`을 공통부(코드 · 상태 · SLA · 경과 · 이전/다음 · 이력 골격)와 요청자별 조립기로 나눈다.

| 조립기 | 담당 |
| --- | --- |
| `BrandChangeRequestDetailAssembler` | **지금 코드 그대로 옮긴다** — 대조표 · 증빙 · 참고 항목 · `holderCheck`(상호 대조) |
| `CreatorChangeRequestDetailAssembler`(신규) | 3-4 · 3-5 |

이전/다음은 지금처럼 요청자 구분 없이 현재 탭 순서다(§16-2 — 브랜드 → 인플루언서 건으로 넘어가도 목록으로 돌아가지 않는다).

### 3-4. 상세 — 인플루언서 건(C3a · C3b · C5b)

**대조표 `diff`** — 저장 항목 2개 + 파생 행 4개. 값은 **서버가 완성한 표시 문자열**이다(브랜드 `BANK_CODE`가 이미 은행명을 내리는 것과 같은 기준 · 15-16 계획 A-0의 예외를 「대조표 셀」로 넓힌다).

| 행(`fieldKey`) | `source` | 사업자 전환(C3a) 현재 → 요청 | 비사업자 전환(C3b) 현재 → 요청 |
| --- | --- | --- | --- |
| `CREATOR_BUSINESS_TYPE` 사업자 구분 | `REQUEST` | 비사업자 → 개인사업자 | 개인사업자 → 비사업자 |
| `CREATOR_BUSINESS_REG_NUMBER` 사업자등록번호 | 사업자 전환 `REQUEST` / 비사업자 전환 `DERIVED` | `null`(—) → 214-37-55120 | 312-81-40177 → `폐업` |
| `CREATOR_RESIDENT_NUMBER` 주민등록번호 | `REQUEST` | (행 없음) | `null`(—) → `950812-*******`(1-5 표시 마스킹) |
| `LICENSE_COMPANY_REPRESENTATIVE` 상호 · 대표자 (등록증) | `DOCUMENT` | `null` → `null` — FE 「등록증으로 확인 · 운영자 확인」 | (행 없음) |
| `PAYOUT_METHOD` 지급 방식 | `DERIVED` | 원천징수 3.3% → 세금계산서 · 리워드 + 부가세 10% | 세금계산서 · 리워드 + 부가세 → 원천징수 3.3% |
| `ACCOUNT_HOLDER` 정산 계좌 예금주 | `CHECK` | 본인 실명(1-10 규칙) → `holderCheck` | 같음 |
| `SETTLEMENT_ACCOUNT` 정산 계좌 | `LIVE` | `국민 604***-**-2218`(1-11) → 변경 없음 | 같음 |

- **상호 · 대표자에 값을 내리지 않는다.** 스튜디오는 등록번호 · 등록증 두 값만 받고(§50-2 「받지 않는 정보는 표시하지 않는다」), 53 0-8도 같은 결론이다. 시안 C3a의 `예린스튜디오 · 오예린`은 운영자가 등록증에서 읽는 값의 예시다.
- **비사업자 전환의 폐업일은 서버에 없다.** T8이 받는 값은 주민번호 · 증명원뿐이다. 시안 C3b의 `폐업 (2026.07.31)` 괄호 날짜는 서류에서 읽는 값 → 서버는 `폐업`까지만 내린다(9-1 폐업일 수집 여부).
- `changedFieldLabels`는 저장 항목 라벨(`["사업자 구분", "사업자등록번호"]` / `["사업자 구분", "주민등록번호"]`) — 「대조 확인 항목」 안내문 · 스튜디오 배너가 쓴다.

**`holderCheck`** — 브랜드 모양에 필드 2개를 더한다(브랜드 응답은 새 필드 `null`).

```json
"holderCheck": { "currentHolder": "오예린", "rule": "REPRESENTATIVE_NAME", "mismatch": false,
                 "requestedHolder": null, "companyName": null }
```

`rule` = 전환 후 규칙(`REPRESENTATIVE_NAME` 대표자명 또는 상호 · `REAL_NAME` 본인 실명). `mismatch`는 1-9 판정식 — 지금은 항상 `false`.

**대조 패널 `compareItems`**(인플루언서 건만 · C5b 「서류와 대조할 값」) — 브랜드 미리보기는 `diff`의 변경 행을 대조 값으로 쓰지만, 인플루언서 건은 대조 기준이 **본인확인 정보**라 따로 내린다.

| 방향 | 항목 | `value` | `serverCheck` |
| --- | --- | --- | --- |
| 사업자 전환 | 대표자명 | 본인확인 실명(1-10) | `null` — 서류로 운영자 확인 |
| | 사업자등록번호 | 요청 값 | `null` |
| | 개업일 | `null` · `mustBeBefore = requestedAt` | `null` |
| 비사업자 전환 | 대표자 성명 | 본인확인 실명 | `null` |
| | 생년월일 | 본인확인 생년월일 | **`MATCH` / `MISMATCH`** — 요청 주민번호 앞 6자리 ↔ `creator.birthday`(숫자만 비교 · 세기는 7번째 자리로 판정). **PENDING 동안만** 요청 행 암호문을 복호화해 계산하고, 처리 후에는 암호문이 없으므로 `null`(대조가 끝난 건이다) |
| | 사업자등록번호 | 현재 등록값 | `null` |
| | 폐업일 | `null` · `mustBeBefore = requestedAt` | `null` |

시안 C5b의 「· 일치」 표기는 **`serverCheck = MATCH`인 행에만** 그린다 — 서버가 확인하지 않은 값에 「일치」를 붙이지 않는다. 국세청 상태 조회는 하지 않는다(§16-2a · 16-8 #11 ⓖ).

**`referenceItems`**(C5b 「참고 항목 (대조 대상 아님)」) — 비사업자 전환: `주민등록번호 = 950812-******* · 마스킹 유지` · `정산 계좌 예금주 = 본인 실명` / 사업자 전환: `정산 계좌 예금주`.

**그 밖의 차이**

| 필드 | 인플루언서 건 |
| --- | --- |
| `evidence.documentLabel` | `TO_BUSINESS` = 「사업자등록증」 · `TO_INDIVIDUAL` = 「폐업사실증명원」(지금 코드는 `BUSINESS_INFO`가 아니면 「통장 사본」 — 1-12 회귀 지점) |
| `reason` | 스튜디오 T7 · T8에 사유 입력이 없어 **`null`이 정상** — FE는 C4와 같은 「작성된 변경 사유가 없습니다」(9-1) |
| `history[].actorLabel` | 접수 · 취소 = 「쇼룸 스튜디오」 · 승인 · 반려 = 운영자 이름 |
| `requesterName` | `null`(3-1) |

### 3-5. 「승인하면」 카드 — `approvalImpact`(C3a · C3b · C7b)

`status = PENDING`일 때만 내리고 그 밖은 `null`(「승인 전에 확인」 카드다). C7b 승인 모달은 **이 값을 다시 쓴다** — 별도 API를 두지 않는다.

```json
"approvalImpact": {
  "appliesFrom": "NEXT_SETTLEMENT",
  "payoutMethodBefore": "WITHHOLDING_3_3",
  "payoutMethodAfter": "TAX_INVOICE",
  "retainedSettlements": [ { "settlementId": 412, "settlementNumber": "STL-2609-016", "status": "REVIEWING" } ],
  "accountChangeRequired": false,
  "accountHolderRuleAfter": "REPRESENTATIVE_NAME"
}
```

| 필드 | 규칙 |
| --- | --- |
| `appliesFrom` | 상수 `NEXT_SETTLEMENT` — 근거는 1-7(생성 시점 스냅샷). FE 라벨 「다음 정산부터」 |
| `retainedSettlements` | 이 인플루언서의 정산 중 `creator_business_type = 현재 구분` ∧ `status ∈ {REVIEWING, ADJUSTING, PAYOUT_SCHEDULED}` — **지급이 끝나지 않은, 기존 구분으로 처리될 정산 전부**. 시안 라벨은 「확인 기간 중 정산」이지만 지급 예정(확정됨)도 기존 구분으로 지급되므로 함께 센다(9-1 ⓕ) |
| `accountChangeRequired` | 1-9 판정식 — 지금은 `false`. 시안의 「승인 후 변경 필요 · 예금주가 상호」 분기는 판정식이 `true`를 낼 수 있게 된 뒤에 산다 |
| `payoutMethod*` | `WithholdingType` 코드 — 53 상세와 같은 enum |

### 3-6. 승인 — `POST /{id}/approve`

브랜드 건은 **지금 절차 그대로**다. 인플루언서 건:

1. `PENDING`이 아니면 `CHANGE_REQUEST_NOT_PENDING`(기존).
2. `creator.status = WITHDRAWN`(53)이면 409 `CHANGE_REQUEST_REQUESTER_INACTIVE`(신규) — 탈퇴 승인 · 파기가 끝난 회원의 세무 구분을 바꾸지 않는다. 정지(`SUSPENDED`)는 막지 않는다 — 정지는 새로 벌이는 일만 막고(§51-1) 구분 변경은 정산 정리에 가깝다.
3. **방향 재검증** — `creator.businessType ≠ type.sourceBusinessType()`이면 409 `CHANGE_REQUEST_STALE`(신규). 열린 요청이 1건뿐이라 정상 경로에서는 나오지 않지만, 접수 후 운영자 직권 수정 등으로 전제가 바뀐 건을 승인하지 않기 위한 방어다.
4. `CreatorBusinessTypeChangeApplier.apply(request, creator)`(신규 · `ChangeRequestApplier`와 같은 패키지)
   - `TO_BUSINESS` → `creator.convertToBusiness(항목 CREATOR_BUSINESS_REG_NUMBER 요청값, request.evidenceFileUrl)` — 증빙(사업자등록증)이 곧 회원의 등록증이 된다. 브랜드 M2 승인이 통장 사본을 교체하는 것(15-16 계획 7-2)과 같은 논리.
   - `TO_INDIVIDUAL` → `creator.convertToIndividual(request.residentNumberEnc, 항목 CREATOR_RESIDENT_NUMBER 요청값)` — 항목 값은 기존 마스킹 형식(1-5)이라 그대로 옮긴다. 복호화하지 않는다.
   - 커밋 후 `SettlementPartnerSyncService.onCreatorProfileChanged(creatorId)`(신규 · 5-3) — 실패해도 승인은 끝난다(브랜드 계좌 변경의 `AfterCommit` 패턴과 같다).
5. `request.approve(operatorId)` — 주민번호 암호문이 여기서 비워진다.
6. 53 처리 이력 — `CreatorAdminHistoryRecorder.byOperator(creatorId, BUSINESS_TYPE_CHANGE_APPROVED, operatorId, operatorName, "비사업자 → 개인사업자 · CHG-2026-0083", "CHANGE_REQUEST", requestId, now)`. 53이 enum 값을 이미 예약해 두었다(53 0-7). 운영자 이름은 53과 같은 `AdminOperatorResolver`.
7. 메일 — `MailService.sendBusinessTypeChangeApprovedEmail(creator.businessEmail, …)`(5-4).
8. 응답 — 기존 `ProcessResponse` + `requester`. C10 토스트는 `requester.name` + `type` + `status`로 FE가 조립한다.

**반영하지 않는 것** — 기존 정산 행(1-7) · 정산 계좌(1-9 · 계좌는 이 요청에서 받지 않는다 — §16-2a 「반려되면 계좌만 바뀐 채 남기 때문」) · 계약(5-5).

### 3-7. 반려 — `POST /{id}/reject`

브랜드 건은 그대로다. 인플루언서 건:

- `reasonType.supports(type)` 검사 · `OTHER`만 상세 사유 필수(500자) — 기존 코드 그대로 동작한다(enum만 늘었다).
- `request.reject(…)` — 주민번호 암호문이 비워진다.
- 53 이력 `BUSINESS_TYPE_CHANGE_REJECTED`(detail = 정형 사유 문구).
- 메일 `sendBusinessTypeChangeRejectedEmail(creator.businessEmail, …)` — 정형 문구 + 상세 사유를 **가공 없이**(§16-5).
- 결과는 스튜디오 T4a · T9a 배너가 4-2의 배너 API로 읽는다. 즉시 재요청 가능(대기기간 없음 · §16-2a).

### 3-8. 동시 처리

지금 승인 · 반려는 `isPending()` 확인 후 갱신이라 **두 운영자가 동시에 누르면 둘 다 통과**한다(브랜드도 같다 — 승인 두 번이면 메일 두 통, 승인 + 반려면 마지막 커밋이 이긴다). 사업자 구분 변경은 세무 처리가 바뀌는 승인이라 이 틈을 닫는다 — `@Version` 충돌(`ObjectOptimisticLockingFailureException`)을 409 `CHANGE_REQUEST_NOT_PENDING`으로 매핑한다. 브랜드 건에도 그대로 적용된다(동작 변화는 「동시 클릭 두 번째가 실패」뿐).

---

## 4. 스튜디오 접수 API — `/v1/creator/business-type-change-requests`

> **[계획서 2-2 #11 ~ #16 — 50 정본]** 경로 · 요청 바디 · 배너 · 에러 코드 · 업로드 타입은 50 7절을 따른다 — `/v1/creator/basic-info/business-type-change-requests` · `targetType` · 배너는 `GET /v1/creator/basic-info/settlement` 의 `businessTypeChange` · 방향 불일치 `CHANGE_REQUEST_BUSINESS_TYPE_MISMATCH` · 탈퇴 `CREATOR_WITHDRAWN` · 증빙 `CREATOR_DOCUMENT` · 사유 입력 없음. 이 절은 검증 규칙의 참고로만 남긴다.

어드민 큐에 행을 넣는 생산자다. 스튜디오 정산 정보 탭 본체(§50-2)는 범위 밖이지만, 이 API 없이는 큐에 인플루언서 건이 생기지 않는다. 패키지 `showroomz.api.creator.changerequest` · 인증은 `SecurityConfig`의 `/v1/creator/**`(CREATOR) 규칙 그대로 · 현재 인플루언서는 `creatorRepository.findByUser(...)`(53 `CreatorSanctionGuard`와 같은 조회).

| # | 메서드 · 경로 | 상태 | 내용 |
| --- | --- | --- | --- |
| 1 | `GET /current` | T4 · T4a · T5 · T9 · T9a | 배너 객체 또는 `null` — 브랜드 15-16 계획 3절 배너 판정식과 같다(`PENDING` 또는 처리 완료 ∧ 미확인) |
| 2 | `POST /` | T7 · T8 → M3 | 생성 |
| 3 | `POST /{id}/cancel` | T4 · T9 `[요청 취소]` | `CANCELED` · 주민번호 암호문 비움 |
| 4 | `POST /{id}/acknowledge` | T4a · T5 · T9a `[확인]` | `result_acknowledged_at` |

증빙 업로드는 기존 `POST /v1/creator/images`(`type=CHANGE_REQUEST_DOCUMENT` — 2-2에서 허용)로 올리고 받은 URL을 생성 요청에 싣는다(브랜드와 같은 2단계).

### 4-1. 생성 — 요청 바디 · 검증

```json
{ "type": "TO_BUSINESS",
  "businessRegistrationNumber": "214-37-55120",      // TO_BUSINESS 필수
  "residentRegistrationNumber": null,                 // TO_INDIVIDUAL 필수 · 응답 · 로그에 원문을 싣지 않는다
  "reason": null,                                     // 선택 · 500자 — 시안에 입력란 없음(9-1)
  "evidenceFileUrl": "https://…", "evidenceFileName": "사업자등록증_예린스튜디오.pdf", "evidenceFileSize": 838860 }
```

| 검증 | 실패 |
| --- | --- |
| `type`이 `TO_BUSINESS` · `TO_INDIVIDUAL` 중 하나 | 400 `CHANGE_REQUEST_TYPE_NOT_ALLOWED` |
| 방향 = 현재 구분의 반대(`INDIVIDUAL` → `TO_BUSINESS`만 · `BUSINESS` → `TO_INDIVIDUAL`만) | 409 `CHANGE_REQUEST_STALE` |
| `creator.status ≠ WITHDRAWN` | 409 `CHANGE_REQUEST_REQUESTER_INACTIVE` |
| 열린 요청 없음(+ `creator_pending_key` 유니크 위반도 같은 코드) | 409 `CHANGE_REQUEST_ALREADY_PENDING` |
| 사업자등록번호 `^\d{3}-\d{2}-\d{5}$` — 온보딩(`CreatorCompleteRegistrationRequest`)과 같은 식 · 같은 문구 | 400 |
| 주민등록번호 `ResidentRegistrationNumber.normalize` — 온보딩과 같은 식 · **원문을 메시지에 싣지 않는다** | 400 「주민등록번호를 정확히 입력해 주세요.」 |
| 증빙 3필드 필수 | 400 `CHANGE_REQUEST_EVIDENCE_REQUIRED` |

**저장**

- 항목 2개 — `currentValue`는 `ChangeRequestFieldResolver.currentValue(field, creator)`로 **서버 스냅샷**(15-16 계획 1-2 원칙 그대로).
  - `TO_BUSINESS`: `CREATOR_BUSINESS_TYPE(INDIVIDUAL → BUSINESS)` · `CREATOR_BUSINESS_REG_NUMBER(현재값 → 요청 번호)`
  - `TO_INDIVIDUAL`: `CREATOR_BUSINESS_TYPE(BUSINESS → INDIVIDUAL)` · `CREATOR_RESIDENT_NUMBER(현재 마스킹 → mask(normalized))`
- `TO_INDIVIDUAL`은 `attachResidentNumber(personalDataCipher.encrypt(normalized))` — 온보딩(`CreatorAuthService` L112 ~ 114)과 같은 암호화 경로.
- 요청 코드는 `ChangeRequestCodeGenerator`(2-4) — 브랜드와 같은 순번.
- 생년월일 대조(주민번호 앞 6자리 ↔ `creator.birthday`)는 **접수 때 막지 않는다** — 본인확인 생년월일 자체가 틀렸을 가능성(재인증 경로 미확정 · §50-1)이 있어 판단은 운영자에게 둔다. 대조 결과는 저장하지 않고 어드민 상세가 계산한다(3-4 `compareItems`).

### 4-2. 배너 응답 — `GET /current`

```json
{
  "requestId": 83, "requestCode": "CHG-2026-0083", "type": "TO_BUSINESS", "status": "PENDING",
  "requestedAt": "2026-08-07T13:05:00", "processedAt": null, "cancelable": true,
  "rejectReason": null, "rejectReasonDetail": null,
  "evidence": { "fileName": "사업자등록증_예린스튜디오.pdf", "fileSizeBytes": 838860, "uploadedAt": "2026-08-07T13:05:00" },
  "accountChangeRequired": false
}
```

- `evidence` ← T4 · T4a · T9 · T9a 「제출한 사업자등록증 PDF · 1.2MB · 2026.09.05 제출」.
- `rejectReason`은 정형 문구 완성본 · `rejectReasonDetail` 미입력은 `null`(브랜드 A-5와 같다).
- `accountChangeRequired`는 T10 재료(1-9 — 지금은 항상 `false`). T10의 계좌 카드 「변경 필요」 경고는 정산 정보 탭 본체가 이 값을 읽는다.

---

## 5. 다른 모듈 연계

### 5-1. 53 인플루언서 상세 — 자리 채움

| 53의 자리 | 이 설계가 채우는 것 |
| --- | --- |
| `settlementAccount.pendingBusinessTypeChange`(지금 `AdminCreatorDto` L110 `Object` · 항상 `null`) | `{ changeRequestId, requestCode, direction: "TO_BUSINESS" \| "TO_INDIVIDUAL", requestedAt }` — `findFirstByCreator_IdAndStatus(PENDING)` · 시안 「변경 요청 확인 중 ↗」 → `/v1/admin/change-requests/{id}` |
| `CreatorAdminEventType.BUSINESS_TYPE_CHANGE_APPROVED · _REJECTED`(예약됨) | 3-6 · 3-7에서 기록 · `ref_type = "CHANGE_REQUEST"` 상수를 `CreatorAdminHistoryRecorder`에 추가(`REF_PRIVACY_ACCESS` · `REF_SETTLEMENT_ACCOUNT` 옆) |

접수 · 취소는 이력에 쓰지 않는다 — 53이 예약한 값이 승인 · 반려 2종뿐이고, 52 브랜드 상세도 `CANCELED`를 내리지 않는다(52 384행). 같은 기준이다.

### 5-2. 52 브랜드 상세 — 영향 없음 확인

52가 읽는 쿼리(`findByMarket_IdAndStatusOrderByRequestedAtAsc` · `findByMarket_IdOrderByRequestedAtDesc`)는 `market_id` 조건이라 인플루언서 행이 섞이지 않는다. `BrandAdminHistoryReader` L113의 유형 라벨 이분법도 브랜드 행만 들어오므로 안전하다 — 다만 방어적으로 `TO_*`면 `IllegalStateException`을 던지도록 바꾼다(6-1).

### 5-3. 정산 · PG

| 대상 | 변경 |
| --- | --- |
| 정산 생성 · 원천징수 · 부가세 · `PayoutBlockPolicy` · 세금계산서 문서 | **없음**(1-7 · 1-8) |
| `SettlementPartnerSyncService` | `onCreatorProfileChanged(creatorId)` 신설 — 지금 `prepareCreator`의 최신 판정(`upToDate`)이 **계좌 해시만** 봐서, 구분이 바뀌어도(사업자 정보 `Business` 추가 · 제거) PG 셀러를 갱신하지 않는다. 승인 후 강제 재동기화한다. **토스 셀러의 `businessType`(`INDIVIDUAL` ↔ `INDIVIDUAL_BUSINESS`) 변경을 수정 API로 받는지 · 재등록이 필요한지는 `[근거 대기-PG]`**(9-1). 근거가 오기 전 구현은 「수정 시도 → 실패 시 수취자 행 `PARTNER_NOT_READY`로 남기고 지급 배치 앞단 재시도」 — 기존 실패 처리 경로를 그대로 탄다 |

### 5-4. 메일 — `MailService` 2개 추가

브랜드용(`sendChangeRequestApproved/RejectedEmail`)은 문구가 「파트너센터에서 다시 요청」 · 수신자가 로그인 이메일이라 재사용하지 않는다.

| 메서드 | 수신 | 내용 |
| --- | --- | --- |
| `sendBusinessTypeChangeApprovedEmail(to, requestCode, direction, processedAt)` | `creator.businessEmail`(업무 이메일 — 「승인 · 계약 · 정산 알림 수신 주소」 §50-1) | 사업자 전환: 「다음 정산부터 리워드 + 부가세 10% · SHOWROOMZ 앞으로 세금계산서 발행」(T5 문구) / 비사업자 전환: 「다음 정산부터 3.3% 원천징수」 |
| `sendBusinessTypeChangeRejectedEmail(to, requestCode, processedAt, reasonSummary, reasonDetail)` | 같음 | 정형 문구 + 상세 사유 가공 없이 · 「쇼룸 스튜디오 › 기본정보 관리 › 정산 정보에서 다시 요청」 |

### 5-5. 계약 — 표시만 바뀐다(조치 없음 · 알아 둘 것)

`WithholdingType.from(creator.businessType)`을 계약 상세(`CreatorContractDetailAssembler` L243 · `ContractDetailAssembler` L178)와 계약서 PDF 템플릿(`ContractPdfTemplate` L25)이 **현재값으로** 읽는다. 승인 뒤에는 이미 체결된 계약의 상세 화면 「지급 방식」 표기도 새 구분으로 바뀐다. 실제 지급은 정산 스냅샷을 따르므로 돈은 틀리지 않지만, 화면과 서명본 PDF의 표기가 갈린다 — 9-1에 올린다.

---

## 6. 회귀 지점 · 의도적으로 하지 않는 것

### 6-1. 유형 · 요청자 확장으로 깨지는 기존 코드(전부 수정 대상)

| 위치 | 지금 | 깨지는 방식 | 수정 |
| --- | --- | --- | --- |
| `AdminChangeRequestService.java:103 · 142 · 183 · 208 · 293` | `request.getMarket()…` | 인플루언서 건 NPE → 500 | 3-3 분기 · 조립기 |
| `AdminChangeRequestService.java:219` | `BUSINESS_INFO ? 사업자 specs : 계좌 specs` | 인플루언서 건이 계좌 대조표로 그려짐 | 브랜드 조립기로 이동 + 브랜드 유형만 받음 |
| `AdminChangeRequestService.java:265` | `BUSINESS_INFO ? "사업자등록증" : "통장 사본"` | 폐업사실증명원이 「통장 사본」 | 유형별 `switch` |
| `AdminChangeRequestService.java:280 · 288` | 참고 항목 · `holderCheck` 이분법 | 상호 대조가 인플루언서 건에 붙음 | 조립기 분리 |
| `ChangeRequestApplier.java:29 · 36` | `getMarket()` · `SETTLEMENT_ACCOUNT` 분기 | NPE | 서비스가 요청자로 적용기를 고른다 · 브랜드 적용기는 브랜드 유형만 받음 |
| `BrandChangeRequestService.java:140` | `getMarket().getId()` | 인플루언서 요청 id로 취소 · 확인 호출 시 NPE | `market == null` → 403 |
| `BrandChangeRequestService` `create` · `getFields` · `getBanner` | `type` 무검사 | 브랜드가 `TO_*` 요청 생성 가능 | 400 `CHANGE_REQUEST_TYPE_NOT_ALLOWED` |
| `BrandChangeRequestService.java:155 · 213` | `SETTLEMENT_ACCOUNT` 아니면 사업자 정보로 간주 | 위 가드로 도달 불가 | 가드만 |
| `BrandChangeRequestRepository` `countByStatusGroup` | `JOIN r.market` | 탭 건수에서 인플루언서 누락 | 2-4 |
| `ChangeRequestFieldResolver.currentValue` | enum 전수 `switch` | **컴파일 에러**(좋은 신호) | 2-3 |
| `BrandAdminHistoryReader.java:113` | `SETTLEMENT_ACCOUNT ? … : "사업자 정보 변경 요청"` | 도달 불가 | 방어 예외 |
| Swagger — `SellerChangeRequestControllerDocs` · `AdminChangeRequestControllerDocs` | `type` 허용값 2종 | 셀러 문서에 `TO_*`가 노출 | 셀러 문서는 `allowableValues`를 브랜드 2종으로 고정 · 어드민 문서는 4종 + 예시(C3a · C3b) 추가 |

### 6-2. 의도적으로 하지 않는 것

| 항목 | 이유 |
| --- | --- |
| 인플루언서 **계좌 변경**을 이 큐에 넣기 | 시안 rev.7 결정(「운영자 확인 없이 저장」) · 16-8 #9 ⓔ 대표 확정 전 |
| 국세청 사업자 상태 조회 | 홈택스 수동(16-8 #11 ⓖ) |
| 개업일 · 폐업일 · 상호 · 대표자 자동 대조 | 서버가 그 값을 받지 않는다(3-4) — 받기로 하면 T7 · T8 입력란부터 |
| T10 계좌 변경 필요 플래그 · 지급 보류 사유 | 현재 모델에서 발생 불가(1-9) · §55 B-9 확정 후 |
| SLA를 요청자별로 분리 | 16-8 #12 ⓗ 미확정 — 48h 상수 한 곳(`AdminChangeRequestService.SLA_HOURS`)을 `ChangeRequestSla.hoursFor(requesterType)`로만 감싸 두고 값은 같게 |
| 요청 유형 · 요청자 필터 | §16-1 「요청 유형 필터를 두지 않는다」 유지 · 시안에 요청자 필터 없음 |
| 부분 승인 | §16-4 그대로 |
| 테이블 리네임 | 1-2 |

---

## 7. 구현 순서

| Phase | 내용 | 산출 |
| --- | --- | --- |
| 0 | **선행 확인** — 52 · 53 미커밋 작업(`Creator.java` · `BrandChangeRequestRepository` · `CreatorAdminHistoryRecorder` · `AdminCreatorDto`)이 먼저 머지됐는지. 같은 파일을 만진다 | — |
| 1 | V200 + 엔티티 · enum · 리졸버 · 채번기 이동 + **6-1 회귀 수정**(브랜드 동작 불변 확인까지) | `domain/changerequest/**` · 셀러 가드 |
| 2 | 스튜디오 접수 · 배너 · 취소 · 확인(4절) + `ImageType` | `api/creator/changerequest/**` |
| 3 | 어드민 목록 · 건수 · 검색 · `requester` · 반려 사유 enum(3-1 · 3-2) | `api/admin/changerequest/**` |
| 4 | 어드민 상세 조립기 분리 + 인플루언서 조립기 · `approvalImpact`(3-3 ~ 3-5) | 〃 + `SettlementRepository` |
| 5 | 승인 · 반려 · 적용기 · `@Version` · 53 이력 · 메일 · PG 재동기화 훅(3-6 ~ 3-8 · 5절) | 〃 + `MailService` · `SettlementPartnerSyncService` |
| 6 | 53 `pendingBusinessTypeChange` 채움 | `AdminCreatorQueryService` |
| 7 | 테스트(8절) · Swagger 예시 | `src/test/**` |

Controller / `*ControllerDocs` / Service / DTO 4종 세트 관례를 따른다.

---

## 8. 테스트

```powershell
.\gradlew.bat compileJava      # V200 ↔ 엔티티 불일치는 ddl-auto=validate 부팅에서 잡힌다
.\gradlew.bat test
```

| 대상 | 케이스 |
| --- | --- |
| **브랜드 회귀** | 기존 `BrandChangeRequestService` · `AdminChangeRequestService` 테스트 전부 통과 · 브랜드 상세 응답이 `requester` · `source`(`REQUEST`/`LIVE`) 외에 바뀌지 않음 · 셀러가 `type=TO_BUSINESS`로 생성 · 필드 · 배너 호출 시 400 · 셀러가 인플루언서 요청 id로 취소 시 403(500 아님) |
| 스키마 | CHECK — `requester_type=CREATOR`인데 `market_id` 채움 → 실패 · 인플루언서 열린 요청 2건 → 유니크 위반 · 기존 행 `BRAND` 기본값 |
| 스튜디오 접수 | 비사업자가 `TO_INDIVIDUAL` → 409 · 열린 요청 중복 409 · 등록번호 형식 400 · 주민번호 형식 400(메시지에 원문 없음) · 탈퇴 회원 409 · `current_value` 서버 스냅샷 · 주민번호 **암호문만** 저장되고 항목엔 마스킹 · 요청 코드가 브랜드와 같은 순번 |
| 취소 · 확인 | 취소 시 `resident_number_enc` NULL · `PENDING` 아닌 건 취소 거부 · 처리 전 확인 거부 |
| 목록 | 쇼룸명 검색이 인플루언서 건을 찾음 · 탭 건수에 인플루언서 포함 · 정렬이 요청자 섞여도 경과 내림차순 · `summary` 합산 · 이전/다음이 요청자를 넘나듦 |
| 상세 | `TO_BUSINESS` 6행 · `TO_INDIVIDUAL` 6행 `source` 정확 · 계좌 마스킹 · 주민번호 성별 자리 가림 · PENDING이면 실명 실값 / 처리 후 마스킹 · `approvalImpact`가 PENDING에서만 · `retainedSettlements`가 현재 구분 스냅샷 ∧ 미지급만(지급 완료 · 다른 구분 제외) · `compareItems` 생년월일 `MATCH` / `MISMATCH` |
| 승인 | `TO_BUSINESS` → `businessType=BUSINESS` · 등록번호 · 등록증 = 증빙 URL · **주민번호 암호문 유지** / `TO_INDIVIDUAL` → `INDIVIDUAL` · 주민번호 이관 · **등록번호 · 등록증 유지** · 요청 행 암호문 NULL · 53 이력 1행 · 메일 수신자 = 업무 이메일 · 기존 정산 행 `creator_business_type` 불변 · 승인 후 생성되는 정산은 새 구분 · 탈퇴 회원 409 · 방향 불일치 409 · **동시 승인 2회 → 1회만 성공 · 메일 1통** |
| 반려 | 사업자 구분 축 사유만 허용(브랜드 사유 `EVIDENCE_MISSING` 거부) · `OTHER` 상세 필수 · 암호문 NULL · 53 이력 · 메일 |
| 지급 연동 | `TO_INDIVIDUAL` 승인 뒤 새 비사업자 정산이 `RESIDENT_NUMBER_MISSING`에 걸리지 않음 · `TO_BUSINESS` 승인 뒤 남은 비사업자 정산도 걸리지 않음(1-8) · 남은 사업자 정산 확정 시 세금계산서 문서에 등록번호가 채워짐 |

리포지토리 쿼리 파싱은 기존 `RepositoryQueryParsingTest`가 잡는다.

---

## 9. 미결 · 확인 필요

### 9-1. 기획 미결과 이 설계의 잠정 처리

| 출처 | 항목 | 확정 전 처리 |
| --- | --- | --- |
| 16-8 #9 ⓔ · §55 A-6 | 인플루언서 계좌 변경 승인제 여부 | 이 큐에 넣지 않음(6-2). 승인제가 되면 유형 `CREATOR_SETTLEMENT_ACCOUNT` 하나를 더하는 구조 — 이 설계의 일반화가 그대로 받는다 |
| 16-8 #10 ⓕ · §50-4 적용 시점 | 「확인 기간 중 정산」 처리 기준 | 기존 구분 유지(1-7 — 코드가 이미 그렇게 동작) · 카드에는 **지급 예정까지** 포함해 센다(3-5). 「확인 기간(REVIEWING · ADJUSTING)만」으로 좁히라면 상태 집합 한 줄 |
| 16-8 #11 ⓖ | 국세청 사업자 상태 조회 | 하지 않음 |
| 16-8 #12 ⓗ | 인플루언서 SLA | 48h 동일 · 분기 자리만(6-2) |
| 16-8 #13 · §55 D-25 | 시안 C3b 예시 등록번호 `312-81-40177`이 **법인 구분 번호**(가운데 81) | 아래 「법인 번호 거부」와 함께 결정 |
| §50-2 「법인 제외」 | 사업자 전환에서 가운데 두 자리 81 ~ 88(법인) 번호를 거부할지 | **온보딩과 같게 형식만 검사**(지금 온보딩도 거부하지 않는다). 거부하려면 온보딩 · 전환 두 곳에 같이 넣는 것을 권장 — 토스 셀러 판정(44 토스 4-1)이 이 번호를 `CORPORATE`로 분류해 「개인사업자만」과 충돌한다 |
| §55 B-9 · §50-3 ⚠️ | 사업자 상호 명의 예금주 수집 | 1-9 — 수집 전까지 예금주 = 실명 · `accountChangeRequired=false` · T10 경로 미구현 |
| C3a · C3b 사유 카드 ↔ T7 · T8 | 스튜디오 모달에 **변경 사유 입력란이 없다** | API는 선택값으로 받고, 어드민은 `null` → 「작성된 변경 사유가 없습니다」. 입력란을 둘지 기획 확인 |
| C3b · C5b 「폐업 (2026.07.31)」 | 폐업일을 서버가 모른다 | `폐업`까지만. T8에 폐업일 입력을 더하면 「폐업일 < 요청일」을 서버가 접수 시 검사할 수 있다 — 권장 |
| C8b 목록 | 두 방향에 6종 공통 — 사업자 전환 건에 「폐업 사실이 확인되지 않음 (비사업자 전환)」, 비사업자 전환 건에 「등록증의 대표자명이 …」가 뜬다 | 시안대로 공통. 방향별로 거르려면 `supports`만 쪼개면 된다(API 그대로) |
| 반려 문구 표기 | 기획 16-2a(`판독 불가(흐림 · 잘림)`) ↔ 시안 C8b(`판독 불가 (흐림·잘림)`) | 시안 — 기존 브랜드 값과 같은 기준. 배너 · 메일에 그대로 실리므로 확정 필요 |
| 1-10 · 개인정보 | 변경 요청 상세에서 본인확인 실명 · 생년월일 실값 노출 | PENDING만 실값 · 열람 로그 없음 — 개인정보 담당 확인 |
| 5-5 | 승인 뒤 기존 계약의 「지급 방식」 표기가 새 구분으로 바뀜(서명본 PDF와 불일치) | 조치 없음 — 계약에 구분 스냅샷을 둘지 계약 설계에서 결정 |
| 5-3 · `[근거 대기-PG]` | 토스 셀러 `businessType` 변경 가능 여부 · 재등록 필요 여부 | 수정 시도 → 실패는 기존 `PARTNER_NOT_READY` 경로 |
| §16-8 #8 · 자문대기-법률 I | 감사 기록 보존 범위 | 처리 끝난 요청 행에서 주민번호 암호문 제거(1-5) · 마스킹 · 증빙 URL은 보존 |

### 9-2. 이 설계에서 새로 나온 확인 사항 · 리스크

| # | 항목 | 내용 |
| --- | --- | --- |
| 1 | **기존 동시 처리 틈** | 3-8 — 브랜드 승인 · 반려도 지금 동시 클릭이 둘 다 통과한다. `@Version`으로 함께 닫는다(브랜드 동작 변화 = 두 번째 클릭 409) |
| 2 | **요청 코드 경합** | `countByRequestCodeStartingWith + 1` 채번은 동시 접수 시 같은 코드를 만들 수 있고, 유니크 위반이 지금은 `CHANGE_REQUEST_ALREADY_PENDING`(「이미 검토 중」)으로 잘못 매핑된다. 접수원이 둘(파트너 · 스튜디오)이 되어 확률이 오른다 → 채번기 이동(2-4) 때 **코드 유니크 위반만 1회 재시도**로 분리할 것을 권장 |
| 3 | **주민번호 마스킹 형식 이원화** | 저장 `900101-1******` ↔ 표시 규칙 `900101-*******`(§50-2). 이 설계는 표시에서만 가린다(1-5). 53 · 스튜디오 · 정산 화면이 저장값을 그대로 내리는 곳이 있으면 같은 표시 함수로 맞춰야 한다 — 일괄 정리는 별건 |
| 4 | **증빙 파일 공개 범위** | 브랜드 변경 요청 증빙과 같은 업로드 경로(S3 URL)를 쓴다. 폐업사실증명원 · 사업자등록증은 인적사항이 있는 서류 — 53이 등록증을 presigned로 내리기로 한 것과 맞출지(브랜드 증빙 포함) 결정 |
| 5 | **승인 후 사업자 전환 증빙 = 회원 등록증** | 3-6 ④ — 브랜드 M2의 통장 사본 교체(15-16 계획 7-2)와 같은 판단. 증빙 원본이 그대로 회원의 사업자등록증 URL이 된다 |
| 6 | **정지 중 구분 변경** | 3-6 ② · 4-1 — 막지 않는다. 53 정지 사유 「정산 정보 불일치」로 정지된 인플루언서가 구분을 고치는 것은 해소 경로이기도 하다 |
| 7 | **탈퇴 심사 중 구분 변경** | 막지 않는다 — 53 `CreatorWithdrawalEligibility`의 미정산 조건과 독립. 탈퇴 승인이 구분 변경 PENDING을 자동 취소할지는 53과 협의(지금은 승인 시 2번 검사로 막힌다) |
| 8 | **테이블 · 엔티티 리네임** | `brand_change_request` → `change_request` · `BrandChangeRequest` → `ChangeRequest`. 52 · 53 머지 후 별도 PR 권장(1-2) |

---

# 부록 A. 응답 예시 — 시안 기준

## A-1. 목록 행 ← C1

```json
{
  "requestId": 83,
  "requestCode": "CHG-2026-0083",
  "requester": { "type": "CREATOR", "id": 31, "name": "예린 뷰티" },
  "brandName": null,
  "type": "TO_BUSINESS",
  "requestedAt": "2026-08-07T13:05:00",
  "processedAt": null,
  "elapsedText": "1일 4h",
  "slaExceeded": false,
  "status": "PENDING"
}
```

C1 1열 = `requester.type` 배지(`브랜드` / `인플루언서`) + `requester.name`. 브랜드 행은 `brandName`이 지금처럼 채워진다(deprecated).

## A-2. 상세 — 사업자 전환 ← C3a

```json
{
  "requestId": 83, "requestCode": "CHG-2026-0083",
  "requester": { "type": "CREATOR", "id": 31, "name": "예린 뷰티" },
  "brandName": null, "marketId": null,
  "type": "TO_BUSINESS", "status": "PENDING", "slaExceeded": false,
  "requestedAt": "2026-08-07T13:05:00", "processedAt": null, "elapsedText": "1일 4h",
  "requesterName": null,
  "reason": null,
  "diff": [
    { "fieldKey": "CREATOR_BUSINESS_TYPE",          "label": "사업자 구분",          "currentValue": "비사업자",       "requestedValue": "개인사업자",                    "changed": true,  "locked": false, "source": "REQUEST" },
    { "fieldKey": "CREATOR_BUSINESS_REG_NUMBER",    "label": "사업자등록번호",       "currentValue": null,             "requestedValue": "214-37-55120",                 "changed": true,  "locked": false, "source": "REQUEST" },
    { "fieldKey": "LICENSE_COMPANY_REPRESENTATIVE", "label": "상호 · 대표자 (등록증)", "currentValue": null,             "requestedValue": null,                           "changed": false, "locked": false, "source": "DOCUMENT" },
    { "fieldKey": "PAYOUT_METHOD",                  "label": "지급 방식",            "currentValue": "원천징수 3.3%",  "requestedValue": "세금계산서 · 리워드 + 부가세 10%", "changed": true,  "locked": false, "source": "DERIVED" },
    { "fieldKey": "ACCOUNT_HOLDER",                 "label": "정산 계좌 예금주",      "currentValue": "오예린",         "requestedValue": null,                           "changed": false, "locked": false, "source": "CHECK" },
    { "fieldKey": "SETTLEMENT_ACCOUNT",             "label": "정산 계좌",            "currentValue": "국민 604***-**-2218", "requestedValue": null,                      "changed": false, "locked": false, "source": "LIVE" }
  ],
  "changedFieldLabels": ["사업자 구분", "사업자등록번호"],
  "holderCheck": { "currentHolder": "오예린", "rule": "REPRESENTATIVE_NAME", "mismatch": false, "requestedHolder": null, "companyName": null },
  "compareItems": [
    { "key": "REPRESENTATIVE_NAME", "label": "대표자명",       "value": "오예린",       "mustBeBefore": null,                  "serverCheck": null },
    { "key": "BUSINESS_REG_NUMBER", "label": "사업자등록번호", "value": "214-37-55120", "mustBeBefore": null,                  "serverCheck": null },
    { "key": "OPENING_DATE",        "label": "개업일",         "value": null,           "mustBeBefore": "2026-08-07T13:05:00", "serverCheck": null }
  ],
  "referenceItems": [ { "label": "정산 계좌 예금주", "value": "오예린" } ],
  "evidence": { "documentLabel": "사업자등록증", "fileName": "사업자등록증_예린스튜디오.pdf", "fileSizeBytes": 838860,
                "extension": "pdf", "fileUrl": "https://…", "uploadedAt": "2026-08-07T13:05:00" },
  "approvalImpact": {
    "appliesFrom": "NEXT_SETTLEMENT", "payoutMethodBefore": "WITHHOLDING_3_3", "payoutMethodAfter": "TAX_INVOICE",
    "retainedSettlements": [ { "settlementId": 412, "settlementNumber": "STL-2609-016", "status": "REVIEWING" } ],
    "accountChangeRequired": false, "accountHolderRuleAfter": "REPRESENTATIVE_NAME"
  },
  "history": [ { "event": "REQUESTED", "occurredAt": "2026-08-07T13:05:00", "actorLabel": "쇼룸 스튜디오" } ],
  "prevRequestId": 81, "nextRequestId": 84
}
```

## A-3. 상세 — 비사업자 전환 ← C3b · C5b (A-2와 다른 부분만)

```json
{
  "type": "TO_INDIVIDUAL",
  "diff": [
    { "fieldKey": "CREATOR_BUSINESS_TYPE",       "label": "사업자 구분",   "currentValue": "개인사업자",   "requestedValue": "비사업자",       "changed": true, "source": "REQUEST" },
    { "fieldKey": "CREATOR_BUSINESS_REG_NUMBER", "label": "사업자등록번호", "currentValue": "312-81-40177", "requestedValue": "폐업",           "changed": true, "source": "DERIVED" },
    { "fieldKey": "CREATOR_RESIDENT_NUMBER",     "label": "주민등록번호",   "currentValue": null,           "requestedValue": "950812-*******", "changed": true, "source": "REQUEST" },
    { "fieldKey": "PAYOUT_METHOD",               "label": "지급 방식",      "currentValue": "세금계산서 · 리워드 + 부가세", "requestedValue": "원천징수 3.3%", "changed": true, "source": "DERIVED" },
    { "fieldKey": "ACCOUNT_HOLDER",              "label": "정산 계좌 예금주", "currentValue": "정다은",      "requestedValue": null, "changed": false, "source": "CHECK" },
    { "fieldKey": "SETTLEMENT_ACCOUNT",          "label": "정산 계좌",      "currentValue": "신한 110-***-**5520", "requestedValue": null, "changed": false, "source": "LIVE" }
  ],
  "changedFieldLabels": ["사업자 구분", "주민등록번호"],
  "holderCheck": { "currentHolder": "정다은", "rule": "REAL_NAME", "mismatch": false, "requestedHolder": null, "companyName": null },
  "compareItems": [
    { "key": "REPRESENTATIVE_NAME", "label": "대표자 성명",    "value": "정다은",       "serverCheck": null },
    { "key": "BIRTHDAY",            "label": "생년월일",       "value": "1995-08-12",   "serverCheck": "MATCH" },
    { "key": "BUSINESS_REG_NUMBER", "label": "사업자등록번호", "value": "312-81-40177", "serverCheck": null },
    { "key": "CLOSURE_DATE",        "label": "폐업일",         "value": null,           "mustBeBefore": "2026-08-07T19:40:00", "serverCheck": null }
  ],
  "referenceItems": [
    { "label": "주민등록번호",     "value": "950812-*******" },
    { "label": "정산 계좌 예금주", "value": "정다은" }
  ],
  "evidence": { "documentLabel": "폐업사실증명원", "fileName": "폐업사실증명원_다은스튜디오.pdf", "fileSizeBytes": 419430, "extension": "pdf", "…": "…" },
  "approvalImpact": {
    "appliesFrom": "NEXT_SETTLEMENT", "payoutMethodBefore": "TAX_INVOICE", "payoutMethodAfter": "WITHHOLDING_3_3",
    "retainedSettlements": [], "accountChangeRequired": false, "accountHolderRuleAfter": "REAL_NAME"
  }
}
```

시안 C3b의 「예금주 다은스튜디오(상호) · ✕ 본인 실명 필요 · 승인 후 변경 필요」는 1-9에 따라 현재 데이터로는 나오지 않는다 — 위 예시처럼 실명 · 통과로 내려간다.

## A-4. 반려 사유 ← C8b

```
GET /v1/admin/change-requests/reject-reasons?type=TO_BUSINESS
[
  { "code": "LICENSE_UNREADABLE",           "label": "사업자등록증 판독 불가 (흐림·잘림)",         "detailRequired": false },
  { "code": "LICENSE_OWNER_NAME_MISMATCH",  "label": "등록증의 대표자명이 본인 실명과 다름",       "detailRequired": false },
  { "code": "BUSINESS_NUMBER_MISMATCH",     "label": "사업자등록번호가 등록증과 다름",             "detailRequired": false },
  { "code": "BUSINESS_SUSPENDED_OR_CLOSED", "label": "휴업 · 폐업 상태의 사업자",                  "detailRequired": false },
  { "code": "CLOSURE_NOT_CONFIRMED",        "label": "폐업 사실이 확인되지 않음 (비사업자 전환)",  "detailRequired": false },
  { "code": "OTHER",                        "label": "기타",                                       "detailRequired": true  }
]
```

## A-5. 신규 에러 코드

| 코드 | HTTP | 메시지 |
| --- | --- | --- |
| `CHANGE_REQUEST_TYPE_NOT_ALLOWED` | 400 | 요청할 수 없는 변경 유형입니다. |
| `CHANGE_REQUEST_STALE` | 409 | 요청 이후 회원 정보가 바뀌어 처리할 수 없습니다. |
| `CHANGE_REQUEST_REQUESTER_INACTIVE` | 409 | 탈퇴한 회원의 요청은 처리할 수 없습니다. |
