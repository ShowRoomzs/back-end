# 57 포트원 V2 본인인증(KCP) — 테스트 시나리오

- 작성일: 2026-10-24 · 기준 코드 `feat/add` `5a6eb690` + 57 구현분(미커밋 · V217)
- 근거: [57 설계서](57_포트원_KCP_본인인증_연동_BE_설계서.md) — **14절(구현 메모)이 본문보다 우선**이다 · 시안 `C0 로그인, 회원가입.dc.html`(1b · 1d · 1e) · `C15 설정.dc.html`(1c) · 포트원 서버 SDK `io.portone:server-sdk:0.24.0` 소스(JSON 필드명 · 오류 `type` 판별의 정본)
- 케이스 ID 는 영역 접두를 붙였다 — 통합: `AU` 토큰 · `ST` 시작 · `CP` 완료 · `AG` 14세 · DI · `SG` 가입 · `DP` 중복 · `RV` 재인증 · `WD` 탈퇴 · `PU` 파기 · `E2E` / 단위: `SV` 서비스 · `EN` 엔티티 · `GW` 게이트웨이(HTTP) · `MP` 게이트웨이(매핑) · `WR` 배선 · `CY` 암호화. 실행 기록에는 `57/CP-04` 처럼 문서 번호를 붙인다.
- 「자동」 열은 자동 테스트의 클래스다(표시 이름 머리가 케이스 ID). ⬜ 는 자동 테스트가 없다 — 실연동 · 실 MySQL 확인이 필요하다(13절).

## 1. 범위와 판정 기준

- **대상**: 앱 `POST /v1/user/identity-verifications`(시작) · `POST /v1/user/identity-verifications/{id}/complete`(완료) · 결합 지점 `POST /v1/user/auth/social/signup`(가입) · `POST /v1/user/settings/account/verifications`(재인증) · `DELETE /v1/user/auth/withdraw`(탈퇴 파기) · `IdentityVerificationPurgeScheduler`(30일 파기) · 게이트웨이 `PortOneIdentityV2Gateway` / `FakeIdentityGateway` 배선 · V217.
- **범위 밖**: 앱 화면(RN) · KCP 인증창 UI · 어드민 표기(「휴대폰 본인인증(PASS)」 유지) · 토스 지급대행 셀러 본인인증 문자 · 주문 시점 재인증 · 연속 실패 잠금(미정).
- **판정 우선순위**: 설계서 14절 → 설계서 본문 → 시안. 코드와 다르면 15절에 적는다.
- **실행 환경**: H2(MySQL 모드) 위 MockMvc 통합 테스트 · `portone.enabled=false` → `FakeIdentityGateway`. 게이트웨이 HTTP 는 JDK `HttpServer` 스텁 + **실제 SDK**. 배선은 `ApplicationContextRunner`.
- **H2 한계**: `identity_di_hash` UNIQUE 는 엔티티(`unique = true`)로 H2 에도 생겨 DP-07 을 재현한다. 그러나 **실 MySQL 의 동시 트랜잭션 경합 · V217 DDL 자체**는 여기서 검증하지 않는다(13절).

### 1-1. 공통 준비 데이터

| 데이터 | 구성 |
| --- | --- |
| 가입 중 사용자 | `kim`(`GUEST`) + **registerToken**(`createAuthToken(username, +5분)` — role 클레임 없음 · 소셜 로그인이 신규 회원에게 주는 것과 같은 모양) |
| 다른 가입 중 사용자 | `park` · `lee`(`GUEST`) — 중복 · 소유 케이스 |
| 가입 완료 사용자 | `registerVerifiedUser` — 시작 → 완료 → 가입까지 API 로 만든 `USER` · DI = `FakeIdentityGateway.defaultDiOf(username)` |
| 바로 만든 회원 | `mia`(`USER` · 본인인증 없음) — 재인증 케이스 · accessToken = `bearerToken(username, USER, id)` |
| Fake 기본 인물 | `홍길동 · 1998-04-12 · FEMALE · 01000000000 · SKT · 내국인` · CI/DI = `sha256("fake-ci:"/"fake-di:" + username)` · `pgTxId = "pg-" + id` |
| Fake 시나리오 | `will(id, READY · FAILED(CS24) · UNDER_14 · NO_DI · TIMEOUT · NOT_FOUND)` · `willReturnDi(id, di)`(다른 사람과 같은 DI) |
| 시각 | `expires_at` · `requested_at` · `verified_at` 을 SQL 로 옮긴다(만료 · 1분 창 · 30일 · 최신 행) |

