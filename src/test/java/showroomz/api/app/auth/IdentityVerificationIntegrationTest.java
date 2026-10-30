package showroomz.api.app.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.token.AuthTokenProvider;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.IdentityVerification;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.IdentityVerificationRepository;
import showroomz.domain.member.user.type.IdentityFailReason;
import showroomz.domain.member.user.type.IdentityVerificationStatus;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.global.payment.portone.identity.FakeIdentityGateway;
import showroomz.global.payment.portone.identity.FakeIdentityGateway.Scenario;
import showroomz.global.scheduler.IdentityVerificationPurgeScheduler;
import showroomz.global.utils.PersonalDataCipher;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [통합] 포트원 KCP 본인인증 — 시작 → 완료 → 가입 / 재인증 / 탈퇴 · 재가입 / 파기(57 테스트 시나리오 AU ~ E2E 절).
 *
 * <p>포트원은 {@link FakeIdentityGateway}(시나리오 더블)다. 지키는 것 — ① 가입 · 재인증은 완료 API 뒤에만 성공한다 ② 14세 미만 ·
 * 중복 · DI 누락 · 만료로 닫힌 행에는 개인정보가 <b>한 글자도</b> 남지 않는다 ③ 같은 DI 의 다른 활성 회원이 있으면 409 — 완료에서 한 번,
 * 동시 가입은 UNIQUE 로 한 번 더 ④ 응답 본문에 원문(실명 · 생년월일 · 전화 · CI · DI)이 없다 ⑤ 닫힌 행을 다시 불러도 처음과 같은 코드다.
 */
@DisplayName("[57 통합] 포트원 KCP 본인인증")
class IdentityVerificationIntegrationTest extends IntegrationTestSupport {

    private static final String START_PATH = "/v1/user/identity-verifications";
    private static final String COMPLETE_PATH = "/v1/user/identity-verifications/%s/complete";
    private static final String SIGNUP_PATH = "/v1/user/auth/social/signup";
    private static final String REVERIFY_PATH = "/v1/user/settings/account/verifications";
    private static final String ACCOUNT_PATH = "/v1/user/settings/account";
    private static final String WITHDRAW_PATH = "/v1/user/auth/withdraw";

    private static final AtomicInteger NICKNAME_SEQ = new AtomicInteger();

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private IdentityVerificationRepository verificationRepository;
    @Autowired
    private FakeIdentityGateway fakeGateway;
    @Autowired
    private PersonalDataCipher cipher;
    @Autowired
    private AuthTokenProvider tokenProvider;
    @Autowired
    private IdentityVerificationPurgeScheduler purgeScheduler;
    @Autowired
    private JdbcTemplate jdbc;

    private Users guest;
    private String registerToken;

    @BeforeEach
    void setUp() {
        fakeGateway.reset();
        guest = createUser("kim", "김게스트", RoleType.GUEST);
        registerToken = registerToken(guest.getUsername());
    }

    // ================================================================== AU 토큰 · 권한

    @Nested
    @DisplayName("AU 토큰 · 권한 — 화이트리스트 경로라 컨트롤러가 직접 검증한다")
    class Auth {

