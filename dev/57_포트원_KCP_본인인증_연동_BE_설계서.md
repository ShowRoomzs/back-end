# 포트원 V2 본인인증(KCP 휴대폰 본인확인) 연동 — 백엔드 기능 설계서

- 근거 시안: `dev/1021 디자인파일/SHOWROOMZ 플랫폼 디자인/공유용_1021/C0 로그인, 회원가입.dc.html` — 1b **C0-2 본인인증**(소셜 로그인 직후 · 회원가입 전) · 1d **만 14세 미만 차단** · 1e **PASS 인증 실패 · 타임아웃** / `C15 설정.dc.html` — 1c **C15-2 회원정보 — 조회 · 재인증 갱신**
- 상류 설계: `dev/완료/결제 기능 구현 계획서.md`(포트원 V2 연동 방식 · 6-1 게이트웨이 추상화 · 6-2 설정 · 6-3 보안 · 5-7 웹훅) · `dev/완료/결제 연동 앱 전달사항.md`(RN SDK · 키는 앱에 박지 않는다) · `51-6_소비자_계정이용정지_BE_설계서.md` 6절(탈퇴 · 재가입)
- 근거 외부 문서(2026-10-24 조회): 포트원 개발자센터 — 본인인증 연동하기 V2(`/opi/ko/extra/identity-verification/readme-v2`) · KCP 본인인증 V2(`/opi/ko/integration/pg/v2/kcp-v2-identity-verification`) · 브라우저 SDK 본인인증 요청 · 응답 파라미터(`/sdk/ko/v2-sdk/identity-verification-request` · `-response`) · 웹훅 V2(`/opi/ko/integration/webhook/readme-v2`) · REST V2 본인인증 API(`/api/rest-v2/identityVerification`) · RN SDK 소스(`github.com/portone-io/react-native-sdk` `src/index.tsx` · `src/SdkDelegate.tsx`) · 서버 SDK `io.portone:server-sdk:0.24.0` jar(`identityverification` 패키지 — 응답 스키마의 정본으로 썼다). 조사 결과는 바로 아래 **「조사 결과(사실)」** 절에 모았고, 문서에 **없어서 확인해야 하는 것**은 13절에 남겼다.
- 인증사 선정 근거: 연동 가이드의 인증사별 제공 필드 표 — **KCP** 는 `di · gender · operator · phoneNumber · isForeigner` **항상 제공**, `ci` 는 **가맹점 설정에 따름** / 다날은 `operator · phoneNumber · isForeigner` **추가 계약** · `ci` 계약 시 신청 / KG이니시스 통합인증은 **`di` 미제공** · 카카오 `ci` 별도 서류. 가입 · 재인증이 **전화번호**를 저장하고(`Users.phoneNumber`) 중복 가입 차단에 **DI** 가 필요하므로 KCP 다. 토스페이먼츠에는 본인인증 상품이 없고 「토스인증」은 별개 서비스(전화번호 미반환)라 후보에서 뺐다.
- 범위: 포트원 V2 **본인인증 게이트웨이**(SDK `IdentityVerificationClient`) · 앱 **인증 시작 · 완료 API 2종** · 회원가입 `POST /v1/user/auth/social/signup` 의 **인증 결과 결합**(임시 데이터 제거) · C15-2 **재인증** 결합 · `Users` 에 **CI · DI 저장**(암호화 · 중복 가입 차단) · 만 14세 차단 · 실패 · 타임아웃 분기 · 탈퇴 파기 · 테스트 더블
- 범위 밖: 앱 화면 구현(RN — 이 문서는 서버 계약 + 앱이 호출할 SDK 1줄만) · 인플루언서 · 셀러 가입의 본인인증(인플루언서 신청은 `Users` 값을 그대로 쓴다 — `CreatorApplicationService` 주석) · 주문 시점 재인증(C0-2 부제 「한 번만 인증하면 주문할 때 다시 하지 않아도 돼요」) · 재인증 주기 정책(C0 남은 결정 ①) · 연속 실패 잠금(C0-1e 남은 결정 ①) · 어드민 화면 변경(「인증 수단(PASS)」 표기는 그대로 — 13절 #6) · 토스 지급대행 셀러의 본인인증 문자(44 토스 설계서 — 다른 축)
- 구현 위치: `showroomz.global.payment.portone.identity.{PortOneIdentityGateway, PortOneIdentityV2Gateway, FakeIdentityGateway, IdentityVerificationResult}`(신규) · `showroomz.api.app.auth.{controller, docs, DTO, service}`(`IdentityVerificationService` **재작성** · `IdentityVerificationController` 신규) · `showroomz.domain.member.user.{entity, repository}`(`Users` 열 추가 · `identity_verification` 표) · `api.app.setting.service.SettingService`(재인증 결합)
- 작성일: 2026-10-24 · @hc · 기준 코드 `feat/add`(5a6eb690) · 최신 마이그레이션 **V216** — 이 설계서가 파는 것은 **V217** 하나(동시 진행 작업이 먼저 잡으면 뒤로 민다)
- 구현 상태: ✅ **구현 완료**(2026-10-24 · 14절 구현 메모 · 관련 테스트 78건 통과 — 통합 `IdentityVerificationIntegrationTest` 31 · `ConsumerHomeAndSettingsIntegrationTest` 18(갱신) · 단위 `IdentityVerificationServiceTest` 5 · `PersonalDataCipherTest` 4 · `PortOneIdentityV2GatewayTest` 5 · `SettingServiceTest` 15(갱신). 탈퇴 경로 회귀 `Design1021ConsumerWithdrawalIntegrationTest` · `ConsumerSanctionEdgeIntegrationTest` · `Design1021P7SanctionWithdrawalIntegrationTest` 통과). **운영 전 남은 것**은 13절 #1(KCP 채널 · CI 설정) 과 7단계 실호출 확인(#2 · #4 · #4b · #12) — 채널 키가 들어오기 전까지 `portone.identity.enabled=false` 로 `FakeIdentityGateway` 가 붙는다.

---

## 조사 결과(사실) — 포트원 V2 본인인증 · KCP

아래는 전부 문서 · SDK 에 적힌 범위다. 설계 본문은 이 절의 번호(R-n)를 인용한다.

### R-1. 흐름 · 엔드포인트

| 단계 | 사실 |
| --- | --- |
| 요청 | 브라우저 SDK `PortOne.requestIdentityVerification({ storeId, identityVerificationId, channelKey, redirectUrl?, customer?, windowType?, bypass?, customData?, forceRedirect? })`. 필수는 `storeId · identityVerificationId` + `channelKey`(또는 채널 그룹 ID) |
| 응답(팝업 · iframe) | `{ transactionType: "IDENTITY_VERIFICATION", identityVerificationId, identityVerificationTxId, code?, message?, pgCode?, pgMessage? }` — **`code` 가 있으면 실패**. 리다이렉트 방식은 같은 필드가 `redirectUrl` 의 **쿼리 문자열**로 온다 |
| 서버 조회 | `GET https://api.portone.io/identity-verifications/{identityVerificationId}` · `Authorization: PortOne {API_SECRET}` · 가이드: 「`status` 가 `VERIFIED` 인지 확인」. 서버 SDK `IdentityVerificationClient.getIdentityVerification(id)` |
| 상태 | `READY · VERIFIED · FAILED` — 응답 타입은 `ReadyIdentityVerification · VerifiedIdentityVerification · FailedIdentityVerification` 의 합집합(+ SDK `Unrecognized`) |
| 그 외 API | `POST …/send`(SMS · APP 방식 요청 전송) · `POST …/confirm` · `POST …/resend`(SMS 재전송) · `GET /identity-verifications`(페이지 조회 — 정렬 `REQUESTED_AT · VERIFIED_AT · FAILED_AT · STATUS_UPDATED_AT`) |
| 에러 타입 | `ChannelNotFound · Forbidden · IdentityVerificationAlreadySent · IdentityVerificationAlreadyVerified · IdentityVerificationNotFound · IdentityVerificationNotSent · InvalidRequest · MaxTransactionCountReached · PgProvider · Unauthorized` |

### R-2. 응답 스키마(서버 SDK 0.24.0 jar 기준)

- `VerifiedIdentityVerification { id, channel(SelectedChannel{type,id,key,name,pgProvider,pgMerchantId}), verifiedCustomer, customData, requestedAt, updatedAt, statusChangedAt, verifiedAt, pgTxId, pgRawResponse, version }`
- `IdentityVerificationVerifiedCustomer { id?, name, operator?(SKT·KT·LGU·SKT_MVNO·KT_MVNO·LGU_MVNO), phoneNumber?, birthDate?(yyyy-MM-dd), gender?(MALE·FEMALE·OTHER), isForeigner?, ci?, di? }` — 가이드: `phoneNumber` 는 「숫자로만 구성」
- `FailedIdentityVerification { …, requestedCustomer{id,name,phoneNumber,birthYear,birthMonth,birthDay}, failure{reason, pgCode, pgMessage}, pgTxId }`
- `ReadyIdentityVerification { …, requestedCustomer }`

### R-3. 인증사별 제공 필드(연동 가이드 표)

| 필드 | KCP | 다날 | KG이니시스 통합인증 |
| --- | --- | --- | --- |
| `ci` | **가맹점 설정에 따름** | 계약 시 신청 | 카카오 외 항상 · 카카오는 별도 서류 |
| `di` | 항상 | 항상 | **미제공** |
| `gender` | 항상 | 항상 | 카카오 외 항상 |
| `operator` | 항상 | 추가 계약 | 미제공 |
| `phoneNumber` | 항상 | 추가 계약 | 항상 |
| `isForeigner` | 항상 | 추가 계약 | 카카오 · 네이버 외 항상 |

가이드 주의: 「`di` 는 KG이니시스에서 제공되지 않으므로 중복 가입 검사에서 `null` 을 별도 처리」 · 「연령 제한 · 1인 1계정은 `birthDate` 와 `di` 로 구현」 · 「인증 완료 여부는 서버에서 확인」.

### R-4. `identityVerificationId` 규칙

- 가맹점이 정하는 문자열 · 요청 · 조회의 키. 「같은 id 로 여러 번 시도 가능하나 **최종 성공은 단 한 번**」 · 「이미 완료된 id 로 다시 시도하면 **실패**」.
- **KCP: 영문 대소문자 · 숫자만 · 40자 이하**(KCP 페이지 유의사항). 가이드 예시 `identity-verification-${uuid}` 의 하이픈은 KCP 에서 쓸 수 없다.

### R-5. KCP 채널 · 설정(KCP 페이지)

- 포트원 콘솔 채널 설정 + **KCP 관리자 페이지** 두 가지: ① 부가서비스 > 휴대폰본인확인 > **인증결과URL** = `checkout-service.prod.iamport.co` ② 「**신규 연동방식(V2) 사용여부 = 사용**」 — 안 켜면 `pgCode CS24` 「정보조회가 올바르지 않습니다」.
- SDK 방식은 연동 가이드(R-1) 그대로. **API 방식도 있다** — `send`(필수 `channelKey · customer{…, operator, method ∈ SMS·APP} · bypass`) → `confirm`(`otp` 는 SMS 만). 즉 `send/confirm` 은 다날 전용이 아니라 **다날 · KCP 공통**이다(가이드: 「다날 및 KCP 는 수기결제처럼 API 호출만으로 진행하는 방식도 지원」).
- `bypass.kcp_v2`(SDK) / `kcpV2`(API): `web_siteid`(**DI 생성에 쓰는 사이트 ID** · 미입력 시 KCP 발급 사이트 ID) · `media_type`(API · PC `MC01` · 모바일 `MC02`) · `cp_sms_msg` · `cp_callback`(인증번호 SMS 발신번호 · 070 · 060 불가).
- 페이지에 **없는 것**: `windowType` 의 KCP 기본값 · PASS 라는 명칭(「APP」 으로만 표기) · SDK 창 안에서 SMS · APP 을 사용자가 고르는지.

### R-6. 웹훅 V2(2024-04-25)

이벤트 타입은 `Transaction.*`(10종) · `BillingKey.*`(5종) **뿐** — 본인인증 이벤트 없음. 「모르는 `type` 은 오류 없이 무시」 · 재전송 5회(0 · 1 · 4 · 16 · 64 · 256분). 서버 SDK `Webhook` sealed class 도 같다.

### R-7. RN SDK(`@portone/react-native-sdk` 소스)

- `IdentityVerification = createSdkDelegate<IdentityVerificationRequest, IdentityVerificationResponse>('requestIdentityVerification')` — `Payment` 와 **같은 구조**. 타입은 `@portone/browser-sdk` 것.
- `redirectUrl` 은 **런타임에 `portone://blank` 로 덮어쓴다**(호출자 값 무시). 결과는 `portone://` 이동을 가로채 **쿼리 문자열 → 객체**로 `onComplete` 에 준다(값은 전부 문자열 · 런타임 검증 없음). `onError` 는 SDK Promise 가 **시작 전** reject 될 때만.
- 공식 문서는 RN 본인인증 사용법을 따로 적지 않고 「Git README · 샘플 참고」로 끝난다 — README 도 `IdentityVerification` 이 export 된다는 것까지만.

---

## 0. 먼저 정한 11가지

### 0-1. 인증은 **앱 → 포트원 SDK → 서버 조회** 다 — 서버는 앱이 준 결과를 믿지 않는다

결제와 같은 원칙(결제 계획서 2절 · 앱 전달사항 0-2). 앱은 포트원 RN SDK 로 KCP 인증창을 띄우고, 끝나면 **`identityVerificationId` 만** 서버에 보낸다. 서버가 `GET /identity-verifications/{id}` 로 포트원에 직접 물어 `VERIFIED` 인지 · 누구인지 판정한다. 앱이 이름 · 생년월일 · 전화번호를 보내는 경로는 **없다** — 앱이 보내면 앱이 조작할 수 있다.

### 0-2. `identityVerificationId` 는 **서버가 발급**한다 — 시작 API 가 있다

포트원 V2 의 `identityVerificationId` 는 가맹점이 정하는 고유 문자열이다(결제의 `paymentId` 와 같은 모델). 앱이 임의로 만들면 서버는 「이 인증이 이 사용자의 것인가」를 알 수 없다. `POST /v1/user/identity-verifications` 가 **사용자(username) 에 묶인 id · storeId · channelKey** 를 내리고 `identity_verification` 표에 `READY` 행을 남긴다(1-2). 완료 API 는 그 행의 주인과 호출자가 같은지 먼저 본다.

storeId · channelKey 를 응답에 싣는 것도 결제와 같다 — **앱에 키를 박지 않는다**(앱 전달사항 1-1). 테스트 → 운영 채널 전환이 서버 설정 하나다.

### 0-3. 가입은 **2단계 API** 다 — 인증 완료 → 회원가입 완료

C0 흐름은 `소셜 로그인 → C0-2 본인인증 → C0-1 회원가입(닉네임 · 약관)` 이다. 기존 `POST /social/signup` 하나에 인증 조회를 끼워 넣으면 「인증은 됐는데 닉네임이 중복이라 400」 때 인증을 다시 해야 하는지가 애매하고, 14세 차단(1d)이 닉네임 입력 **뒤**에 뜬다(시안은 **앞**).

- `POST /v1/user/identity-verifications/{id}/complete` — 포트원 조회 · 14세 판정 · DI 중복 판정 · 결과를 `identity_verification` 행에 **보관**(`VERIFIED`). 여기서 403 `UNDER_MIN_AGE` 가 난다.
- `POST /v1/user/auth/social/signup` — 요청 본문은 그대로(닉네임 · 약관). 서버가 그 사용자의 **최근 10분 내 `VERIFIED` · 미소비** 행을 찾아 `Users` 에 옮기고 행을 `CONSUMED` 로 닫는다. 없으면 **400 `IDENTITY_VERIFICATION_REQUIRED`**(새 코드). 임시 데이터(`홍길동 · 1998-04-12`)는 **삭제**한다.

registerToken(5분) 과 인증 유효(10분) 는 축이 다르다 — 인증 중 registerToken 이 만료되면 소셜 로그인을 다시 하되(`REGISTER_EXPIRED`), 같은 username 의 인증 행은 10분 안이면 그대로 쓴다. 인증을 두 번 시키지 않는다.

### 0-4. 재인증(C15-2)도 같은 2 API 를 쓴다 — `purpose` 로 가른다

시작 API 의 `purpose ∈ {SIGNUP, REVERIFY}`. 재인증은 `POST /v1/user/settings/account/verifications`(기존 `reverifyIdentity`) 가 `VERIFIED` 행을 소비한다. 동의(`agreeConsent`) · 이력(`ConsentType.IDENTITY_VERIFICATION`) 은 기존 그대로다. `SIGNUP` 행을 재인증이 집어 쓰지 못하게 소비 때 `purpose` 를 대조한다.

### 0-5. CI · DI 를 저장하고 **DI 로 중복 가입을 차단한다**(확정) — CI 는 보조

`IdentityVerificationService` 의 TODO 「CI/DI 저장(중복 가입 방지)」를 닫는다. 가이드도 「1인 1계정은 `di` 로」 라고 적는다(R-3).

- **DI(중복가입확인정보)** — 가맹점(사이트)별 고유 · 같은 사람은 같은 값(KCP 항상 제공 — R-3). 완료 API 에서 **같은 DI 의 활성 회원(`roleType != GUEST` · `status != WITHDRAWN`)이 다른 username 으로 있으면 409 `IDENTITY_ALREADY_REGISTERED`**. 한 사람이 카카오 · 네이버 · 애플로 계정 3개를 만드는 것을 막는다. **차단은 확정**이고(2026-10-24), `portone.identity.duplicate-block` 은 장애 때 끄는 **킬 스위치**로만 남긴다(기본 `true`).
- DI 는 KCP `web_siteid` 로 만들어진다(R-5). **`web_siteid` 를 바꾸면 전 회원의 DI 가 달라져 중복 차단이 무력화된다** — `bypass` 에 넣지 않고 KCP 발급 기본 사이트 ID 를 쓰며, 바꿀 일이 생기면 CI 로 재대조한다(아래). 운영 런북에 적는다.
- **CI(연계정보)** — 전 사이트 공통 · 인증사를 바꿔도 같은 값. 지금 쓰는 곳은 없지만 **인증사 교체 · `web_siteid` 변경 · 명의도용 조사 · 1인 1계정 재대조**에 필요하고, 나중에 받으려면 전 회원이 재인증해야 한다. KCP 는 「가맹점 설정에 따름」(R-3) — 계약 때 **켠다**(13절 #1).
- 둘 다 **`PersonalDataCipher`(AES-256-GCM) 로 암호화**해 저장한다 — 주민등록번호와 같은 취급. 중복 조회는 암호문으로 할 수 없으므로 **`identity_di_hash`(HMAC-SHA256 · 키 = cipher 키에서 파생)** 열을 따로 두고 **UNIQUE** 를 건다(1-1) — 차단이 확정됐으니 동시 가입 경쟁을 DB 가 막는다. 원문 · 암호문은 로그 · API 응답 · 어드민 화면 어디에도 내리지 않는다.
- KCP 응답에 `di` 가 **비어 있으면**(항상 제공이라 설정 오류다) 통과시키지 않는다 — 행 `FAILED(DI_MISSING)` · 422 `IDENTITY_VERIFICATION_FAILED` · Sentry. 가이드의 「`di` null 별도 처리」(R-3)는 KG이니시스 얘기고, 우리는 KCP 만 쓴다.

### 0-6. 결과 보관은 `identity_verification` 표 — `Users` 에는 **최종 값만**

포트원 응답(`pgRawResponse` · `pgTxId` · 통신사 · 외국인 여부 · 실패 사유)을 `Users` 에 열로 늘리지 않는다. 인증 1회 = 1행(`identity_verification`) 으로 두고, `Users` 에는 지금 있는 `name · birthday · gender · phoneNumber · identityVerifiedAt` + `identity_ci_enc · identity_di_enc · identity_di_hash` 만 더한다. 실패 · 타임아웃 · 미소비 행이 **조사 자료**가 되고(명의도용 · 연속 실패), 14세 미만 행은 즉시 파기한다(0-8).

### 0-7. 상태는 포트원 3종을 그대로 쓰고 우리 것 2종을 더한다

포트원 `IdentityVerificationStatus { READY · VERIFIED · FAILED }` + 우리 `CONSUMED`(Users 에 반영됨) · `EXPIRED`(10분 안에 완료 호출이 없었음 — 배치 아님, 읽을 때 판정). 전이: `READY → VERIFIED → CONSUMED` / `READY → FAILED` / `READY|VERIFIED → EXPIRED`(파생). 완료 API 를 두 번 불러도 `VERIFIED` 는 멱등(같은 응답) · `CONSUMED` 는 409.

### 0-8. 만 14세 미만은 **결과를 저장하지 않는다** — 시안 1d 「인증 정보는 가입 가능 여부 확인에만 쓰이고 즉시 폐기됩니다」

완료 API 가 `birthDate` 로 14세 미만을 판정하면 403 `UNDER_MIN_AGE` 를 내리고, 행은 `FAILED(reason = UNDER_MIN_AGE)` 로 닫되 **개인정보 열(이름 · 생년월일 · 전화 · CI · DI · pgRawResponse)을 비운 채** 남긴다. 「14세 미만 시도가 있었다」만 남는다. `isUnderMinAge` 판정식(`Period.between … < 14`)은 기존 것을 옮긴다.

### 0-9. 실패 · 타임아웃은 **앱이 먼저 알고** 서버는 기록만 한다

KCP 인증창에서 실패 · 닫힘 · 통신사 불일치가 나면 RN SDK `onComplete` 가 `code` 를 들고 돌아온다(1e 「시간이 초과되었거나 인증 창이 닫혔어요」). 앱은 그래도 **완료 API 를 부른다**(결제의 「성공 · 실패와 관계없이 complete」와 같다 — 앱 전달사항 0-2). 서버가 포트원을 조회해 `FAILED` 면 행을 `FAILED(pgCode · pgMessage)` 로 닫고 **422 `IDENTITY_VERIFICATION_FAILED`**, 아직 `READY` 면(창만 닫힘) **409 `IDENTITY_VERIFICATION_NOT_COMPLETED`**. 둘 다 앱은 1e 화면 — 「다시 인증하기」는 시작 API 를 **다시** 불러 새 id 를 받는다(같은 id 재사용 금지 — 포트원이 `IdentityVerificationAlreadyVerified` · 상태 혼선).

「다시 인증하기」가 **새 id** 인 이유 — 포트원은 같은 id 로 재시도를 허용하지만(R-4) `FAILED` 로 닫은 행을 다시 `READY` 로 되돌리면 조사 자료(실패 사유)가 지워지고, `VERIFIED` 뒤 재시도는 포트원이 거절한다. 행 1개 = 시도 1회가 단순하다.

「문자로 인증하기」(1e 보조 버튼)는 서버에 별도 API 가 없다 — 시안 1b 「문자(SMS) 인증은 PASS 밖의 별도 수단이 아니라 PASS 인증창 안의 갈래」. 포트원 **API 방식**(`send(method = SMS|APP, operator) → confirm(otp)` · 다날 · KCP 공통 — R-5)을 쓰면 SMS · APP 을 서버가 고를 수 있지만 **쓰지 않는다**: `send` 의 `customer` 가 `name · phoneNumber · identityNumber(주민번호 앞자리)` 를 요구해 **우리 화면이 개인정보를 입력받고 서버가 중계**해야 한다. 시안의 「입력 없이 PASS 가 돌려준다」(1b 설명 · C15-2 「직접 입력은 오히려 신뢰를 떨어뜨린다」) 와 맞지 않고, 입력값 보관 · 전송 책임이 우리에게 생긴다. SDK 창 안에 SMS 갈래가 있는지는 KCP 페이지에 적혀 있지 않아 **확인 뒤** 앱 버튼 유지 여부를 정한다(13절 #4).

### 0-10. 웹훅은 **두지 않는다** — 조회로 충분하다

결제는 `onComplete` 가 오지 않아도 돈이 걸려 웹훅으로 확정했다. 본인인증은 앱이 완료 API 를 안 부르면 **가입이 안 된 것**이고 사용자가 다시 시도하면 된다 — 서버가 몰라서 생기는 손해가 없다. 그리고 **둘 수가 없다** — 웹훅 V2 이벤트는 `Transaction.*` · `BillingKey.*` 뿐이고(R-6) 서버 SDK `Webhook` sealed class 도 같다. 미래에 생겨도 `PaymentWebhookService` 가 모르는 타입을 `IGNORED(200)` 으로 흘린다.

### 0-11. 게이트웨이는 결제와 **분리**한다 — 같은 자격 · 다른 클라이언트

`PortOnePaymentGateway` 에 메서드를 더하지 않는다. 결제 · 본인인증은 실패의 뜻이 다르고(돈 vs 재시도), `FakePaymentGateway` 시나리오 더블에 인증 시나리오를 섞으면 둘 다 읽기 어렵다. 새 포트 `PortOneIdentityGateway` + 구현 `PortOneIdentityV2Gateway`(SDK `IdentityVerificationClient`) + `FakeIdentityGateway`(`portone.enabled=false`). 자격(`storeId · apiSecret · baseUrl · 타임아웃`)은 `PortOneProperties` 를 **공유**하고, 채널 키만 `portone.channel-keys.identity` 를 더한다(6절).

---

## 1. 도메인 모델

### 1-1. `users` 열 추가(V217)

| 열 | 타입 | 뜻 |
| --- | --- | --- |
| `identity_ci_enc` | VARCHAR(512) NULL | CI 암호문(`PersonalDataCipher` · base64) |
| `identity_di_enc` | VARCHAR(512) NULL | DI 암호문 |
| `identity_di_hash` | CHAR(64) NULL | DI 의 HMAC-SHA256 hex — 중복 조회 키. **UNIQUE**(아래) |
| `identity_operator` | VARCHAR(10) NULL | 통신사 `SKT · KT · LGU · SKT_MVNO · KT_MVNO · LGU_MVNO` — 어드민 조사용 · 화면 없음 |
| `identity_foreigner` | TINYINT(1) NULL | 외국인 여부 |

- UNIQUE KEY `uk_users_identity_di_hash (identity_di_hash)` — MySQL 은 NULL 을 여러 행에 허용하므로 GUEST · 탈퇴(해시 비움) 행과 충돌하지 않는다. 1차 판정은 **서비스 조회**(`findActiveByIdentityDiHash` → 409)이고, UNIQUE 는 **같은 DI 두 건이 동시에 소비되는 경쟁**을 막는 2차 방어다 — `consumeForSignup` 의 `DataIntegrityViolationException` 을 409 `IDENTITY_ALREADY_REGISTERED` 로 바꾼다. 킬 스위치(`duplicate-block=false`)를 내리면 서비스 판정만 꺼지고 UNIQUE 는 남는다 — 그때는 두 번째 가입이 409 가 아니라 DB 충돌로 같은 409 를 받는다(의도 — 스위치는 「장애 때 조회 경로를 끄는 것」이지 다계정 허용이 아니다).
- 같은 사람의 **정지(`SUSPENDED`) 계정**은 활성이다 — 정지 중 다른 소셜로 새 계정을 만들면 409. 51-6 0-9 「탈퇴 → 재가입 시 정지 복귀」가 username 기준이던 구멍이 DI 로 일부 막힌다(탈퇴 뒤 다른 소셜은 여전히 뚫린다 — 13절 #3).
- 기존 `name(64) · birthday(10) · gender(10) · phone_number(20) · identity_verified_at` 그대로. `birthday` 는 `yyyy-MM-dd` 10자 — 포트원 `birthDate` 도 같은 형식.
- `purgeOnWithdrawal` 에 5열 null 을 더한다. 탈퇴 뒤 같은 소셜 계정으로 재가입하면 `GUEST` 로 돌아가(51-6 6절) **다시 인증**한다 — DI 가 다시 들어온다.

### 1-2. `identity_verification` — 인증 1회 = 1행(V217)

| 열 | 타입 | 뜻 |
| --- | --- | --- |
| `identity_verification_id` | VARCHAR(40) PK | 포트원에 보내는 id. **`idv` + ULID(26자 · Crockford base32 대문자 · 숫자)** = 29자 — KCP 제약 「영문 대소문자 · 숫자만 · 40자 이하」(R-4) 를 지킨다. 결제 `paymentId`(하이픈 포함) 형식을 **따라가면 안 된다** |
| `user_id` | BIGINT NOT NULL | 요청자(GUEST 포함). FK 없음(users 삭제 없음) |
| `purpose` | VARCHAR(10) NOT NULL | `SIGNUP · REVERIFY` |
| `status` | VARCHAR(10) NOT NULL | `READY · VERIFIED · FAILED · CONSUMED` (`EXPIRED` 는 파생 — 저장 안 함) |
| `channel_key` | VARCHAR(64) NOT NULL | 요청 당시 채널 키(채널 교체 뒤 조사용) |
| `pg_tx_id` | VARCHAR(64) NULL | 포트원 `pgTxId` |
| `verified_name` | VARCHAR(64) NULL | 포트원 `verifiedCustomer.name` — `CONSUMED` 로 옮긴 뒤 **비운다**(아래) |
| `verified_birth_date` | VARCHAR(10) NULL | |
| `verified_gender` | VARCHAR(10) NULL | `MALE · FEMALE · OTHER` |
| `verified_phone` | VARCHAR(20) NULL | 숫자만 |
| `verified_operator` | VARCHAR(10) NULL | |
| `verified_foreigner` | TINYINT(1) NULL | |
| `verified_ci_enc` · `verified_di_enc` | VARCHAR(512) NULL | 암호문 |
| `fail_reason` | VARCHAR(40) NULL | 우리 분류 `PG_FAILED · UNDER_MIN_AGE · DUPLICATE_IDENTITY · DI_MISSING · NOT_COMPLETED` |
| `identity_verification_tx_id` | VARCHAR(64) NULL | SDK 응답 `identityVerificationTxId`(시도 ID — R-1) · 앱이 완료 API 에 실어 주면 기록(선택) |
| `pg_code` · `pg_message` | VARCHAR(64) · VARCHAR(255) NULL | 포트원 `failure.pgCode / pgMessage` |
| `requested_at` | DATETIME(6) NOT NULL | 시작 API 시각 |
| `verified_at` | DATETIME(6) NULL | 포트원 `verifiedAt` |
| `consumed_at` | DATETIME(6) NULL | Users 반영 시각 |
| `expires_at` | DATETIME(6) NOT NULL | `requested_at + 10분` — 이 시각 뒤 `READY|VERIFIED` 는 `EXPIRED` 로 읽는다 |

- KEY `idx_idv_user (user_id, requested_at)`.
- **개인정보 보존 원칙**: `verified_*` · `*_enc` 는 `CONSUMED` 전이 때 **null 로 비운다** — 값은 `Users` 로 갔고 여기 남길 이유가 없다. `FAILED(UNDER_MIN_AGE)` 는 처음부터 비운다(0-8). `VERIFIED` 미소비 · `EXPIRED` 행은 **30일 지나면 `verified_*` 를 비우는 배치** 1개(`IdentityVerificationPurgeScheduler` · 매일 04:00 · 결제 만료 스케줄러와 같은 자리). `pgRawResponse` 는 **저장하지 않는다** — 원문 JSON 안에 CI · DI 가 평문으로 들어 있다.

### 1-3. 엔티티 · 리포지토리

- `domain.member.user.entity.IdentityVerification` — 위 표. 전이 메서드 `verified(result, now)` · `failed(reason, pgCode, pgMessage, now)` · `consumed(now)` · 판정 `isExpired(now)` · `isOwnedBy(userId)`.
- `domain.member.user.type.IdentityVerificationPurpose` · `IdentityVerificationStatus` · `IdentityFailReason`.
- `IdentityVerificationRepository` — `findLatestVerifiedUnconsumed(userId, purpose, now)`(`status = VERIFIED AND expires_at > now ORDER BY verified_at DESC LIMIT 1`).
- `UserRepository` 에 `findActiveByIdentityDiHash(hash)` — `roleType != GUEST AND status != WITHDRAWN`.
- `Users` — 5열 + `applyIdentity(IdentityVerificationResult, LocalDateTime)`(기존 `updateIdentity` 를 흡수 — CI · DI 까지 함께 쓴다) · `purgeOnWithdrawal` 보강.

---

## 2. 게이트웨이 — `global.payment.portone.identity`

### 2-1. 포트 `PortOneIdentityGateway`

```java
public interface PortOneIdentityGateway {
    /** 포트원 조회 — READY · VERIFIED · FAILED 를 그대로 돌려준다. 없으면 Optional.empty(). */
    Optional<IdentityVerificationResult> find(String identityVerificationId);
}
```

```java
public record IdentityVerificationResult(
        String id, Status status,                    // READY · VERIFIED · FAILED
        VerifiedCustomer customer,                   // VERIFIED 일 때만
        Failure failure,                             // FAILED 일 때만
        String pgTxId, LocalDateTime verifiedAt) {
    public record VerifiedCustomer(String name, String birthDate, String gender, String phoneNumber,
                                   String operator, Boolean foreigner, String ci, String di) {}
    public record Failure(String reason, String pgCode, String pgMessage) {}
}
```

### 2-2. `PortOneIdentityV2Gateway` — SDK `IdentityVerificationClient`

- `@ConditionalOnProperty(portone.enabled=true)`. 생성자에서 `PortOneProperties` 의 `storeId · apiSecret` + **`channelKeys.identity`** 를 `requireText` 한다(결제 게이트웨이와 같은 기동 검증).
- `new IdentityVerificationClient(apiSecret, baseUrl, storeId)` — `PaymentClient` 와 같은 자격. `@PreDestroy close()`.
- `client.getIdentityVerification(id)` 는 `CompletableFuture<IdentityVerification>` — `readTimeoutMillis` 로 `get` 을 끊는다(SDK 내부 60초를 그대로 두지 않는다 — `PortOneV2Gateway` 와 같은 이유).
- 응답 매핑:

| SDK | → |
| --- | --- |
| `VerifiedIdentityVerification` | `VERIFIED` · `verifiedCustomer{name · birthDate · gender(Male/Female/Other → 문자열) · phoneNumber(숫자만) · operator.value · isForeigner · ci · di}` · `verifiedAt` |
| `FailedIdentityVerification` | `FAILED` · `failure{reason · pgCode · pgMessage}` |
| `ReadyIdentityVerification` | `READY` |
| `IdentityVerification.Unrecognized` | `PaymentGatewayException`(결과 모름 — 새 SDK 가 모르는 상태를 줬다) |
| `IdentityVerificationNotFoundException` | `Optional.empty()` — 포트원은 SDK 창이 열리기 전까지 id 를 모른다. 창을 열지 않고 완료를 부른 경우 |
| `UnauthorizedException · ForbiddenException · ChannelNotFoundException` | `PaymentGatewayRejectedException`(설정 오류 — 재시도 무의미 · Sentry) |
| 타임아웃 · 통신 · `UnknownException` | `PaymentGatewayException`(502 `PAYMENT_GATEWAY_ERROR` — 메시지는 「인증사 통신」으로 별도 코드 `IDENTITY_GATEWAY_ERROR`) |

- `ci · di · phoneNumber · name` 은 **로그에 남기지 않는다**. 디버그 로그는 `id · status · pgTxId` 까지.

### 2-3. `FakeIdentityGateway` — `portone.enabled=false`

`FakePaymentGateway` 와 같은 「시나리오 지정 더블」. 기본은 **id 를 등록하면 `VERIFIED`(고정 인물)** 이라 자격 없이 가입이 돈다. 통합 테스트는 `register(id, scenario)` 로 `READY` · `FAILED(pgCode = "CS24")` · `UNDER_14`(생년월일 = 오늘 − 13년) · `DUPLICATE(di)` · `NO_DI` · `TIMEOUT` · `NOT_FOUND` 를 만든다. 기본 인물의 `di` 는 **username 기반 결정값**(`hash("fake-di:" + username)`)이라 같은 사용자가 재인증하면 같은 DI, 다른 사용자면 다른 DI 가 나온다 — 중복 차단 테스트가 시나리오 없이도 자연스럽다. 기존 임시 데이터(`홍길동 · 1998-04-12 · FEMALE · 01000000000`)는 이 더블의 **기본 인물**로 옮긴다 — 기존 테스트가 기대하는 값이 유지된다.

---

## 3. 앱 API — `IdentityVerificationController`(`/v1/user/identity-verifications`)

인증은 **GUEST(registerToken) · USER(accessToken)** 둘 다 부른다. 시작 · 완료는 토큰에서 `username` 을 꺼내 사용자를 특정한다 — registerToken 의 `subject` 가 username 이라 기존 `register()` 와 같은 식으로 읽는다. 이 두 엔드포인트는 `SecurityConfig` 에서 **registerToken 을 허용**하는 경로로 등록한다(지금 registerToken 은 `/social/signup` 만 통과 — 확인 필요 · 13절 #5).

### 3-1. 시작 `POST /v1/user/identity-verifications`

요청: `{ "purpose": "SIGNUP" | "REVERIFY" }`

| 검증 | 응답 |
| --- | --- |
| `SIGNUP` 인데 `roleType != GUEST` | 400 `ALREADY_REGISTERED` |
| `REVERIFY` 인데 `roleType == GUEST` 또는 `WITHDRAWN` | 404 `USER_NOT_FOUND` / `USER_WITHDRAWN` |
| 같은 사용자 · 같은 purpose 의 `READY` 행이 **1분 안**에 있으면 | 그 행을 **그대로 돌려준다**(연타 멱등 — 새 id 를 찍지 않는다) |

응답 201:

```json
{ "identityVerificationId": "idv01HXYZ3K8M2Q7R9T1V4W6Y8Z", "storeId": "store-…", "channelKey": "channel-key-…",
  "expiresAt": "2026-10-24T10:10:00" }
```

앱은 이 세 값으로 RN SDK `<IdentityVerification request={{ storeId, identityVerificationId, channelKey }} onComplete onError />` 를 띄운다(R-7 — `redirectUrl` 은 SDK 가 `portone://blank` 로 덮어쓰므로 넣지 않는다 · `customer` · `bypass` 도 넣지 않는다 — 0-5 `web_siteid`). 10분 지나면 `expiresAt` 기준으로 앱이 1e 를 띄우고 다시 시작한다.

### 3-2. 완료 `POST /v1/user/identity-verifications/{id}/complete`

본문(선택): `{ "identityVerificationTxId": "…", "code": "…", "pgCode": "…" }` — SDK `onComplete` 가 준 값을 **기록용**으로만 받는다(1-2). 판정에는 쓰지 않는다(0-1). 순서:

1. 행 조회 — 없거나 `user_id` 가 호출자와 다르면 404 `IDENTITY_VERIFICATION_NOT_FOUND`(남의 id 를 찔러도 존재 여부를 흘리지 않는다).
2. `CONSUMED` → 409 `IDENTITY_VERIFICATION_CONSUMED`. `FAILED` → 422 `IDENTITY_VERIFICATION_FAILED`(저장된 사유 그대로 — 포트원을 다시 묻지 않는다). `VERIFIED` → **200 멱등**(아래 응답).
3. `isExpired(now)` → 행을 `FAILED(NOT_COMPLETED)` 로 닫고 409 `IDENTITY_VERIFICATION_EXPIRED`.
4. `gateway.find(id)`:
   - `empty` / `READY` → 409 `IDENTITY_VERIFICATION_NOT_COMPLETED`(행은 `READY` 유지 — 앱이 다시 부를 수 있다).
   - `FAILED` → 행 `FAILED(PG_FAILED, pgCode, pgMessage)` · 422 `IDENTITY_VERIFICATION_FAILED`.
   - `VERIFIED` → 5 로.
5. **14세 판정** — `birthDate` 기준 만 14세 미만이면 행 `FAILED(UNDER_MIN_AGE)` **개인정보 없이** · 403 `UNDER_MIN_AGE`. `purpose = REVERIFY` 도 같다(이미 회원인 14세 미만은 있을 수 없지만 규칙은 하나).
6. **DI 확인** — `di` 가 비면 행 `FAILED(DI_MISSING)` **개인정보 없이** · 422 `IDENTITY_VERIFICATION_FAILED` · Sentry(KCP 는 항상 제공 — 설정 오류다 · 0-5).
7. **중복 판정**(`duplicate-block=true` · 기본) — `findActiveByIdentityDiHash(hmac(di))` 가 **다른 user** 를 돌려주면 행 `FAILED(DUPLICATE_IDENTITY)` **개인정보 없이**(남의 정보를 이 사용자 행에 남기지 않는다) · 409 `IDENTITY_ALREADY_REGISTERED`. `REVERIFY` 에서 **같은 user** 면 정상(같은 사람 재인증). `REVERIFY` 에서 DI 가 기존 `identity_di_hash` 와 **다르면** = 타인 명의 재인증 — C15-2 남은 결정 ① 이 미정이라 **지금은 허용하고 WARN 로그 + Sentry breadcrumb**(13절 #7).
8. 행 `verified(result)` — `verified_*` · `*_enc`(cipher 로 암호화) 채움. 응답 200:

```json
{ "status": "VERIFIED", "purpose": "SIGNUP", "maskedName": "홍*동", "maskedPhone": "010-****-0000", "verifiedAt": "…" }
```

마스킹은 `MaskingUtils` 그대로. 앱 1b → 1c 전환에 「홍*동 님, 인증되었어요」 정도만 쓸 수 있게 — 원문은 내리지 않는다.

### 3-3. 회원가입 `POST /v1/user/auth/social/signup` — 바뀌는 것

기존 3단계 「`identityVerificationService.verify(username)`」를 **`identityVerificationService.consumeForSignup(user, now)`** 로 바꾼다:

- `findLatestVerifiedUnconsumed(userId, SIGNUP, now)` 없음 → **400 `IDENTITY_VERIFICATION_REQUIRED`**("본인인증을 먼저 완료해 주세요").
- 있음 → `user.applyIdentity(…)` · 행 `consumed(now)` · `verified_*` 비움. 같은 트랜잭션. `identity_di_hash` UNIQUE 충돌(`DataIntegrityViolationException`) → 409 `IDENTITY_ALREADY_REGISTERED`(1-1 경쟁 방어).
- 14세 판정은 **완료 API 로 옮겨졌으므로 여기서 다시 하지 않는다**(결과가 `VERIFIED` 면 이미 통과). `UNDER_MIN_AGE` 분기 삭제.
- Swagger 문구 「PASS 연동 전까지는 서버가 임시 인증 데이터를 사용합니다」 삭제 · 「`POST /identity-verifications/{id}/complete` 가 선행돼야 한다 · 400 `IDENTITY_VERIFICATION_REQUIRED`」 추가.

### 3-4. 재인증 `POST /v1/user/settings/account/verifications` — 바뀌는 것

`SettingService.reverifyIdentity` 의 `verify(username)` → `consumeForReverify(user, now)`. 동의 검사 · 이력 저장은 그 **앞**에 그대로. 없으면 400 `IDENTITY_VERIFICATION_REQUIRED`. 응답 `AccountInfoResponse` 그대로.

### 3-5. 시안 ↔ API

| 시안 | API |
| --- | --- |
| 1b [PASS로 인증하기] | 시작 → SDK 창 → 완료 |
| 1b 대기 모달 「남은 시간 2:47」 | 앱 로컬 타이머(KCP 창 자체 제한) — `expiresAt`(10분) 은 서버 상한 |
| 1b 이탈 모달 [나가기] | 아무 API 없음 — 행은 `EXPIRED` 로 읽힌다 |
| 1d 14세 차단 | 완료 403 `UNDER_MIN_AGE` |
| 1e 실패 · 타임아웃 [다시 인증하기] | 완료 409/422 → 시작을 **다시** |
| 1e [문자로 인증하기] | 서버 API 없음 — 시작을 다시 부르고 SDK 창에서 고른다. KCP 창 안에 SMS 갈래가 있는지 확인 전(13절 #4) |
| 1c 회원가입 | `/social/signup`(기존) |
| C15-2 [PASS로 다시 인증하기] | 시작(`REVERIFY`) → SDK → 완료 → `PATCH …/identity` |

---

## 4. `IdentityVerificationService` — 재작성

패키지는 그대로(`api.app.auth.service`). 메서드:

| 메서드 | 하는 일 |
| --- | --- |
| `start(username, purpose, now)` | 3-1. id 발급(`idv` + ULID · R-4 제약 검증 `^[A-Za-z0-9]{1,40}$` 단위 테스트) · `READY` 행 · 응답 |
| `complete(username, id, now)` | 3-2. 조회 · 판정 · `VERIFIED` 저장 |
| `consumeForSignup(user, now)` · `consumeForReverify(user, now)` | 3-3 · 3-4. 행 → `Users` |
| `isUnderMinAge(birthDate, now)` | 기존 판정식 — `MIN_AGE = 14` 유지 |

`IdentityVerification`(기존 내부 클래스) 은 삭제 — `IdentityVerificationResult.VerifiedCustomer` 가 대신한다. 호출부 2곳(`AuthController` · `SettingService`) 만 바뀐다.

DI 해시: `PersonalDataCipher` 에 `hmac(String)` 1개 추가(HMAC-SHA256 · 키 = 같은 32바이트 키에서 `"di-hash"` 라벨로 HKDF 파생 — 암호화 키를 그대로 MAC 키로 쓰지 않는다). 키를 바꾸면 해시도 다시 계산해야 한다 — cipher 주석의 「키를 바꾸면 기존 행을 다시 암호화」와 같은 제약.

---

## 5. 탈퇴 · 재가입 · 정지

| 지점 | 처리 |
| --- | --- |
| `UserService.withdrawUser` → `purgeOnWithdrawal` | `identity_ci_enc · identity_di_enc · identity_di_hash · identity_operator · identity_foreigner` null. **`identity_verification` 행의 `verified_*` 도 비운다**(`purgeByUser`) — 탈퇴 즉시 |
| 재가입(51-6 6절 · `GUEST` 복귀) | 가입 흐름 그대로 → **다시 인증**. 정지 복귀(`restoreOnRejoin`)는 그대로 |
| 정지(`SUSPENDED`) 중 재인증 | 허용 — 51-6 4-2 「허용 범위」에 회원정보는 없지만 **본인 정보 갱신은 새로 벌이는 일이 아니다**. 가드 안 둠 |
| 중복 차단이 탈퇴 회원을 보는가 | 안 본다(`status != WITHDRAWN`). 탈퇴 → 다른 소셜로 가입은 **새 계정**이다(정지 복귀는 username 기준이라 이걸로 우회되는 문제가 있다 — 13절 #3) |

---

## 6. 설정 · 보안

### 6-1. `application.yml`

```yaml
portone:
  channel-keys:
    identity: ${PORTONE_CHANNEL_IDENTITY:}        # KCP 휴대폰 본인확인 채널 — 결제 채널과 다른 키
  identity:
    ttl-minutes: 10                                # 시작 → 완료 상한
    duplicate-block: ${IDENTITY_DUPLICATE_BLOCK:true}    # DI 중복 가입 차단(확정) — 장애 때만 false · UNIQUE 는 남는다(1-1)
```

- `PortOneProperties.ChannelKeys` 에 `identity` 추가 · `Identity { ttlMinutes · duplicateBlock }` 추가.
- `PortOneV2Gateway` 의 기동 검증은 **건드리지 않는다**(결제는 `identity` 키 없이도 떠야 한다). `PortOneIdentityV2Gateway` 만 `identity` 키를 요구한다 — 운영에서 결제는 켰는데 본인인증 채널이 아직이면 **`portone.identity.enabled=false`** 로 Fake 를 붙일 수 있게 플래그 하나 더 둔다(`FakeIdentityGateway` 조건 = `portone.enabled=false OR portone.identity.enabled=false`). 런칭 전 채널 계약 지연에 대비.

### 6-2. 보안

- `SecurityConfig` — `/v1/user/identity-verifications/**` 는 **registerToken 또는 accessToken** 허용(13절 #5). 그 외 변경 없음.
- CI · DI · 전화 · 이름은 로그 · 예외 메시지 · Sentry 에 싣지 않는다(`PersonalDataCipher` 주석과 같은 원칙). Sentry 에는 `identityVerificationId · status · pgCode` 만.
- 완료 API 는 **사용자당 분당 10회** 제한(기존 레이트 리미터가 있으면 그것 · 없으면 13절 #8). 포트원 조회 비용 · 연속 실패 잠금(C0 남은 결정) 의 최소 방어.
- 어드민 소비자 상세의 마스킹 해제(`user_privacy_access_log`)는 **CI · DI 를 열지 않는다** — `fields` 에 추가하지 않는다. 운영자가 볼 값이 아니다.

---

## 7. API · 에러 코드

### 7-1. 앱 — `IdentityVerificationController`(`/v1/user/identity-verifications` · GUEST+USER) · docs `IdentityVerificationControllerDocs`

| 메서드 | 경로 | 응답 |
| --- | --- | --- |
| POST | `/` | 201 시작 응답(3-1) |
| POST | `/{id}/complete` | 200 완료 응답(3-2) |

### 7-2. 바뀌는 기존 API

| 경로 | 변경 |
| --- | --- |
| `POST /v1/user/auth/social/signup` | 400 `IDENTITY_VERIFICATION_REQUIRED` 추가 · 403 `UNDER_MIN_AGE` **제거**(완료 API 로 이동) · Swagger 문구 |
| `POST /v1/user/settings/account/verifications` | 400 `IDENTITY_VERIFICATION_REQUIRED` 추가 |

### 7-3. 에러 코드(신규 · `ErrorCode`)

| 코드 | HTTP | 메시지 |
| --- | --- | --- |
| `IDENTITY_VERIFICATION_REQUIRED` | 400 | 본인인증을 먼저 완료해 주세요. |
| `IDENTITY_VERIFICATION_NOT_FOUND` | 404 | 본인인증 요청을 찾을 수 없습니다. |
| `IDENTITY_VERIFICATION_NOT_COMPLETED` | 409 | 본인인증이 완료되지 않았습니다. 다시 시도해 주세요. |
| `IDENTITY_VERIFICATION_EXPIRED` | 409 | 본인인증 유효 시간이 지났습니다. 다시 시도해 주세요. |
| `IDENTITY_VERIFICATION_CONSUMED` | 409 | 이미 사용된 본인인증입니다. |
| `IDENTITY_VERIFICATION_FAILED` | 422 | 본인인증에 실패했습니다. 다시 시도해 주세요. |
| `IDENTITY_ALREADY_REGISTERED` | 409 | 이미 가입된 본인인증 정보입니다. |
| `IDENTITY_GATEWAY_ERROR` | 502 | 인증사 통신에 실패했습니다. 잠시 후 다시 시도해 주세요. |

기존 `UNDER_MIN_AGE(403)` · `IDENTITY_CONSENT_REQUIRED(400)` 그대로.

---

## 8. 마이그레이션 — V217 하나

```sql
-- V217__identity_verification.sql — 포트원 KCP 본인인증(57 설계서 1절)
ALTER TABLE `users`
    ADD COLUMN `identity_ci_enc`    VARCHAR(512) NULL COMMENT 'CI 암호문(PersonalDataCipher)',
    ADD COLUMN `identity_di_enc`    VARCHAR(512) NULL COMMENT 'DI 암호문',
    ADD COLUMN `identity_di_hash`   CHAR(64)     NULL COMMENT 'DI HMAC-SHA256 hex — 중복 가입 차단 키(활성 회원 1인 1행)',
    ADD COLUMN `identity_operator`  VARCHAR(10)  NULL COMMENT 'SKT·KT·LGU·*_MVNO',
    ADD COLUMN `identity_foreigner` TINYINT(1)   NULL,
    ADD UNIQUE KEY `uk_users_identity_di_hash` (`identity_di_hash`);

CREATE TABLE `identity_verification` (
    `identity_verification_id` VARCHAR(40)  NOT NULL COMMENT 'idv + ULID — KCP: 영문·숫자 40자 이하',
    `identity_verification_tx_id` VARCHAR(64) NULL,
    `user_id`             BIGINT       NOT NULL,
    `purpose`             VARCHAR(10)  NOT NULL COMMENT 'SIGNUP·REVERIFY',
    `status`              VARCHAR(10)  NOT NULL COMMENT 'READY·VERIFIED·FAILED·CONSUMED',
    `channel_key`         VARCHAR(64)  NOT NULL,
    `pg_tx_id`            VARCHAR(64)  NULL,
    `verified_name`       VARCHAR(64)  NULL,
    `verified_birth_date` VARCHAR(10)  NULL,
    `verified_gender`     VARCHAR(10)  NULL,
    `verified_phone`      VARCHAR(20)  NULL,
    `verified_operator`   VARCHAR(10)  NULL,
    `verified_foreigner`  TINYINT(1)   NULL,
    `verified_ci_enc`     VARCHAR(512) NULL,
    `verified_di_enc`     VARCHAR(512) NULL,
    `fail_reason`         VARCHAR(40)  NULL COMMENT 'PG_FAILED·UNDER_MIN_AGE·DUPLICATE_IDENTITY·DI_MISSING·NOT_COMPLETED',
    `pg_code`             VARCHAR(64)  NULL,
    `pg_message`          VARCHAR(255) NULL,
    `requested_at`        DATETIME(6)  NOT NULL,
    `verified_at`         DATETIME(6)  NULL,
    `consumed_at`         DATETIME(6)  NULL,
    `expires_at`          DATETIME(6)  NOT NULL,
    PRIMARY KEY (`identity_verification_id`),
    KEY `idx_idv_user` (`user_id`, `requested_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

기존 회원의 `identity_*` 는 NULL 로 남는다 — 런칭 전이라 백필 대상이 없다(임시 데이터로 가입된 개발 계정은 전부 `홍길동` 이다).

---

## 9. 기획 제외 범위 · 기존 코드와의 접점

- 기획 제외 17개 컨트롤러 · 정산 3 메서드는 건드리지 않는다. `SocialLoginService` 는 제외 패키지 `SocialPolicyService` 를 쓰지만 이 설계는 `SocialLoginService` 를 **바꾸지 않는다**(GUEST 발급 · 재가입 복귀 그대로).
- `AdminCreatorApplicationControllerDocs` · `CreatorApplicationDetailResponse.VERIFICATION_METHOD_PASS_LABEL("휴대폰 본인인증(PASS)")` — KCP 창도 PASS 앱 · 문자 인증을 띄우므로 표기는 **그대로**(13절 #6).
- `AdminPostQueryService` 「실명(본인인증)이 없으면 닉네임을 가린다」 · 어드민 소비자 상세 `identity` 블록 — `Users.name · birthday · phoneNumber` 를 읽는 코드라 변경 없음.
- `PaymentWebhookService` — 모르는 타입 `IGNORED` 그대로(0-10).
- `MaskingUtils` 재사용.

---

## 10. 테스트 — `src/test/java/showroomz/api/app/auth/` · `api/app/setting/`

통합(`IntegrationTest` · `portone.enabled=false` → `FakeIdentityGateway`):

| # | 시나리오 | 기대 |
| --- | --- | --- |
| I-01 | GUEST 시작(SIGNUP) | 201 · `idv` + ULID id(영문 · 숫자 29자) · storeId · channelKey · `READY` 행 |
| I-02 | 1분 안 재시작 | 같은 id |
| I-03 | 완료(Fake 기본 인물) | 200 `VERIFIED` · 마스킹 이름 · 행 `verified_*` 채움 · `*_enc` 는 복호화하면 Fake 의 ci/di |
| I-04 | 완료 뒤 `/social/signup` | 201 · `Users.name/birthday/gender/phone/identityVerifiedAt/identity_di_hash` 채움 · 행 `CONSUMED` · `verified_*` null |
| I-05 | 인증 없이 `/social/signup` | 400 `IDENTITY_VERIFICATION_REQUIRED` · Users 변경 없음 |
| I-06 | Fake `UNDER_14` | 403 `UNDER_MIN_AGE` · 행 `FAILED(UNDER_MIN_AGE)` · 개인정보 열 전부 null |
| I-07 | Fake `FAILED(pgCode)` | 422 · 행 `FAILED(PG_FAILED)` · pg_code 보존 |
| I-08 | Fake `READY` | 409 `NOT_COMPLETED` · 행 `READY` 유지 · 다시 부르면 또 409 |
| I-09 | `expires_at` 지난 행 완료 | 409 `EXPIRED` · 행 `FAILED(NOT_COMPLETED)` |
| I-10 | 남의 id 완료 | 404 |
| I-11 | `CONSUMED` 행 완료 | 409 `CONSUMED` |
| I-12 | `VERIFIED` 행 완료 2회 | 200 · 200 같은 응답 |
| I-13 | 다른 활성 회원(USER)과 같은 DI | 409 `IDENTITY_ALREADY_REGISTERED` · 행 `FAILED(DUPLICATE_IDENTITY)` · `verified_*` 전부 null |
| I-13b | 다른 **정지(SUSPENDED)** 회원과 같은 DI | 409(정지도 활성) |
| I-14 | 탈퇴 회원과 같은 DI | 200(탈퇴는 해시가 비어 있다) |
| I-15 | `duplicate-block=false` · 같은 DI · 완료 → `/social/signup` | 완료 200 · 가입은 UNIQUE 충돌 → 409 `IDENTITY_ALREADY_REGISTERED`(1-1 2차 방어) |
| I-15b | Fake `NO_DI` | 422 `IDENTITY_VERIFICATION_FAILED` · 행 `FAILED(DI_MISSING)` · 개인정보 null |
| I-15c | 완료 본문에 `identityVerificationTxId · code · pgCode` 실어 보냄 | 행에 기록 · 판정에는 영향 없음(`VERIFIED` 면 200) |
| I-16 | USER 재인증 시작(REVERIFY) → 완료 → `PATCH …/identity` | 200 · 값 갱신 · `IDENTITY_VERIFICATION` 이력 1건 · 행 `CONSUMED` |
| I-17 | SIGNUP 행으로 `PATCH …/identity` | 400 `IDENTITY_VERIFICATION_REQUIRED`(purpose 불일치) |
| I-18 | 재인증 DI 가 기존과 다름 | 200(허용) · WARN 로그 1건 |
| I-19 | 탈퇴 | `users.identity_*` null · 그 사용자의 `identity_verification.verified_*` null |
| I-20 | Fake `TIMEOUT` | 502 `IDENTITY_GATEWAY_ERROR` · 행 `READY` 유지 |
| I-21 | registerToken 만료 후 10분 안 인증 행 | 소셜 로그인 다시 → 새 registerToken → `/social/signup` 201(인증 재요구 없음) |

단위:

- `PortOneIdentityV2GatewayTest` — SDK 응답 3종 + `Unrecognized` + 예외 매핑(결제 게이트웨이 테스트와 같은 방식 · 실호출 없음).
- `IdentityVerificationServiceTest` — 14세 경계(오늘이 14번째 생일 = 통과 · 하루 전 = 차단 · 파싱 불가 = 통과) · 만료 판정 · DI 해시 결정성 · **id 형식 `^[A-Za-z0-9]{1,40}$`**(R-4) 1만 회 생성 전부 통과 · 중복 없음.
- `PersonalDataCipherTest` — `hmac` 결정성 · 키 다르면 다름.
- 기존 `SettingServiceTest` 재인증 3건 — `identityVerificationService` 모킹 대상이 `consumeForReverify` 로 바뀐다.

---

## 11. 앱 전달 요지(RN)

1. SDK `@portone/react-native-sdk` 의 **`<IdentityVerification request onComplete onError />`** — `Payment` 와 같은 구조(R-7). `request = { storeId, identityVerificationId, channelKey }` 를 **시작 API 응답 그대로** 넘긴다. 앱에 키를 박지 않는다. `redirectUrl` 은 SDK 가 `portone://blank` 로 덮어쓰니 넣지 않는다 · `customer` · `bypass` 도 넣지 않는다.
2. `onComplete` 의 인자는 **쿼리 문자열을 객체로 만든 것**(값 전부 문자열) — `{ transactionType, identityVerificationId, identityVerificationTxId, code?, message?, pgCode?, pgMessage? }`. **`code` 가 있으면 실패**지만 성공 · 실패 가리지 말고 **완료 API** 를 부른다(본문에 `identityVerificationTxId · code · pgCode` 를 실어 주면 서버가 기록한다 — 선택). 판정은 완료 API 응답(200 / 403 / 409 / 422)으로 한다. `onError` 는 창이 열리기 **전** 실패(파라미터 오류 등)에만 온다 — 이때도 완료 API 를 부르면 409 `NOT_COMPLETED` 라 1e 로 가면 된다.
3. 403 `UNDER_MIN_AGE` → 1d. 409 · 422 · 502 → 1e(「다시 인증하기」 = **시작 API 부터**). 200 → 1c(가입) 또는 C15-2 복귀.
4. 가입 완료 `POST /social/signup` 은 바뀌지 않는다 — 단 **완료 API 뒤에만** 성공한다(400 `IDENTITY_VERIFICATION_REQUIRED`).
5. 시작 응답 `expiresAt`(10분) 을 넘기면 완료를 부르지 말고 1e 로.

---

## 12. 구현 순서

1. V217 · 엔티티 · enum · 리포지토리 · `Users` 5열 + `applyIdentity` + `purgeOnWithdrawal`.
2. `PersonalDataCipher.hmac`.
3. `PortOneIdentityGateway` · `IdentityVerificationResult` · `FakeIdentityGateway`(Fake 먼저 — 전 구간이 돈다) · `PortOneProperties` 확장.
4. `IdentityVerificationService` 재작성 · `IdentityVerificationController` · docs · 에러 코드 · `SecurityConfig`.
5. `AuthController.register` · `SettingService.reverifyIdentity` 결합 · Swagger 문구.
6. 통합 테스트 I-01 ~ I-21 · 단위.
7. `PortOneIdentityV2Gateway`(SDK) · 단위 테스트 — 채널 키가 나오면 테스트 채널로 1회 실호출 확인.
8. 퍼지 스케줄러(1-2) · 탈퇴 퍼지.

관련 테스트만 돈다: `./gradlew test --tests '*IdentityVerification*' --tests '*SettingServiceTest' --tests '*PersonalDataCipherTest'`

---

## 13. 확인 필요 · 미결

| # | 항목 | 지금 설계 | 누가 |
| --- | --- | --- | --- |
| 1 | **포트원 콘솔 KCP 본인인증 채널 개설** + KCP 관리자 페이지 ① 인증결과URL `checkout-service.prod.iamport.co` ② **신규 연동방식(V2) 사용 = 사용**(안 켜면 `CS24`) ③ **CI 제공 설정 ON**(R-3 「가맹점 설정에 따름」) · 테스트 채널 키 | 채널 키 없이는 Fake 로만 돈다 | 운영 · 포트원/KCP 계약 |
| 2 | KCP 테스트 채널 1회 실호출로 `ci · di · phoneNumber · operator · isForeigner` 수신 확인 — 문서상 `di · phone · operator · isForeigner` 는 항상 · `ci` 는 설정(R-3). 특히 **CI 가 오는지** | `ci` 만 nullable 취급(비면 `Users.identity_ci_enc` null + WARN) · `di` 는 비면 실패(0-5) | 7단계 때 개발 |
| 3 | ~~DI 중복 가입 차단을 켤 것인가~~ → **차단 확정**(2026-10-24). 남은 것: **탈퇴 → 다른 소셜로 재가입** 은 해시를 비우므로 뚫린다 — 탈퇴 회원의 DI 해시를 (정지 중 탈퇴에 한해) 보존할지는 개인정보 파기 의무와 충돌하므로 법무 | 탈퇴 시 해시 파기 · 재가입은 새 계정 | 법무 · 기획 |
| 4 | 1e [문자로 인증하기] — KCP **SDK 창** 안에 SMS · APP 갈래가 있는지(KCP 페이지는 API 방식의 `method ∈ SMS·APP` 만 적고 SDK 창 UI 는 안 적었다 — R-5). 없으면 앱이 버튼을 뺀다. 서버가 API 방식(`send/confirm`)으로 SMS 를 직접 제공하는 안은 **채택하지 않음**(0-9) | 서버 API 없음 | 7단계 때 테스트 채널로 확인 · 앱 |
| 4b | `windowType` — KCP 의 모바일 기본값이 `REDIRECTION` 인지(RN 은 `portone://blank` 리다이렉트 전제 — R-7). 결제와 같은 SDK 경로라 될 것으로 보지만 문서에 KCP 기본값이 없다 | 지정하지 않음(PG 기본) | 7단계 때 확인 |
| 5 | `SecurityConfig` 에서 registerToken 이 `/social/signup` 외 경로를 통과하는 방식 — 현재 `HeaderUtil.getAccessToken` + `tokenProvider.convertAuthToken` 을 컨트롤러가 직접 하므로 필터가 GUEST 를 어떻게 다루는지 코드 확인 뒤 결정 | `/v1/user/identity-verifications/**` 화이트리스트 + 컨트롤러가 토큰을 직접 검증(기존 `register()` 와 동일) | 4단계 때 개발 |
| 6 | 어드민 표기 「휴대폰 본인인증(PASS)」 — KCP 창도 PASS/문자라 유지했다. 「KCP」 를 노출할지 | 유지 | 기획 |
| 7 | 재인증 결과가 **다른 명의**(C15-2 남은 결정 ①) | 허용 + WARN | 기획 · 법무 |
| 8 | 완료 API 레이트 리밋 — 기존 리미터 유무 | 사용자당 분당 10회 | 개발 |
| 9 | `identity_verification` 보존 기간(미소비 · 실패 행의 `verified_*` 30일 파기 / 메타는 영구) | 30일 | 법무 |
| 10 | 재인증 주기(C0 남은 결정 ①) — `identityVerifiedAt` 이 N년 지나면 주문 전 재인증을 요구할지 | 범위 밖 · 열은 이미 있다 | 기획 |
| 11 | KCP `web_siteid` — DI 생성 키(R-5). 기본(KCP 발급 사이트 ID)을 쓰고 **절대 바꾸지 않는다**는 것을 운영 런북 · 계약 담당에게 공유. 바뀌면 전 회원 DI 가 달라져 중복 차단이 깨지고 CI 로 재대조해야 한다 | `bypass` 미지정 | 운영 |
| 12 | 포트원 `MaxTransactionCountReached` 에러(R-1) 의 뜻 — 같은 id 재시도 횟수 제한인지. 우리는 시도마다 새 id 라 안 걸릴 것으로 보지만 확인 | 새 id 정책 유지 | 7단계 때 확인 |

---

## 14. 구현 메모(2026-10-24) — 설계에서 달라진 것

| # | 항목 | 설계 | 구현 | 이유 |
| --- | --- | --- | --- | --- |
| 1 | 재인증 API 경로 | `PATCH /v1/user/settings/account/identity` 로 적었다 | 기존 **`POST /v1/user/settings/account/verifications`**(`SettingController#reverifyIdentity`) 그대로 | 코드 확인 — 경로를 바꾸지 않았다. 본문도 수정 |
| 2 | 완료 API 트랜잭션 | 「행을 FAILED 로 닫고 4xx」 | `@Transactional(noRollbackFor = BusinessException.class)` | 거절 예외가 롤백을 일으켜 FAILED · 사유가 사라졌다(첫 테스트에서 5건 실패) — 실패 기록이 조사 자료라 커밋해야 한다 |
| 3 | id 발급 | 「`idv` + ULID」 | 라이브러리 없이 `IdentityVerificationIdGenerator`(시각 48비트 + 난수 80비트 · Crockford base32) | ULID 의존성이 없었다. 1만 회 생성 · 정규식 · 중복 없음 단위 테스트 |
| 4 | Fake 기본 인물의 DI | 「username 기반 결정값」 | 서비스가 `start` 때 `fake.bind(id, username)` 으로 알려 준다(`instanceof FakeIdentityGateway`) | 게이트웨이는 id 만 받으므로 사용자를 알 길이 없다. 운영 구현에는 영향 없음 |
| 5 | 시작 API 검증 | SIGNUP 만 적었다 | `REVERIFY` 는 `WITHDRAWN → 403 USER_WITHDRAWN` · `GUEST → 404 USER_NOT_FOUND` 추가 | 3-1 표에 있던 것을 코드로 |
| 6 | SDK 생성자 | — | `VerifiedIdentityVerification` 의 `pgTxId` · `pgRawResponse` 는 **non-null**(Kotlin) | 단위 테스트 픽스처에서 NPE — 응답에 항상 온다는 뜻이라 매핑은 그대로 |
| 7 | 테스트 I-15(킬 스위치 OFF + UNIQUE) | 통합 테스트로 적었다 | **생략** — 대신 I-13 · I-13b · I-14 로 서비스 판정, `Users.identityDiHash(unique = true)` 로 제약 | 테스트 컨텍스트에서 프로퍼티만 바꾸면 컨텍스트가 갈라진다. UNIQUE 는 H2 `create-drop` 이 만든다 |
| 8 | 회원가입 Swagger | 403 `UNDER_MIN_AGE` 제거 | 403 블록을 **409 `IDENTITY_ALREADY_REGISTERED`**(UNIQUE 경쟁) 로 바꾸고 400 설명에 `IDENTITY_VERIFICATION_REQUIRED` 추가 | 가입이 낼 수 있는 코드로 정정 |
| 9 | 파기 배치 플래그 | 적지 않았다 | `portone.identity.purge-scheduler-enabled`(기본 true) · 매일 04:00 · 500건 | 다른 배치와 같은 끄기 방식 |
| 10 | 완료 API 본문 | 「선택 · 기록용」 | `@RequestBody(required = false)` · `recordClientResult` 는 `pgCode` 가 비어 있을 때만 채운다 | 포트원 조회 결과의 `pgCode` 를 앱 값이 덮어쓰지 않게 |
| 11 | `UserService.withdrawUser` | `purgeByUser` | `IdentityVerificationRepository.purgePersonalDataByUser`(bulk UPDATE) | 행 수 상관없이 1 쿼리 |

변경 파일 — 신규: `V217__identity_verification.sql` · `domain.member.user.{entity.IdentityVerification, repository.IdentityVerificationRepository, type.IdentityVerificationPurpose · IdentityVerificationStatus · IdentityFailReason}` · `global.payment.portone.identity.{PortOneIdentityGateway, PortOneIdentityV2Gateway, FakeIdentityGateway, IdentityVerificationResult}` · `api.app.auth.{controller.IdentityVerificationController, docs.IdentityVerificationControllerDocs, service.IdentityVerificationIdGenerator, DTO.IdentityVerification{Start,Complete}{Request,Response}}` · `global.scheduler.IdentityVerificationPurgeScheduler`. 수정: `Users`(5열 · `applyIdentity` · `purgeOnWithdrawal`) · `UserRepository.findActiveByIdentityDiHash` · `IdentityVerificationService`(재작성) · `AuthController.register` · `SettingService.reverifyIdentity` · `UserService.withdrawUser` · `PersonalDataCipher.hmac` · `PortOneProperties` · `ErrorCode`(8종) · `SecurityConfig` · `application.yml` · Swagger 2곳.