### 1-2. 공통 확인 방법

1. 쓰기 한 번마다 **응답 코드 · `identity_verification` 행(status · fail_reason · 개인정보 열) · `users` 행**을 같이 본다.
2. **개인정보 비움**은 8열(`verified_name · birth_date · gender · phone · operator · foreigner · ci_enc · di_enc`)이 **전부** null 인지로 본다(`assertPersonalDataPurged`).
3. **원문 누출**은 응답 본문 **문자열 검색**으로 본다 — 실명 · 생년월일(`1998`) · 전화 · CI · DI 원문. 필드 값 비교만으로는 다른 필드로 새는 것을 못 잡는다.
4. 「포트원을 다시 묻지 않는다」는 `FakeIdentityGateway.lookupCalls()` 가 그대로인지로 본다.
5. 응답 `code` 는 enum 이름과 같다. 예외: `INVALID_INPUT_VALUE` → **`INVALID_INPUT`**, `UNAUTHORIZED_ACCESS` → **`UNAUTHORIZED`**.

---

## 2. AU 토큰 · 권한

두 경로는 `SecurityConfig` 화이트리스트다 — registerToken 은 role 이 없어 필터가 인증을 채우지 않으므로 컨트롤러가 토큰을 직접 검증한다.

| ID | 시나리오 | 기대 | 자동 |
| --- | --- | --- | --- |
| AU-01 | 토큰 없이 시작 · 완료 | 401 `UNAUTHORIZED` | IdentityVerificationIntegrationTest |
| AU-02 | 만료된 registerToken 으로 시작 | 401 · 행 0개 | 〃 |
| AU-03 | 서명이 틀린 토큰 · `Bearer ` 접두 없음 | 401 | 〃 |
| AU-04 | 브랜드 셀러 토큰(subject 가 users 에 없음) | 404 `USER_NOT_FOUND` | 〃 |
| AU-05 | registerToken 으로 시작한 행을 같은 username 의 accessToken 으로 완료 | 200 — 주인은 토큰 종류가 아니라 username | 〃 |

## 3. ST 시작 (설계서 3-1)

| ID | 시나리오 | 기대 | 자동 |
| --- | --- | --- | --- |
| ST-01 | GUEST · `SIGNUP` | 201 · id `idv`+ULID 29자 · `^[A-Za-z0-9]{1,40}$` · storeId · channelKey(Fake) · expiresAt ≈ +10분 · `READY` 행(요청자 · 채널 키) | 통합 |
| ST-02 | 1분 안 재시작 | 같은 id · 행 1개 | 통합 |
| ST-03 | `requested_at` 을 61초 전으로 → 재시작 | 새 id · 앞 행 `READY` 유지 | 통합 |
| ST-04 | 완료(`VERIFIED`) 직후 재시작 | 새 id — 재사용은 READY 만 | 통합 |
| ST-05 | 다른 사용자가 1분 안에 시작 | 서로 다른 id | 통합 |
| ST-06 | 가입된 `USER` 가 `SIGNUP` | 400 `ALREADY_REGISTERED` | 통합 · SV-03 |
| ST-07 | GUEST 가 `REVERIFY` | 404 `USER_NOT_FOUND` | 통합 · SV-04 |
| ST-08 | 탈퇴 회원이 `REVERIFY` | 403 `USER_WITHDRAWN` | 통합 · SV-05 |
| ST-09 | `purpose` 누락 `{}` · 모르는 값 `LOGIN` | 400 `INVALID_INPUT` · 행 0개 | 통합 |
| ST-10 | 시작 응답 필드 | 정확히 4개(`identityVerificationId · storeId · channelKey · expiresAt`) — 개인정보 없음 | 통합 |

## 4. CP 완료 (설계서 3-2)