        @Test
        @DisplayName("AU-01 토큰 없음 — 시작 · 완료 모두 401 UNAUTHORIZED")
        void noToken() throws Exception {
            mockMvc.perform(post(START_PATH).contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("purpose", "SIGNUP"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            mockMvc.perform(post(COMPLETE_PATH.formatted("idvANY")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AU-02 만료된 registerToken — 401 · 행이 생기지 않는다")
        void expiredRegisterToken() throws Exception {
            String expired = "Bearer " + tokenProvider.createAuthToken("kim", new Date(System.currentTimeMillis() - 1_000)).getToken();

            mockMvc.perform(post(START_PATH).header(HttpHeaders.AUTHORIZATION, expired)
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("purpose", "SIGNUP"))))
                    .andExpect(status().isUnauthorized());
            assertThat(verificationRepository.count()).isZero();
        }

        @Test
        @DisplayName("AU-03 서명이 틀린 토큰 · Bearer 접두 없음 — 401")
        void malformedToken() throws Exception {
            mockMvc.perform(post(START_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt")
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("purpose", "SIGNUP"))))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post(START_PATH).header(HttpHeaders.AUTHORIZATION, registerToken.substring("Bearer ".length()))
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("purpose", "SIGNUP"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AU-04 소비자가 아닌 토큰(브랜드 셀러 — subject 가 users 에 없다) — 404 USER_NOT_FOUND")
        void nonConsumerToken() throws Exception {
            String sellerToken = bearerToken("brand@showroomz.test", RoleType.SELLER, 999L);

            mockMvc.perform(post(START_PATH).header(HttpHeaders.AUTHORIZATION, sellerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("purpose", "REVERIFY"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }

        @Test
        @DisplayName("AU-05 registerToken 으로 시작한 행을 같은 사람의 accessToken 으로 완료해도 된다 — 주인은 토큰이 아니라 username")
        void ownerIsUsernameNotToken() throws Exception {
            String id = start(registerToken, "SIGNUP");

            complete(bearerToken("kim", RoleType.USER, guest.getId()), id).andExpect(status().isOk());
        }
    }

    // ================================================================== ST 시작

    @Nested
    @DisplayName("ST 시작 (3-1)")
    class Start {

        @Test
        @DisplayName("ST-01 GUEST 시작 — 201 · idv+ULID(영문·숫자 29자) · storeId · channelKey · expiresAt · READY 행")
        void startIssuesIdAndKeys() throws Exception {
            String body = mockMvc.perform(post(START_PATH).header(HttpHeaders.AUTHORIZATION, registerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("purpose", "SIGNUP"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.storeId").value(FakeIdentityGateway.STORE_ID))
                    .andExpect(jsonPath("$.channelKey").value(FakeIdentityGateway.CHANNEL_KEY))
                    .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();
            String id = JsonPath.read(body, "$.identityVerificationId");

            assertThat(id).startsWith("idv").hasSize(29).matches("^[A-Za-z0-9]{1,40}$");
            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.READY);
            assertThat(row.getUserId()).isEqualTo(guest.getId());
            assertThat(row.getChannelKey()).isEqualTo(FakeIdentityGateway.CHANNEL_KEY);
            assertThat(row.getExpiresAt()).isBetween(LocalDateTime.now().plusMinutes(9), LocalDateTime.now().plusMinutes(11));
        }

        @Test
        @DisplayName("ST-02 1분 안에 다시 시작하면 같은 id · 행은 하나")
        void startIsIdempotentWithinAMinute() throws Exception {
            String first = start(registerToken, "SIGNUP");
            String second = start(registerToken, "SIGNUP");

            assertThat(second).isEqualTo(first);
            assertThat(verificationRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("ST-03 1분이 지나면 새 id — 앞 행은 READY 로 남아 만료를 기다린다")
        void startAfterAMinuteIssuesNewId() throws Exception {
            String first = start(registerToken, "SIGNUP");
            shiftRequestedAt(first, LocalDateTime.now().minusSeconds(61));

            String second = start(registerToken, "SIGNUP");

            assertThat(second).isNotEqualTo(first);
            assertThat(verificationRepository.findById(first).orElseThrow().getStatus()).isEqualTo(IdentityVerificationStatus.READY);
        }

        @Test
        @DisplayName("ST-04 완료(VERIFIED) 직후 다시 시작하면 1분 안이어도 새 id — 재사용은 READY 만")
        void startAfterVerifiedIssuesNewId() throws Exception {
            String first = start(registerToken, "SIGNUP");
            complete(registerToken, first).andExpect(status().isOk());

            assertThat(start(registerToken, "SIGNUP")).isNotEqualTo(first);
        }

        @Test
        @DisplayName("ST-05 다른 사용자는 각자 id — 1분 안이어도 섞이지 않는다")
        void startIsPerUser() throws Exception {
            Users lee = createUser("lee", "이게스트", RoleType.GUEST);

            assertThat(start(registerToken(lee.getUsername()), "SIGNUP")).isNotEqualTo(start(registerToken, "SIGNUP"));
        }

        @Test
        @DisplayName("ST-06 이미 가입된 사용자가 SIGNUP — 400 ALREADY_REGISTERED")
        void registeredUserCannotStartSignup() throws Exception {
            Users user = createUser("done", "가입자", RoleType.USER);

            startRequest(userToken(user), "SIGNUP")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"));
        }

        @Test
        @DisplayName("ST-07 GUEST 가 REVERIFY — 404 USER_NOT_FOUND")
        void guestCannotStartReverify() throws Exception {
            startRequest(registerToken, "REVERIFY")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }

        @Test
        @DisplayName("ST-08 탈퇴 회원이 REVERIFY — 403 USER_WITHDRAWN")
        void withdrawnCannotStartReverify() throws Exception {
            Users user = createUser("gone", "탈퇴자", RoleType.USER);
            jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE user_id = ?", user.getId());

            startRequest(userToken(user), "REVERIFY")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_WITHDRAWN"));
        }

        @Test
        @DisplayName("ST-09 purpose 누락 · 모르는 값 — 400 INVALID_INPUT · 행 없음")
        void invalidPurpose() throws Exception {
            mockMvc.perform(post(START_PATH).header(HttpHeaders.AUTHORIZATION, registerToken)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            startRequest(registerToken, "LOGIN")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            assertThat(verificationRepository.count()).isZero();
        }

        @Test
        @DisplayName("ST-10 시작 응답에는 개인정보가 없다 — id · 상점 · 채널 · 만료 시각 4개뿐")
        void startResponseHasOnlyFourFields() throws Exception {
            String body = startRequest(registerToken, "SIGNUP").andReturn().getResponse().getContentAsString();

            Map<String, Object> fields = JsonPath.read(body, "$");
            assertThat(fields).containsOnlyKeys("identityVerificationId", "storeId", "channelKey", "expiresAt");
        }
    }

    // ================================================================== CP 완료

    @Nested
    @DisplayName("CP 완료 (3-2)")
    class Complete {

        @Test
        @DisplayName("CP-01 완료 — 200 VERIFIED · 마스킹 이름 · 전화 · 행에 개인정보와 CI · DI 암호문 · pgTxId")
        void completeStoresVerifiedResult() throws Exception {
            String id = start(registerToken, "SIGNUP");

            complete(registerToken, id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("VERIFIED"))
                    .andExpect(jsonPath("$.purpose").value("SIGNUP"))
                    .andExpect(jsonPath("$.maskedName").value("홍길*"))
                    .andExpect(jsonPath("$.maskedPhone").value("010-****-0000"))
                    .andExpect(jsonPath("$.verifiedAt").isNotEmpty());

            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
            assertThat(row.getVerifiedName()).isEqualTo(FakeIdentityGateway.DEFAULT_NAME);
            assertThat(row.getVerifiedBirthDate()).isEqualTo(FakeIdentityGateway.DEFAULT_BIRTH_DATE);
            assertThat(row.getVerifiedPhone()).isEqualTo(FakeIdentityGateway.DEFAULT_PHONE);
            assertThat(row.getVerifiedOperator()).isEqualTo(FakeIdentityGateway.DEFAULT_OPERATOR);
            assertThat(row.getVerifiedCiEnc()).isNotEqualTo(FakeIdentityGateway.defaultCiOf("kim"));
            assertThat(cipher.decrypt(row.getVerifiedDiEnc())).isEqualTo(FakeIdentityGateway.defaultDiOf("kim"));
            assertThat(cipher.decrypt(row.getVerifiedCiEnc())).isEqualTo(FakeIdentityGateway.defaultCiOf("kim"));
            assertThat(row.getPgTxId()).isEqualTo("pg-" + id);
        }

        @Test
        @DisplayName("CP-02 완료 응답 본문에 원문이 없다 — 실명 · 생년월일 · 전화 · CI · DI 를 문자열로 검색")
        void completeResponseLeaksNothing() throws Exception {
            String id = start(registerToken, "SIGNUP");

            complete(registerToken, id)
                    .andExpect(content().string(not(containsString(FakeIdentityGateway.DEFAULT_NAME))))
                    .andExpect(content().string(not(containsString(FakeIdentityGateway.DEFAULT_PHONE))))
                    .andExpect(content().string(not(containsString(FakeIdentityGateway.DEFAULT_BIRTH_DATE))))
                    .andExpect(content().string(not(containsString("1998"))))
                    .andExpect(content().string(not(containsString(FakeIdentityGateway.defaultDiOf("kim")))))
                    .andExpect(content().string(not(containsString(FakeIdentityGateway.defaultCiOf("kim")))))
                    .andExpect(content().string(not(containsString("Enc"))));
        }

        @Test
        @DisplayName("CP-03 VERIFIED 행 완료 2회 — 둘 다 200 · 포트원은 한 번만 묻는다")
        void completeIsIdempotentOnceVerified() throws Exception {
            String id = start(registerToken, "SIGNUP");
            complete(registerToken, id).andExpect(status().isOk());
            int calls = fakeGateway.lookupCalls();

            complete(registerToken, id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.maskedName").value("홍길*"));

            assertThat(fakeGateway.lookupCalls()).isEqualTo(calls);
        }

        @Test
        @DisplayName("CP-04 아직 READY(창만 닫힘) — 409 NOT_COMPLETED · 행 READY 유지 · 다시 불러도 409 · 그 뒤 인증하면 200")
        void readyIsNotCompletedThenSucceeds() throws Exception {
            String id = start(registerToken, "SIGNUP");
            fakeGateway.will(id, Scenario.READY);

            complete(registerToken, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_NOT_COMPLETED"));
            complete(registerToken, id).andExpect(status().isConflict());
            assertThat(verificationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(IdentityVerificationStatus.READY);

            fakeGateway.will(id, Scenario.VERIFIED);
            complete(registerToken, id).andExpect(status().isOk());
        }

        @Test
        @DisplayName("CP-05 포트원이 모르는 id(창을 연 적 없음 · SDK onError) — 409 NOT_COMPLETED")
        void notFoundAtPortOneIsNotCompleted() throws Exception {
            String id = start(registerToken, "SIGNUP");
            fakeGateway.will(id, Scenario.NOT_FOUND);

            complete(registerToken, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_NOT_COMPLETED"));
        }

        @Test
        @DisplayName("CP-06 포트원 FAILED — 422 · 행 FAILED(PG_FAILED) · pg_code · pg_message 보존 · 다시 불러도 포트원을 묻지 않는다")
        void pgFailureIsRecorded() throws Exception {
            String id = start(registerToken, "SIGNUP");
            fakeGateway.will(id, Scenario.FAILED);

            complete(registerToken, id)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"));

            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getFailReason()).isEqualTo(IdentityFailReason.PG_FAILED);
            assertThat(row.getPgCode()).isEqualTo(FakeIdentityGateway.FAILED_PG_CODE);
            assertThat(row.getPgMessage()).isNotBlank();

            int calls = fakeGateway.lookupCalls();
            fakeGateway.will(id, Scenario.VERIFIED);             // 포트원이 뒤늦게 바뀌어도 닫힌 행은 닫힌 행
            complete(registerToken, id).andExpect(status().isUnprocessableEntity());
            assertThat(fakeGateway.lookupCalls()).isEqualTo(calls);
        }

        @Test
        @DisplayName("CP-07 앱이 보낸 pgCode 는 기록만 — 포트원 FAILED 의 pgCode 가 이긴다 · txId 는 남는다")
        void gatewayPgCodeWinsOverClient() throws Exception {
            String id = start(registerToken, "SIGNUP");
            fakeGateway.will(id, Scenario.FAILED);

            mockMvc.perform(post(COMPLETE_PATH.formatted(id)).header(HttpHeaders.AUTHORIZATION, registerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("identityVerificationTxId", "iv-tx-9", "code", "FAILURE_TYPE_PG", "pgCode", "CLIENT"))))
                    .andExpect(status().isUnprocessableEntity());

            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getPgCode()).isEqualTo(FakeIdentityGateway.FAILED_PG_CODE);
            assertThat(row.getIdentityVerificationTxId()).isEqualTo("iv-tx-9");
        }

        @Test
        @DisplayName("CP-08 앱이 「실패」 code 를 보내도 포트원이 VERIFIED 면 200 — 판정은 서버 조회로만")
        void clientCodeDoesNotDecide() throws Exception {
            String id = start(registerToken, "SIGNUP");

            mockMvc.perform(post(COMPLETE_PATH.formatted(id)).header(HttpHeaders.AUTHORIZATION, registerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("identityVerificationTxId", "iv-tx-1", "code", "FAILURE_TYPE_PG", "pgCode", "CS24"))))
                    .andExpect(status().isOk());

            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getIdentityVerificationTxId()).isEqualTo("iv-tx-1");
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
        }

        @Test
        @DisplayName("CP-09 완료 본문 필드가 64자를 넘으면 400 INVALID_INPUT · 포트원을 묻지 않는다")
        void tooLongClientField() throws Exception {
            String id = start(registerToken, "SIGNUP");

            mockMvc.perform(post(COMPLETE_PATH.formatted(id)).header(HttpHeaders.AUTHORIZATION, registerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("identityVerificationTxId", "x".repeat(65)))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            assertThat(fakeGateway.lookupCalls()).isZero();
        }

        @Test
        @DisplayName("CP-10 유효 시간 지난 READY — 409 EXPIRED · 행 FAILED(NOT_COMPLETED) · 다시 불러도 409 EXPIRED")
        void expiredReady() throws Exception {
            String id = start(registerToken, "SIGNUP");
            expire(id);

            complete(registerToken, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_EXPIRED"));
            complete(registerToken, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_EXPIRED"));

            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.FAILED);
            assertThat(row.getFailReason()).isEqualTo(IdentityFailReason.NOT_COMPLETED);
            assertThat(fakeGateway.lookupCalls()).isZero();
        }

        @Test
        @DisplayName("CP-11 VERIFIED 뒤 유효 시간이 지남 — 409 EXPIRED · 보관했던 개인정보를 비운다")
        void expiredVerified() throws Exception {
            String id = start(registerToken, "SIGNUP");
            complete(registerToken, id).andExpect(status().isOk());
            expire(id);

            complete(registerToken, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_EXPIRED"));

            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getFailReason()).isEqualTo(IdentityFailReason.NOT_COMPLETED);
            assertPersonalDataPurged(row);
        }

        @Test
        @DisplayName("CP-12 남의 id · 없는 id — 404 · 존재 여부를 흘리지 않는다 · 포트원을 묻지 않는다")
        void othersIdIsNotFound() throws Exception {
            String id = start(registerToken, "SIGNUP");
            Users other = createUser("lee", "이게스트", RoleType.GUEST);

            complete(registerToken(other.getUsername()), id)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_NOT_FOUND"));
            complete(registerToken, "idvNOPE").andExpect(status().isNotFound());
            assertThat(fakeGateway.lookupCalls()).isZero();
            assertThat(verificationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(IdentityVerificationStatus.READY);
        }

        @Test
        @DisplayName("CP-13 포트원 통신 실패 — 502 · 행 READY 유지 · 같은 id 로 다시 부르면 200")
        void gatewayTimeoutThenRetry() throws Exception {
            String id = start(registerToken, "SIGNUP");
            fakeGateway.will(id, Scenario.TIMEOUT);

            complete(registerToken, id)
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("IDENTITY_GATEWAY_ERROR"));
            assertThat(verificationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(IdentityVerificationStatus.READY);

            fakeGateway.will(id, Scenario.VERIFIED);
            complete(registerToken, id).andExpect(status().isOk());
        }

        @Test
        @DisplayName("CP-14 소비된 행을 다시 완료 — 409 CONSUMED")
        void consumedRowIsRejected() throws Exception {
            String id = start(registerToken, "SIGNUP");
            complete(registerToken, id).andExpect(status().isOk());
            signup(registerToken).andExpect(status().isCreated());

            complete(registerToken(guest.getUsername()), id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_CONSUMED"));
        }
    }

    // ================================================================== AG 14세 · DI

    @Nested
    @DisplayName("AG 만 14세 · DI 누락 (0-8 · 0-5)")
    class AgeAndDi {

        @Test
        @DisplayName("AG-01 만 14세 미만 — 403 UNDER_MIN_AGE · 행 FAILED(UNDER_MIN_AGE) · 개인정보 전부 null · 가입 400")
        void underAgeIsBlockedWithoutStoringPersonalData() throws Exception {
            String id = start(registerToken, "SIGNUP");
            fakeGateway.will(id, Scenario.UNDER_14);

            complete(registerToken, id)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("UNDER_MIN_AGE"));

            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.FAILED);
            assertThat(row.getFailReason()).isEqualTo(IdentityFailReason.UNDER_MIN_AGE);
            assertPersonalDataPurged(row);
            signup(registerToken).andExpect(status().isBadRequest());
            assertThat(userRepository.findByUsername("kim").orElseThrow().getBirthday()).isNull();
        }

        @Test
        @DisplayName("AG-02 14세 미만으로 닫힌 행을 다시 완료해도 403 — 1d 화면 그대로 · 포트원을 다시 묻지 않는다")
        void underAgeStaysForbidden() throws Exception {
            String id = start(registerToken, "SIGNUP");
            fakeGateway.will(id, Scenario.UNDER_14);
            complete(registerToken, id).andExpect(status().isForbidden());
            int calls = fakeGateway.lookupCalls();

            complete(registerToken, id)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("UNDER_MIN_AGE"));
            assertThat(fakeGateway.lookupCalls()).isEqualTo(calls);
        }

        @Test
        @DisplayName("AG-03 DI 가 비어 옴(KCP 설정 오류) — 422 · 행 FAILED(DI_MISSING) · 개인정보 null")
        void missingDiIsRejected() throws Exception {
            String id = start(registerToken, "SIGNUP");
            fakeGateway.will(id, Scenario.NO_DI);

            complete(registerToken, id)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"));

            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getFailReason()).isEqualTo(IdentityFailReason.DI_MISSING);
            assertPersonalDataPurged(row);
        }

        @Test
        @DisplayName("AG-04 14세로 막혀도 새로 시작하면 다시 시도할 수 있다 — 행 1개 = 시도 1회")
        void underAgeThenRetryWithNewId() throws Exception {
            String first = start(registerToken, "SIGNUP");
            fakeGateway.will(first, Scenario.UNDER_14);
            complete(registerToken, first).andExpect(status().isForbidden());

            String second = start(registerToken, "SIGNUP");

            assertThat(second).isNotEqualTo(first);
            complete(registerToken, second).andExpect(status().isOk());
        }
    }

    // ================================================================== SG 가입 결합

    @Nested
    @DisplayName("SG 회원가입 결합 (3-3)")
    class Signup {

        @Test
        @DisplayName("SG-01 완료 뒤 가입 — Users 에 인증 값 · 통신사 · 외국인 · DI 해시 · CI/DI 암호문 · 행 CONSUMED · 개인정보 비움")
        void signupConsumesVerifiedRow() throws Exception {
            String id = start(registerToken, "SIGNUP");
            complete(registerToken, id).andExpect(status().isOk());

            signup(registerToken).andExpect(status().isCreated());

            Users user = userRepository.findByUsername("kim").orElseThrow();
            assertThat(user.getRoleType()).isEqualTo(RoleType.USER);
            assertThat(user.getName()).isEqualTo(FakeIdentityGateway.DEFAULT_NAME);
            assertThat(user.getBirthday()).isEqualTo(FakeIdentityGateway.DEFAULT_BIRTH_DATE);
            assertThat(user.getGender()).isEqualTo(FakeIdentityGateway.DEFAULT_GENDER);
            assertThat(user.getPhoneNumber()).isEqualTo(FakeIdentityGateway.DEFAULT_PHONE);
            assertThat(user.getIdentityOperator()).isEqualTo(FakeIdentityGateway.DEFAULT_OPERATOR);
            assertThat(user.getIdentityForeigner()).isFalse();
            assertThat(user.getIdentityVerifiedAt()).isNotNull();
            assertThat(user.getIdentityDiHash()).isEqualTo(cipher.hmac(FakeIdentityGateway.defaultDiOf("kim")));
            assertThat(cipher.decrypt(user.getIdentityDiEnc())).isEqualTo(FakeIdentityGateway.defaultDiOf("kim"));
            assertThat(cipher.decrypt(user.getIdentityCiEnc())).isEqualTo(FakeIdentityGateway.defaultCiOf("kim"));

            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.CONSUMED);
            assertThat(row.getConsumedAt()).isNotNull();
            assertPersonalDataPurged(row);
        }

        @Test
        @DisplayName("SG-02 인증 없이 가입 — 400 IDENTITY_VERIFICATION_REQUIRED · GUEST 그대로 · 실명 없음")
        void signupWithoutVerificationIsRejected() throws Exception {
            signup(registerToken)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));

            Users user = userRepository.findByUsername("kim").orElseThrow();
            assertThat(user.getRoleType()).isEqualTo(RoleType.GUEST);
            assertThat(user.getName()).isNull();
        }

        @Test
        @DisplayName("SG-03 시작만 하고(READY) 가입 — 400")
        void signupWithReadyRowIsRejected() throws Exception {
            start(registerToken, "SIGNUP");

            signup(registerToken)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));
        }

        @Test
        @DisplayName("SG-04 VERIFIED 지만 10분이 지남 — 400 · 행은 VERIFIED 로 남는다(가입이 닫지 않는다 · 파기 배치 몫)")
        void signupWithExpiredVerifiedRowIsRejected() throws Exception {
            String id = start(registerToken, "SIGNUP");
            complete(registerToken, id).andExpect(status().isOk());
            expire(id);

            signup(registerToken)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));
            assertThat(verificationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
        }

        @Test
        @DisplayName("SG-05 VERIFIED 행이 둘 — 가장 최근에 인증한 것을 쓰고 앞의 것은 VERIFIED 로 남는다")
        void latestVerifiedRowWins() throws Exception {
            String older = start(registerToken, "SIGNUP");
            complete(registerToken, older).andExpect(status().isOk());
            jdbc.update("UPDATE identity_verification SET verified_at = ? WHERE identity_verification_id = ?",
                    LocalDateTime.now().minusMinutes(2), older);
            String newer = start(registerToken, "SIGNUP");
            fakeGateway.willReturnDi(newer, "DI-SECOND");
            complete(registerToken, newer).andExpect(status().isOk());

            signup(registerToken).andExpect(status().isCreated());

            assertThat(userRepository.findByUsername("kim").orElseThrow().getIdentityDiHash()).isEqualTo(cipher.hmac("DI-SECOND"));
            assertThat(verificationRepository.findById(newer).orElseThrow().getStatus()).isEqualTo(IdentityVerificationStatus.CONSUMED);
            assertThat(verificationRepository.findById(older).orElseThrow().getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
        }

        @Test
        @DisplayName("SG-06 registerToken 이 바뀌어도(재로그인) 10분 안의 인증으로 가입 — 인증을 다시 시키지 않는다")
        void newRegisterTokenReusesVerifiedRow() throws Exception {
            String id = start(registerToken, "SIGNUP");
            complete(registerToken, id).andExpect(status().isOk());

            signup(registerToken(guest.getUsername())).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("SG-07 닉네임 중복으로 가입이 실패해도 인증 행은 소비되지 않는다 — 닉네임만 바꿔 다시 가입")
        void nicknameFailureKeepsVerification() throws Exception {
            createUser("taken", "중복닉", RoleType.USER);
            String id = start(registerToken, "SIGNUP");
            complete(registerToken, id).andExpect(status().isOk());

            signupWithNickname(registerToken, "중복닉").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));
            assertThat(verificationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);

            signup(registerToken).andExpect(status().isCreated());
        }
    }

    // ================================================================== DP 중복 차단

    @Nested
    @DisplayName("DP DI 중복 가입 차단 (0-5 · 1-1)")
    class DuplicateBlock {

        @Test
        @DisplayName("DP-01 다른 활성 회원과 같은 DI — 409 · 행 FAILED(DUPLICATE_IDENTITY) · 남의 정보는 행에 남기지 않는다")
        void sameDiAsActiveUserIsBlocked() throws Exception {
            registerVerifiedUser("kim", registerToken);
            String parkToken = newGuestToken("park");
            String id = start(parkToken, "SIGNUP");
            fakeGateway.willReturnDi(id, FakeIdentityGateway.defaultDiOf("kim"));

            complete(parkToken, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDENTITY_ALREADY_REGISTERED"));

            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getFailReason()).isEqualTo(IdentityFailReason.DUPLICATE_IDENTITY);
            assertPersonalDataPurged(row);
            assertThat(userRepository.findByUsername("park").orElseThrow().getRoleType()).isEqualTo(RoleType.GUEST);
        }

        @Test
        @DisplayName("DP-02 중복으로 닫힌 행을 다시 완료해도 409 — 같은 화면")
        void duplicateStaysConflict() throws Exception {
            registerVerifiedUser("kim", registerToken);
            String parkToken = newGuestToken("park");
            String id = start(parkToken, "SIGNUP");
            fakeGateway.willReturnDi(id, FakeIdentityGateway.defaultDiOf("kim"));
            complete(parkToken, id).andExpect(status().isConflict());

            complete(parkToken, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDENTITY_ALREADY_REGISTERED"));
        }

        @Test
        @DisplayName("DP-03 정지(SUSPENDED) 회원과 같은 DI — 409 · 정지는 활성이다(정지 중 다른 소셜로 새 계정 불가)")
        void suspendedUserStillBlocks() throws Exception {
            registerVerifiedUser("kim", registerToken);
            jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE username = ?", "kim");
            String parkToken = newGuestToken("park");
            String id = start(parkToken, "SIGNUP");
            fakeGateway.willReturnDi(id, FakeIdentityGateway.defaultDiOf("kim"));

            complete(parkToken, id).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("DP-04 인플루언서(CREATOR) 와 같은 DI — 409 · 소비자만이 아니라 활성 회원 전체를 본다")
        void creatorStillBlocks() throws Exception {
            registerVerifiedUser("kim", registerToken);
            jdbc.update("UPDATE users SET role_type = 'CREATOR' WHERE username = ?", "kim");
            String parkToken = newGuestToken("park");
            String id = start(parkToken, "SIGNUP");
            fakeGateway.willReturnDi(id, FakeIdentityGateway.defaultDiOf("kim"));

            complete(parkToken, id).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("DP-05 탈퇴 회원과 같은 DI — 통과 · 가입까지 된다(탈퇴는 해시를 파기했다)")
        void withdrawnUserDoesNotBlock() throws Exception {
            Users kim = registerVerifiedUser("kim", registerToken);
            withdraw(userToken(kim)).andExpect(status().is2xxSuccessful());
            String parkToken = newGuestToken("park");
            String id = start(parkToken, "SIGNUP");
            fakeGateway.willReturnDi(id, FakeIdentityGateway.defaultDiOf("kim"));

            complete(parkToken, id).andExpect(status().isOk());
            signup(parkToken).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("DP-06 GUEST(가입 미완료) 끼리는 완료가 둘 다 통과 — 활성 회원이 아니다")
        void guestsDoNotBlockEachOtherAtComplete() throws Exception {
            String kimId = start(registerToken, "SIGNUP");
            complete(registerToken, kimId).andExpect(status().isOk());
            String parkToken = newGuestToken("park");
            String parkId = start(parkToken, "SIGNUP");
            fakeGateway.willReturnDi(parkId, FakeIdentityGateway.defaultDiOf("kim"));

            complete(parkToken, parkId).andExpect(status().isOk());
        }

        @Test
        @DisplayName("DP-07 동시 가입 경쟁 — 같은 DI 로 둘 다 완료한 뒤 먼저 가입한 쪽만 201 · 뒤는 UNIQUE 로 409 · 롤백(GUEST · 행 VERIFIED)")
        void uniqueConstraintIsSecondDefense() throws Exception {
            String kimId = start(registerToken, "SIGNUP");
            complete(registerToken, kimId).andExpect(status().isOk());
            String parkToken = newGuestToken("park");
            String parkId = start(parkToken, "SIGNUP");
            fakeGateway.willReturnDi(parkId, FakeIdentityGateway.defaultDiOf("kim"));
            complete(parkToken, parkId).andExpect(status().isOk());

            signup(registerToken).andExpect(status().isCreated());
            signup(parkToken)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDENTITY_ALREADY_REGISTERED"));

            Users park = userRepository.findByUsername("park").orElseThrow();
            assertThat(park.getRoleType()).isEqualTo(RoleType.GUEST);
            assertThat(park.getName()).isNull();
            assertThat(park.getIdentityDiHash()).isNull();
            assertThat(verificationRepository.findById(parkId).orElseThrow().getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
        }

        @Test
        @DisplayName("DP-08 같은 사람의 재인증(같은 DI · 같은 user) — 중복이 아니다")
        void sameUserReverifyIsAllowed() throws Exception {
            Users kim = registerVerifiedUser("kim", registerToken);
            String token = userToken(kim);

            String id = start(token, "REVERIFY");
            complete(token, id).andExpect(status().isOk());
        }
    }

    // ================================================================== RV 재인증

    @Nested
    @DisplayName("RV 재인증 (C15-2 · 3-4)")
    class Reverify {

        @Test
        @DisplayName("RV-01 시작(REVERIFY) → 완료 → 재인증 API — 200 마스킹 · 값 갱신 · 동의 이력 1건 · 행 CONSUMED")
        void reverifyConsumesRow() throws Exception {
            Users user = createUser("mia", "미아", RoleType.USER);
            String token = userToken(user);
            String id = start(token, "REVERIFY");
            complete(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.purpose").value("REVERIFY"));

            reverify(token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("홍길*"))
                    .andExpect(jsonPath("$.phoneNumber").value("010-****-0000"))
                    .andExpect(jsonPath("$.identityVerifiedAt").isNotEmpty());

            Users refreshed = userRepository.findByUsername("mia").orElseThrow();
            assertThat(refreshed.getName()).isEqualTo(FakeIdentityGateway.DEFAULT_NAME);
            assertThat(refreshed.getIdentityDiHash()).isEqualTo(cipher.hmac(FakeIdentityGateway.defaultDiOf("mia")));
            assertThat(verificationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(IdentityVerificationStatus.CONSUMED);
            assertThat(consentHistoryCount(refreshed.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("RV-02 완료 없이 재인증 — 400 · 동의 이력은 남는다(동의는 별개 행위) · 회원정보 그대로")
        void reverifyWithoutCompletionIsRejected() throws Exception {
            Users user = createUser("mia", "미아", RoleType.USER);

            reverify(userToken(user))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));

            assertThat(userRepository.findByUsername("mia").orElseThrow().getIdentityVerifiedAt()).isNull();
        }

        @Test
        @DisplayName("RV-03 동의하지 않으면 400 IDENTITY_CONSENT_REQUIRED · 완료된 행을 소비하지 않는다")
        void reverifyWithoutConsentKeepsRow() throws Exception {
            Users user = createUser("mia", "미아", RoleType.USER);
            String token = userToken(user);
            String id = start(token, "REVERIFY");
            complete(token, id).andExpect(status().isOk());

            mockMvc.perform(post(REVERIFY_PATH).header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("agreeConsent", false))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IDENTITY_CONSENT_REQUIRED"));

            assertThat(verificationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
        }

        @Test
        @DisplayName("RV-04 SIGNUP 용도 행으로는 재인증할 수 없다 — 400")
        void signupRowCannotBeUsedForReverify() throws Exception {
            String id = start(registerToken, "SIGNUP");
            complete(registerToken, id).andExpect(status().isOk());
            jdbc.update("UPDATE users SET role_type = 'USER' WHERE username = ?", "kim");

            reverify(userToken(userRepository.findByUsername("kim").orElseThrow()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));
        }

        @Test
        @DisplayName("RV-05 재인증 DI 가 기존과 다름(타인 명의) — 허용 · 해시가 새 값으로(정책 미정 · 13절 #7)")
        void differentIdentityOnReverifyIsAllowed() throws Exception {
            Users kim = registerVerifiedUser("kim", registerToken);
            String token = userToken(kim);
            String id = start(token, "REVERIFY");
            fakeGateway.willReturnDi(id, "someone-else-di");

            complete(token, id).andExpect(status().isOk());
            reverify(token).andExpect(status().isOk());

            assertThat(userRepository.findByUsername("kim").orElseThrow().getIdentityDiHash()).isEqualTo(cipher.hmac("someone-else-di"));
        }

        @Test
        @DisplayName("RV-06 재인증 DI 가 다른 활성 회원의 것 — 409(타인 명의 허용과 별개로 중복은 막는다)")
        void reverifyIntoAnotherUsersIdentityIsBlocked() throws Exception {
            registerVerifiedUser("kim", registerToken);
            Users mia = createUser("mia", "미아", RoleType.USER);
            String token = userToken(mia);
            String id = start(token, "REVERIFY");
            fakeGateway.willReturnDi(id, FakeIdentityGateway.defaultDiOf("kim"));

            complete(token, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDENTITY_ALREADY_REGISTERED"));
        }

        @Test
        @DisplayName("RV-07 정지 회원도 재인증할 수 있다(5절)")
        void suspendedUserCanReverify() throws Exception {
            Users user = createUser("mia", "미아", RoleType.USER);
            jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE user_id = ?", user.getId());
            String token = userToken(user);
            String id = start(token, "REVERIFY");
            complete(token, id).andExpect(status().isOk());

            reverify(token).andExpect(status().isOk());
        }

        @Test
        @DisplayName("RV-08 재인증 행이 10분이 지남 — 재인증 API 400")
        void expiredReverifyRow() throws Exception {
            Users user = createUser("mia", "미아", RoleType.USER);
            String token = userToken(user);
            String id = start(token, "REVERIFY");
            complete(token, id).andExpect(status().isOk());
            expire(id);

            reverify(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));
        }
    }

    // ================================================================== WD 탈퇴 · 재가입

    @Nested
    @DisplayName("WD 탈퇴 · 재가입 (5절)")
    class Withdrawal {

        @Test
        @DisplayName("WD-01 탈퇴 API — users 의 identity_* 와 그 사용자의 인증 행 개인정보가 비워진다 · 행 상태는 남는다")
        void withdrawalPurgesIdentity() throws Exception {
            Users kim = registerVerifiedUser("kim", registerToken);
            String token = userToken(kim);
            String pending = start(token, "REVERIFY");
            complete(token, pending).andExpect(status().isOk());       // 소비되지 않은 VERIFIED — 개인정보가 남아 있다

            withdraw(token).andExpect(status().is2xxSuccessful());

            Users purged = userRepository.findByUsername("kim").orElseThrow();
            assertThat(purged.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
            assertThat(purged.getName()).isNull();
            assertThat(purged.getPhoneNumber()).isNull();
            assertThat(purged.getIdentityCiEnc()).isNull();
            assertThat(purged.getIdentityDiEnc()).isNull();
            assertThat(purged.getIdentityDiHash()).isNull();
            assertThat(purged.getIdentityOperator()).isNull();
            assertThat(purged.getIdentityForeigner()).isNull();
            IdentityVerification row = verificationRepository.findById(pending).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
            assertPersonalDataPurged(row);
        }

        @Test
        @DisplayName("WD-02 탈퇴 후 같은 소셜 계정으로 재가입 — 예전 인증으로는 안 되고(400) 다시 인증하면 201 · 같은 DI 가 다시 들어온다")
        void rejoinRequiresNewVerification() throws Exception {
            Users kim = registerVerifiedUser("kim", registerToken);
            withdraw(userToken(kim)).andExpect(status().is2xxSuccessful());
            // SocialLoginService 의 재가입 복구 — WITHDRAWN → NORMAL · role GUEST
            jdbc.update("UPDATE users SET status = 'NORMAL', role_type = 'GUEST' WHERE user_id = ?", kim.getId());
            String rejoinToken = registerToken("kim");

            signup(rejoinToken).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));

            String id = start(rejoinToken, "SIGNUP");
            complete(rejoinToken, id).andExpect(status().isOk());
            signup(rejoinToken).andExpect(status().isCreated());
            assertThat(userRepository.findByUsername("kim").orElseThrow().getIdentityDiHash())
                    .isEqualTo(cipher.hmac(FakeIdentityGateway.defaultDiOf("kim")));
        }
    }

    // ================================================================== PU 파기 배치

    @Nested
    @DisplayName("PU 개인정보 파기 배치 (1-2 · 30일)")
    class Purge {

        @Test
        @DisplayName("PU-01 30일 넘은 미소비 VERIFIED 만 비운다 · 29일은 남긴다 · 행 상태는 그대로")
        void purgesStaleVerifiedRows() throws Exception {
            String stale = start(registerToken, "SIGNUP");
            complete(registerToken, stale).andExpect(status().isOk());
            String parkToken = newGuestToken("park");
            String fresh = start(parkToken, "SIGNUP");
            complete(parkToken, fresh).andExpect(status().isOk());
            shiftRequestedAt(stale, LocalDateTime.now().minusDays(31));
            shiftRequestedAt(fresh, LocalDateTime.now().minusDays(29));

            int purged = purgeScheduler.purge(LocalDateTime.now());

            assertThat(purged).isEqualTo(1);
            IdentityVerification staleRow = verificationRepository.findById(stale).orElseThrow();
            assertThat(staleRow.getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
            assertPersonalDataPurged(staleRow);
            assertThat(verificationRepository.findById(fresh).orElseThrow().getVerifiedDiEnc()).isNotNull();
        }

        @Test
        @DisplayName("PU-02 이미 비운 행(CONSUMED · FAILED · READY)은 세지 않는다 — 두 번 돌려도 0")
        void purgeIgnoresAlreadyEmptyRows() throws Exception {
            String consumed = start(registerToken, "SIGNUP");
            complete(registerToken, consumed).andExpect(status().isOk());
            signup(registerToken).andExpect(status().isCreated());
            String parkToken = newGuestToken("park");
            String failed = start(parkToken, "SIGNUP");
            fakeGateway.will(failed, Scenario.FAILED);
            complete(parkToken, failed).andExpect(status().isUnprocessableEntity());
            String ready = start(newGuestToken("lee"), "SIGNUP");
            for (String id : new String[]{consumed, failed, ready}) {
                shiftRequestedAt(id, LocalDateTime.now().minusDays(40));
            }

            assertThat(purgeScheduler.purge(LocalDateTime.now())).isZero();
            assertThat(purgeScheduler.purge(LocalDateTime.now())).isZero();
        }
    }

    // ================================================================== E2E

    @Nested
    @DisplayName("E2E")
    class EndToEnd {

        @Test
        @DisplayName("E2E-01 소셜 가입(registerToken) → 인증 → 가입 → 받은 accessToken 으로 C15-2 조회(마스킹) → 재인증 → 다시 조회")
        void signupThenAccountThenReverify() throws Exception {
            String id = start(registerToken, "SIGNUP");
            complete(registerToken, id).andExpect(status().isOk());
            String signupBody = signup(registerToken).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String accessToken = "Bearer " + JsonPath.read(signupBody, "$.accessToken");

            mockMvc.perform(get(ACCOUNT_PATH).header(HttpHeaders.AUTHORIZATION, accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("홍길*"))
                    .andExpect(jsonPath("$.birthday").value("1998.04.**"))
                    .andExpect(jsonPath("$.phoneNumber").value("010-****-0000"))
                    .andExpect(jsonPath("$.identityVerifiedAt").isNotEmpty())
                    .andExpect(content().string(not(containsString(FakeIdentityGateway.DEFAULT_NAME))))
                    .andExpect(content().string(not(containsString(FakeIdentityGateway.DEFAULT_PHONE))));

            String reverifyId = start(accessToken, "REVERIFY");
            complete(accessToken, reverifyId).andExpect(status().isOk());
            reverify(accessToken).andExpect(status().isOk());

            mockMvc.perform(get(ACCOUNT_PATH).header(HttpHeaders.AUTHORIZATION, accessToken))
                    .andExpect(jsonPath("$.name").value("홍길*"));
            assertThat(verificationRepository.findById(reverifyId).orElseThrow().getStatus()).isEqualTo(IdentityVerificationStatus.CONSUMED);
        }
    }

    // ================================================================== 단계 · 픽스처

    private ResultActions startRequest(String token, String purpose) throws Exception {
        return mockMvc.perform(post(START_PATH).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("purpose", purpose))));
    }

    private String start(String token, String purpose) throws Exception {
        String body = startRequest(token, purpose)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.identityVerificationId");
    }

    private ResultActions complete(String token, String id) throws Exception {
        return mockMvc.perform(post(COMPLETE_PATH.formatted(id)).header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions signup(String token) throws Exception {
        return signupWithNickname(token, "닉네임" + NICKNAME_SEQ.incrementAndGet());
    }

    private ResultActions signupWithNickname(String token, String nickname) throws Exception {
        return mockMvc.perform(post(SIGNUP_PATH).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("nickname", nickname, "ageAgree", true, "serviceAgree", true, "privacyAgree", true))));
    }

    private ResultActions reverify(String token) throws Exception {
        return mockMvc.perform(post(REVERIFY_PATH).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("agreeConsent", true))));
    }

    private ResultActions withdraw(String token) throws Exception {
        return mockMvc.perform(delete(WITHDRAW_PATH).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"agreeConsent\":true}"));
    }

    /** GUEST 시작 → 완료 → 가입까지 — 활성 USER 하나를 만든다(DI = defaultDiOf(username)). */
    private Users registerVerifiedUser(String username, String token) throws Exception {
        String id = start(token, "SIGNUP");
        complete(token, id).andExpect(status().isOk());
        signup(token).andExpect(status().isCreated());
        Users user = userRepository.findByUsername(username).orElseThrow();
        assertThat(user.getRoleType()).isEqualTo(RoleType.USER);
        return user;
    }

    private String newGuestToken(String username) {
        return registerToken(createUser(username, username + "게스트", RoleType.GUEST).getUsername());
    }

    private void expire(String id) {
        jdbc.update("UPDATE identity_verification SET expires_at = ? WHERE identity_verification_id = ?",
                LocalDateTime.now().minusSeconds(1), id);
    }

    private void shiftRequestedAt(String id, LocalDateTime at) {
        jdbc.update("UPDATE identity_verification SET requested_at = ? WHERE identity_verification_id = ?", at, id);
    }

    private static void assertPersonalDataPurged(IdentityVerification row) {
        assertThat(row.getVerifiedName()).isNull();
        assertThat(row.getVerifiedBirthDate()).isNull();
        assertThat(row.getVerifiedGender()).isNull();
        assertThat(row.getVerifiedPhone()).isNull();
        assertThat(row.getVerifiedOperator()).isNull();
        assertThat(row.getVerifiedForeigner()).isNull();
        assertThat(row.getVerifiedCiEnc()).isNull();
        assertThat(row.getVerifiedDiEnc()).isNull();
    }

    private int consentHistoryCount(Long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_consent_history WHERE user_id = ? AND consent_type = 'IDENTITY_VERIFICATION'",
                Integer.class, userId);
        return count == null ? 0 : count;
    }

    /** 소셜 로그인이 신규 회원에게 돌려주는 registerToken 과 같은 모양 — role 클레임이 없다. */
    private String registerToken(String username) {
        return "Bearer " + tokenProvider.createAuthToken(username, new Date(System.currentTimeMillis() + 5 * 60 * 1000)).getToken();
    }

    private String userToken(Users user) {
        return bearerToken(user.getUsername(), RoleType.USER, user.getId());
    }

    private Users createUser(String username, String nickname, RoleType roleType) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(new Users(
                username, nickname, username + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, roleType, now, now));
    }
}