| ID | 시나리오 | 기대 | 자동 |
| --- | --- | --- | --- |
| CP-01 | 정상 완료 | 200 `VERIFIED` · `maskedName 홍길*` · `maskedPhone 010-****-0000` · 행에 개인정보 · CI/DI 는 **암호문**(복호화하면 Fake 값) · `pgTxId` | 통합 |
| CP-02 | 완료 응답 원문 검색 | 실명 · `1998-04-12` · `1998` · 전화 · CI · DI 원문 · `Enc` 없음 | 통합 |
| CP-03 | `VERIFIED` 행 완료 2회 | 200 · 200 · 포트원 조회 1회 | 통합 · SV-15 |
| CP-04 | 포트원 `READY`(창만 닫힘) → 2회 → 그 뒤 인증 | 409 `NOT_COMPLETED` · 409 · 행 READY 유지 · 마지막 200 | 통합 · SV-21 |
| CP-05 | 포트원이 모르는 id(SDK `onError` 뒤 완료) | 409 `NOT_COMPLETED` | 통합 · SV-20 |
| CP-06 | 포트원 `FAILED(CS24)` → 포트원이 뒤늦게 VERIFIED 로 바뀌어도 재호출 | 422 · `FAILED(PG_FAILED)` · pg_code · pg_message 보존 · 재호출 422 · 조회 횟수 그대로 | 통합 · SV-22 |
| CP-07 | 앱이 `pgCode=CLIENT` 를 실어 보냄 + 포트원 FAILED | 행 pg_code = `CS24`(포트원이 이긴다) · txId `iv-tx-9` 기록 | 통합 · SV-39 · EN-13 |
| CP-08 | 앱이 실패 `code` 를 실어 보냄 + 포트원 VERIFIED | 200 — 판정은 서버 조회로만 · txId 기록 | 통합 |
| CP-09 | 본문 필드 65자 | 400 `INVALID_INPUT` · 포트원 조회 0 | 통합 |
| CP-10 | `READY` 행 만료 → 2회 | 409 `EXPIRED` · 409 `EXPIRED` · `FAILED(NOT_COMPLETED)` · 조회 0 | 통합 · SV-17 |
| CP-11 | `VERIFIED` 뒤 만료 → 완료 | 409 `EXPIRED` · 개인정보 비움 | 통합 · SV-16 |
| CP-12 | 남의 id · 없는 id | 404 `IDENTITY_VERIFICATION_NOT_FOUND` · 조회 0 · 남의 행 READY 그대로 | 통합 · SV-10 |
| CP-13 | 포트원 통신 실패 → 같은 id 재호출 | 502 `IDENTITY_GATEWAY_ERROR` · 행 READY · 재호출 200 | 통합 · SV-25 |
| CP-14 | 소비된 행 완료 | 409 `CONSUMED` | 통합 · SV-11 |

## 5. AG 만 14세 · DI 누락 (설계서 0-8 · 0-5)

| ID | 시나리오 | 기대 | 자동 |
| --- | --- | --- | --- |
| AG-01 | 생년월일 = 오늘 − 13년 | 403 `UNDER_MIN_AGE` · `FAILED(UNDER_MIN_AGE)` · 개인정보 8열 null · 가입 400 · users.birthday null | 통합 · SV-32 |
| AG-02 | 14세로 닫힌 행 재완료 | **403 그대로**(1d) · 조회 횟수 그대로 | 통합 · SV-12 |
| AG-03 | DI 가 비어 옴 | 422 · `FAILED(DI_MISSING)` · 개인정보 null | 통합 · SV-33 |
| AG-04 | 14세로 막힌 뒤 새로 시작 | 새 id · 완료 가능(행 1개 = 시도 1회) | 통합 |

## 6. SG 회원가입 결합 (설계서 3-3)

| ID | 시나리오 | 기대 | 자동 |
| --- | --- | --- | --- |
| SG-01 | 완료 → 가입 | 201 · `USER` · 실명 · 생년월일 · 성별 · 전화 · 통신사 · 외국인 · 인증 시각 · DI 해시 = `hmac(DI)` · CI/DI 암호문 · 행 `CONSUMED` · 개인정보 비움 | 통합 · SV-40 |
| SG-02 | 인증 없이 가입 | 400 `IDENTITY_VERIFICATION_REQUIRED` · GUEST · 실명 없음 | 통합 · SV-42 |
| SG-03 | 시작만(READY) 하고 가입 | 400 | 통합 |
| SG-04 | `VERIFIED` 뒤 10분 지나 가입 | 400 · 행은 `VERIFIED` 로 남음(파기 배치 몫) | 통합 |
| SG-05 | `VERIFIED` 행 둘(앞 행 `verified_at` −2분) | 최근 행의 DI 로 가입 · 최근 행 CONSUMED · 앞 행 VERIFIED | 통합 |
| SG-06 | registerToken 재발급(재로그인) 뒤 가입 | 201 — 10분 안의 인증 재사용 | 통합 |
| SG-07 | 닉네임 중복으로 가입 실패 → 닉네임 바꿔 재가입 | 409 `DUPLICATE_NICKNAME` · 행 VERIFIED 유지 · 재가입 201 | 통합 |

## 7. DP DI 중복 가입 차단 (설계서 0-5 · 1-1)

| ID | 시나리오 | 기대 | 자동 |
| --- | --- | --- | --- |
| DP-01 | 활성 `USER` 와 같은 DI 로 완료 | 409 `IDENTITY_ALREADY_REGISTERED` · `FAILED(DUPLICATE_IDENTITY)` · 남의 정보 미저장 · park GUEST | 통합 · SV-34 |
| DP-02 | 중복으로 닫힌 행 재완료 | **409 그대로** | 통합 · SV-12 |
| DP-03 | 같은 DI 의 회원이 `SUSPENDED` | 409 — 정지 중 다른 소셜 새 계정 불가 | 통합 |
| DP-04 | 같은 DI 의 회원이 `CREATOR` | 409 — 활성 회원 전체 | 통합 |
| DP-05 | 같은 DI 의 회원이 **탈퇴 API** 로 탈퇴 | 완료 200 · 가입 201 | 통합 |
| DP-06 | GUEST 둘이 같은 DI 로 완료 | 둘 다 200(활성 아님) | 통합 |
| DP-07 | DP-06 뒤 둘 다 가입 | 먼저 201 · 뒤 **UNIQUE 충돌 → 409** · 뒤 사용자 GUEST · 실명 · 해시 null · 행 VERIFIED(롤백) | 통합 · SV-43 |
| DP-08 | 같은 사람 재인증(같은 DI · 같은 user) | 200 | 통합 · SV-35 |
| DP-09 | 킬 스위치 `duplicate-block=false` | 중복 조회를 하지 않고 VERIFIED(UNIQUE 는 남음 → DP-07 경로) | SV-36 |

## 8. RV 재인증 (C15-2 · 설계서 3-4)

| ID | 시나리오 | 기대 | 자동 |
| --- | --- | --- | --- |
| RV-01 | 시작(REVERIFY) → 완료 → 재인증 | 200 마스킹 · 값 갱신 · DI 해시 · 동의 이력 1 · 행 CONSUMED | 통합 · SettingServiceTest |
| RV-02 | 완료 없이 재인증 | 400 `IDENTITY_VERIFICATION_REQUIRED` · 회원정보 그대로 | 통합 · ConsumerHomeAndSettingsIntegrationTest |
| RV-03 | 완료 뒤 `agreeConsent=false` | 400 `IDENTITY_CONSENT_REQUIRED` · 행 VERIFIED 유지(소비 안 함) | 통합 |
| RV-04 | SIGNUP 행으로 재인증 | 400(용도 분리) | 통합 · SV-41 |
| RV-05 | DI 가 기존과 다름(타인 명의) | 200 · 해시 새 값 — 정책 미정(설계서 13절 #7) | 통합 · SV-38 |
| RV-06 | DI 가 **다른 활성 회원**의 것 | 409 — 타인 명의 허용과 별개로 중복은 막는다 | 통합 |
| RV-07 | 정지 회원 재인증 | 200(설계서 5절) | 통합 · SV-06 |
| RV-08 | 재인증 행 10분 경과 | 400 | 통합 |

## 9. WD 탈퇴 · 재가입 (설계서 5절)

| ID | 시나리오 | 기대 | 자동 |
| --- | --- | --- | --- |
| WD-01 | 미소비 REVERIFY 행이 있는 회원이 **탈퇴 API** | `WITHDRAWN` · 실명 · 전화 · `identity_*` 5열 null · 인증 행 상태 VERIFIED 유지 · 개인정보 비움 | 통합 · EN-16 |
| WD-02 | 탈퇴 → 재가입 복구(`NORMAL · GUEST`) → 예전 인증으로 가입 → 새로 인증 후 가입 | 400 → 201 · 같은 DI 해시가 다시 들어온다 | 통합 |

## 10. PU 개인정보 파기 배치 (설계서 1-2)

| ID | 시나리오 | 기대 | 자동 |
| --- | --- | --- | --- |
| PU-01 | 미소비 VERIFIED 2행(31일 전 · 29일 전) → `purge(now)` | 1건 · 31일 행만 비움 · 상태 VERIFIED 유지 · 29일 행 그대로 | 통합 |
| PU-02 | 40일 전 CONSUMED · FAILED · READY 행 → `purge` 2회 | 0 · 0(이미 비었거나 개인정보가 없다) | 통합 |
| PU-03 | 배치 실행 시각 04:00 KST · 1회 500건 상한 | 크론 · 상한 | ⬜ 코드 리뷰 |

## 11. E2E

| ID | 시나리오 | 기대 | 자동 |
| --- | --- | --- | --- |
| E2E-01 | registerToken → 인증 → 가입(응답 accessToken) → C15-2 조회 → 재인증 → 재조회 | 조회 `홍길* · 1998.04.** · 010-****-0000` · 본문에 원문 없음 · 재인증 행 CONSUMED | 통합 |

---

## 12. 단위 테스트

### 12-1. SV 서비스 — `IdentityVerificationServiceTest`(저장소 · 게이트웨이 목 · 실제 cipher)

| ID | 확인 | 
| --- | --- |
| SV-01 ~ 08 | 시작 — 저장 값 · ttl 반영 · 60초 창(`now − 60s`) 재사용 · 역할 · 탈퇴 · 정지 · CREATOR · 모르는 사용자 · Fake `bind` |
| SV-10 ~ 17 | 행 상태 — 404(조회 0) · CONSUMED · **저장된 사유 → 코드 5종**(파라미터) · 사유 없음 · enum 전부 매핑 · VERIFIED 멱등 · 만료(VERIFIED · READY) |
| SV-20 ~ 25 | 포트원 응답 — empty · READY · FAILED(+failure 없음) · 거절 → 502 · 결과 모름 → 502 · 행 READY 유지 |
| SV-30 ~ 39 | 판정 — 암호문 · verifiedAt(포트원 / 서버 시각) · **14세가 DI 중복보다 먼저** · DI null/""/"  " · 중복 · 자기 자신 · 킬 스위치 · CI 없음 허용 · 타인 명의 재인증 · 앱 값 기록 |
| SV-40 ~ 43 | 소비 — 반영 필드 · 해시 = hmac(복호화 DI) · 용도 분리 · 없음 400 · UNIQUE 충돌 → 409 |
| SV-50 ~ 53 | 14세 — 생일 당일 통과 · 하루 전 차단 · **윤일생**(2/28 차단 · 3/1 통과) · 해석 불가 통과 |
| SV-60 ~ 62 | id — 1만 회 KCP 정규식 · 중복 없음 · 앞 10자 시각 되읽기 · Crockford(I · L · O · U 없음) |

### 12-2. EN 엔티티 — `IdentityVerificationEntityTest`

EN-01 ~ 02 생성 · 소유 / EN-03 ~ 06 만료 경계(정각부터 만료 · READY 는 소비 불가 · 닫힌 행은 만료 아님) / EN-07 ~ 11 전이(verified · failed 가 VERIFIED 뒤에도 비움 · 길이 자르기 64/255 · consume · purge 상태 유지) / EN-12 ~ 14 앱 값 기록(덮지 않음 · 빈 값 무시 · 64자) / EN-15 ~ 16 `Users.applyIdentity` · `purgeOnWithdrawal`.

### 12-3. GW 게이트웨이 HTTP — `PortOneIdentityV2GatewayHttpTest`(JDK HttpServer + 실제 SDK)

| ID | 확인 |
| --- | --- |
| GW-01 ~ 02 | 요청 — `GET /identity-verifications/{id}?storeId=…` · `Authorization: PortOne {secret}` · Accept JSON · storeId() · channelKey() |
| GW-10 ~ 18 | 응답 — VERIFIED 전 필드 · UTC → KST · CI 없음 · **모르는 필드 무시** · 전화 하이픈 제거 · FAILED(+pgTxId 없음 · failure `{}`) · READY · 모르는 status → 결과 모름 · 깨진 JSON · 필수 필드(`pgRawResponse`) 누락 → 결과 모름 |
| GW-20 ~ 23 | 오류 — 404 `IDENTITY_VERIFICATION_NOT_FOUND` → empty · 401 · 403 · 400 → 거절(`type` · cause) · 5xx · 모르는 `type` · HTML → 결과 모름 |
| GW-30 ~ 31 | 통신 — 응답 지연 3초 > 설정 1초 → 「타임아웃」 · 2.5초 안에 끊김(SDK 60초 무시) · 연결 거부 → 결과 모름 |
| GW-40 ~ 41 | 기동 — store-id · api-secret · channel-keys.identity 누락 메시지 · 결제 채널 키 없이도 뜬다 |

### 12-4. MP · WR · CY

- **MP** `PortOneIdentityV2GatewayTest` — SDK 객체 → 모델(VERIFIED · 선택 필드 null · FAILED · READY · Unrecognized) · `VerifiedCustomer.toString` 에 원문 없음.
- **WR** `PortOneIdentityGatewayWiringTest` — WR-01 기본값은 실연동(키 없으면 기동 실패) · WR-02 두 스위치 ON → V2 하나 · WR-03 `portone.enabled=false` → Fake · WR-04 결제 ON + `identity.enabled=false` → Fake(채널 키 없이) · WR-05 채널 키만 없음 → 기동 실패 · 끄는 방법 안내 · WR-06 기본값(ttl 10 · 중복 차단 ON).
- **CY** `PersonalDataCipherTest` — hmac 결정성 · 키 의존 · null · 암호문 무작위 · 복호화.

---

## 13. 자동화하지 않은 것 — 실연동 · 실 MySQL

| # | 항목 | 방법 | 설계서 |
| --- | --- | --- | --- |
| 1 | KCP 테스트 채널 실호출 1회 — `ci · di · phoneNumber · operator · isForeigner` 수신 · 특히 **CI**(가맹점 설정) | 테스트 채널 키로 `portone.identity.enabled=true` · 앱 SDK 창 → 완료 API · `identity_verification` 행 확인 | 13절 #1 · #2 |
| 2 | KCP SDK 창 안 SMS · PASS 앱 갈래 · 모바일 `windowType` 기본값 | 실기기(Android · iOS) | 13절 #4 · #4b |
| 3 | KCP 관리자 「신규 연동방식(V2) 사용」 OFF 일 때 `CS24` | 테스트 채널에서 일부러 OFF → 완료 API 422 · pg_code `CS24` | R-5 |
| 4 | V217 DDL · 엔티티 정합 | 실 MySQL 에서 `ShowroomzApplicationTests`(Flyway) | 8절 |
| 5 | 동시 가입 경합(같은 DI · 별도 트랜잭션 동시 커밋) | 실 MySQL · 두 세션 — 하나 201 · 하나 409 | 1-1 |
| 6 | 파기 배치 크론 04:00 · 상한 500 | 운영 로그 「본인인증 개인정보 파기 - N건」 | 1-2 |
| 7 | `MaxTransactionCountReached` 의 뜻 | 포트원 문의 | 13절 #12 |

## 14. 실행

```
./gradlew test --tests '*IdentityVerification*' --tests '*PortOneIdentity*' --tests '*PersonalDataCipherTest' \
  --tests '*SettingServiceTest' --tests '*ConsumerHomeAndSettingsIntegrationTest'
```

2026-10-24 실행 — **195건 통과**(본인인증 162 = 통합 61 · SV 51 · EN 16 · GW 19 · WR 6 · MP 5 · CY 4 / 설정 회귀 33).

## 15. 이 시나리오를 쓰며 고친 것

| # | 결함 | 수정 | 케이스 |
| --- | --- | --- | --- |
| 1 | 닫힌(FAILED) 행으로 완료를 다시 부르면 **사유와 무관하게 422** — 14세 미만인데 1e(재시도) 화면으로 가고, 명의 중복인데 「인증 실패」로 보였다. 설계서 3-2 「저장된 사유 그대로」와 다름 | `IdentityVerificationService.errorOf(failReason)` — `UNDER_MIN_AGE → 403` · `DUPLICATE_IDENTITY → 409 IDENTITY_ALREADY_REGISTERED` · `NOT_COMPLETED → 409 EXPIRED` · `PG_FAILED · DI_MISSING · null → 422` | AG-02 · DP-02 · CP-10 · SV-12 · SV-13 · SV-14 |
