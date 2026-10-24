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
import showroomz.global.payment.portone.identity.FakeIdentityGateway;
import showroomz.global.payment.portone.identity.FakeIdentityGateway.Scenario;
import showroomz.global.utils.PersonalDataCipher;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [통합] 포트원 KCP 본인인증(57 설계서 10절 I-01 ~ I-21) — 시작 → 완료 → 가입 / 재인증.
 *
 * <p>포트원은 {@link FakeIdentityGateway}(시나리오 더블)다. 지키는 것 세 가지 — ① 가입 · 재인증은 완료 API 뒤에만 성공한다 ②
 * 14세 미만 · 중복 · DI 누락으로 닫힌 행에는 개인정보가 <b>한 글자도</b> 남지 않는다 ③ 같은 DI 의 다른 활성 회원이 있으면 409.
 */
@DisplayName("[통합] 57 포트원 KCP 본인인증")
class IdentityVerificationIntegrationTest extends IntegrationTestSupport {

    private static final String START_PATH = "/v1/user/identity-verifications";
    private static final String COMPLETE_PATH = "/v1/user/identity-verifications/%s/complete";
    private static final String SIGNUP_PATH = "/v1/user/auth/social/signup";
    private static final String REVERIFY_PATH = "/v1/user/settings/account/verifications";
    private static final String ACCOUNT_PATH = "/v1/user/settings/account";

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
    private JdbcTemplate jdbc;

    private Users guest;
    private String registerToken;

    @BeforeEach
    void setUp() {
        fakeGateway.reset();
        guest = createUser("kim", "김게스트", RoleType.GUEST);
        registerToken = registerToken(guest.getUsername());
    }

    // ------------------------------------------------------------------ 가입

    @Nested
    @DisplayName("시작 (3-1)")
    class Start {

        @Test
        @DisplayName("I-01 GUEST 시작 — idv+ULID(영문·숫자 29자) · storeId · channelKey · READY 행")
        void startIssuesIdAndKeys() throws Exception {
            String id = start(registerToken, "SIGNUP");

            assertThat(id).startsWith("idv").hasSize(29).matches("^[A-Za-z0-9]{1,40}$");
            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.READY);
            assertThat(row.getUserId()).isEqualTo(guest.getId());
            assertThat(row.getChannelKey()).isEqualTo(FakeIdentityGateway.CHANNEL_KEY);
            assertThat(row.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(9));
        }

        @Test
        @DisplayName("I-02 1분 안에 다시 시작하면 같은 id")
        void startIsIdempotentWithinAMinute() throws Exception {
            String first = start(registerToken, "SIGNUP");
            String second = start(registerToken, "SIGNUP");

            assertThat(second).isEqualTo(first);
        }

        @Test
        @DisplayName("응답에 storeId · channelKey 가 실린다 — 앱은 키를 박지 않는다")
        void startCarriesStoreAndChannel() throws Exception {
            mockMvc.perform(post(START_PATH).header(HttpHeaders.AUTHORIZATION, registerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("purpose", "SIGNUP"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.storeId").value(FakeIdentityGateway.STORE_ID))
                    .andExpect(jsonPath("$.channelKey").value(FakeIdentityGateway.CHANNEL_KEY))
                    .andExpect(jsonPath("$.expiresAt").isNotEmpty());
        }

        @Test
        @DisplayName("이미 가입된 사용자가 SIGNUP 을 시작하면 400")
        void registeredUserCannotStartSignup() throws Exception {
            Users user = createUser("done", "가입자", RoleType.USER);

            mockMvc.perform(post(START_PATH).header(HttpHeaders.AUTHORIZATION, userToken(user))
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("purpose", "SIGNUP"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"));
        }

        @Test
        @DisplayName("토큰 없이 시작하면 401")
        void startWithoutTokenIsRejected() throws Exception {
            mockMvc.perform(post(START_PATH).contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("purpose", "SIGNUP"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("완료 · 가입 (3-2 · 3-3)")
    class CompleteAndSignup {

        @Test
        @DisplayName("I-03 완료 — 200 VERIFIED · 마스킹 이름 · 행에 암호문 · 원문은 본문에 없다")
        void completeStoresVerifiedResult() throws Exception {
            String id = start(registerToken, "SIGNUP");

            complete(registerToken, id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("VERIFIED"))
                    .andExpect(jsonPath("$.purpose").value("SIGNUP"))
                    .andExpect(jsonPath("$.maskedName").value("홍길*"))
                    .andExpect(jsonPath("$.maskedPhone").value("010-****-0000"))
                    .andExpect(content().string(not(containsString(FakeIdentityGateway.DEFAULT_NAME))))
                    .andExpect(content().string(not(containsString(FakeIdentityGateway.DEFAULT_PHONE))));

            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
            assertThat(row.getVerifiedName()).isEqualTo(FakeIdentityGateway.DEFAULT_NAME);
            assertThat(cipher.decrypt(row.getVerifiedDiEnc())).isEqualTo(FakeIdentityGateway.defaultDiOf("kim"));
            assertThat(cipher.decrypt(row.getVerifiedCiEnc())).isEqualTo(FakeIdentityGateway.defaultCiOf("kim"));
            assertThat(row.getPgTxId()).isEqualTo("pg-" + id);
        }

        @Test
        @DisplayName("I-04 완료 뒤 가입 — Users 에 인증 값 · DI 해시 · 행 CONSUMED · 개인정보 열 비움")
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

            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.CONSUMED);
            assertThat(row.getConsumedAt()).isNotNull();
            assertPersonalDataPurged(row);
        }

        @Test
        @DisplayName("I-05 인증 없이 가입하면 400 IDENTITY_VERIFICATION_REQUIRED · Users 변경 없음")
        void signupWithoutVerificationIsRejected() throws Exception {
            signup(registerToken)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));

            Users user = userRepository.findByUsername("kim").orElseThrow();
            assertThat(user.getRoleType()).isEqualTo(RoleType.GUEST);
            assertThat(user.getName()).isNull();
        }

        @Test
        @DisplayName("시작만 하고(완료 없이) 가입하면 400")
        void signupWithReadyRowIsRejected() throws Exception {
            start(registerToken, "SIGNUP");

            signup(registerToken)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));
        }

        @Test
        @DisplayName("I-06 만 14세 미만 — 403 · 행 FAILED(UNDER_MIN_AGE) · 개인정보 전부 null")
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
        }

        @Test
        @DisplayName("I-07 포트원 FAILED — 422 · 행 FAILED(PG_FAILED) · pg_code 보존")
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

            // 닫힌 행은 포트원을 다시 묻지 않는다
            int calls = fakeGateway.lookupCalls();
            complete(registerToken, id).andExpect(status().isUnprocessableEntity());
            assertThat(fakeGateway.lookupCalls()).isEqualTo(calls);
        }

        @Test
        @DisplayName("I-08 아직 READY — 409 NOT_COMPLETED · 행 READY 유지 · 다시 불러도 409")
        void readyIsNotCompleted() throws Exception {
            String id = start(registerToken, "SIGNUP");
            fakeGateway.will(id, Scenario.READY);

            complete(registerToken, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_NOT_COMPLETED"));
            complete(registerToken, id).andExpect(status().isConflict());

            assertThat(verificationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(IdentityVerificationStatus.READY);
        }

        @Test
        @DisplayName("포트원이 모르는 id(창을 연 적 없음) — 409 NOT_COMPLETED")
        void notFoundAtPortOneIsNotCompleted() throws Exception {
            String id = start(registerToken, "SIGNUP");
            fakeGateway.will(id, Scenario.NOT_FOUND);

            complete(registerToken, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_NOT_COMPLETED"));
        }

        @Test
        @DisplayName("I-09 유효 시간 지난 행 — 409 EXPIRED · 행 FAILED(NOT_COMPLETED)")
        void expiredRowIsClosed() throws Exception {
            String id = start(registerToken, "SIGNUP");
            jdbc.update("UPDATE identity_verification SET expires_at = ? WHERE identity_verification_id = ?",
                    LocalDateTime.now().minusMinutes(1), id);

            complete(registerToken, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_EXPIRED"));

            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.FAILED);
            assertThat(row.getFailReason()).isEqualTo(IdentityFailReason.NOT_COMPLETED);
        }

        @Test
        @DisplayName("I-10 남의 id 를 완료하면 404 — 존재 여부를 흘리지 않는다")
        void othersIdIsNotFound() throws Exception {
            String id = start(registerToken, "SIGNUP");
            Users other = createUser("lee", "이게스트", RoleType.GUEST);

            complete(registerToken(other.getUsername()), id)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_NOT_FOUND"));
            complete(registerToken, "idvNOPE")
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("I-11 소비된 행을 다시 완료하면 409 CONSUMED")
        void consumedRowIsRejected() throws Exception {
            String id = start(registerToken, "SIGNUP");
            complete(registerToken, id).andExpect(status().isOk());
            signup(registerToken).andExpect(status().isCreated());

            complete(registerToken(guest.getUsername()), id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_CONSUMED"));
        }

        @Test
        @DisplayName("I-12 VERIFIED 행 완료 2회 — 둘 다 200 · 포트원은 한 번만 묻는다")
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
        @DisplayName("I-15b DI 가 비어 오면 422 · 행 FAILED(DI_MISSING) · 개인정보 null")
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
        @DisplayName("I-15c 완료 본문의 SDK 값은 기록만 한다 — 판정에 영향 없음")
        void clientResultIsRecordedOnly() throws Exception {
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
        @DisplayName("I-20 포트원 통신 실패 — 502 · 행 READY 유지")
        void gatewayTimeoutKeepsRowReady() throws Exception {
            String id = start(registerToken, "SIGNUP");
            fakeGateway.will(id, Scenario.TIMEOUT);

            complete(registerToken, id)
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("IDENTITY_GATEWAY_ERROR"));

            assertThat(verificationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(IdentityVerificationStatus.READY);
        }

        @Test
        @DisplayName("I-21 registerToken 이 바뀌어도(재로그인) 10분 안의 인증 행으로 가입한다 — 인증을 다시 시키지 않는다")
        void newRegisterTokenReusesVerifiedRow() throws Exception {
            String id = start(registerToken, "SIGNUP");
            complete(registerToken, id).andExpect(status().isOk());

            signup(registerToken(guest.getUsername())).andExpect(status().isCreated());
        }
    }

    // ------------------------------------------------------------------ 중복 차단

    @Nested
    @DisplayName("DI 중복 가입 차단 (0-5)")
    class DuplicateBlock {

        @Test
        @DisplayName("I-13 다른 활성 회원과 같은 DI — 409 · 행 FAILED(DUPLICATE_IDENTITY) · 개인정보 null")
        void sameDiAsActiveUserIsBlocked() throws Exception {
            registerVerifiedUser("kim", registerToken);
            Users park = createUser("park", "박게스트", RoleType.GUEST);
            String parkToken = registerToken(park.getUsername());
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
        @DisplayName("I-13b 정지(SUSPENDED) 회원과 같은 DI 도 409 — 정지는 활성이다")
        void suspendedUserStillBlocks() throws Exception {
            registerVerifiedUser("kim", registerToken);
            jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE username = ?", "kim");
            Users park = createUser("park", "박게스트", RoleType.GUEST);
            String parkToken = registerToken(park.getUsername());
            String id = start(parkToken, "SIGNUP");
            fakeGateway.willReturnDi(id, FakeIdentityGateway.defaultDiOf("kim"));

            complete(parkToken, id).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("I-14 탈퇴 회원과 같은 DI 는 통과 — 탈퇴는 해시가 비어 있다")
        void withdrawnUserDoesNotBlock() throws Exception {
            registerVerifiedUser("kim", registerToken);
            Users kim = userRepository.findByUsername("kim").orElseThrow();
            transactionTemplate.executeWithoutResult(tx -> {
                Users u = userRepository.findById(kim.getId()).orElseThrow();
                u.updateStatus(showroomz.domain.member.user.type.UserStatus.WITHDRAWN);
                u.purgeOnWithdrawal();
            });
            Users park = createUser("park", "박게스트", RoleType.GUEST);
            String parkToken = registerToken(park.getUsername());
            String id = start(parkToken, "SIGNUP");
            fakeGateway.willReturnDi(id, FakeIdentityGateway.defaultDiOf("kim"));

            complete(parkToken, id).andExpect(status().isOk());
            signup(parkToken).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("같은 사람의 재인증(같은 DI · 같은 user)은 중복이 아니다")
        void sameUserReverifyIsAllowed() throws Exception {
            registerVerifiedUser("kim", registerToken);
            Users kim = userRepository.findByUsername("kim").orElseThrow();
            String token = userToken(kim);

            String id = start(token, "REVERIFY");
            complete(token, id).andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------------ 재인증

    @Nested
    @DisplayName("재인증 (C15-2 · 3-4)")
    class Reverify {

        @Test
        @DisplayName("I-16 시작(REVERIFY) → 완료 → 재인증 API — 값 갱신 · 동의 이력 1건 · 행 CONSUMED")
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

            mockMvc.perform(get(ACCOUNT_PATH).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(jsonPath("$.name").value("홍길*"));
        }

        @Test
        @DisplayName("완료 없이 재인증 API 를 부르면 400 · 동의 이력은 남는다(동의는 별개 행위)")
        void reverifyWithoutCompletionIsRejected() throws Exception {
            Users user = createUser("mia", "미아", RoleType.USER);
            String token = userToken(user);

            reverify(token)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));
            assertThat(userRepository.findByUsername("mia").orElseThrow().getIdentityVerifiedAt()).isNull();
        }

        @Test
        @DisplayName("I-17 SIGNUP 용도 행으로는 재인증할 수 없다 — 400")
        void signupRowCannotBeUsedForReverify() throws Exception {
            String id = start(registerToken, "SIGNUP");
            complete(registerToken, id).andExpect(status().isOk());
            jdbc.update("UPDATE users SET role_type = 'USER' WHERE username = ?", "kim");
            Users kim = userRepository.findByUsername("kim").orElseThrow();

            reverify(userToken(kim))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));
        }

        @Test
        @DisplayName("I-18 재인증 DI 가 기존과 다르면(타인 명의) 허용된다 — 정책 미정")
        void differentIdentityOnReverifyIsAllowed() throws Exception {
            registerVerifiedUser("kim", registerToken);
            Users kim = userRepository.findByUsername("kim").orElseThrow();
            String token = userToken(kim);
            String id = start(token, "REVERIFY");
            fakeGateway.willReturnDi(id, "someone-else-di");

            complete(token, id).andExpect(status().isOk());
            reverify(token).andExpect(status().isOk());

            assertThat(userRepository.findByUsername("kim").orElseThrow().getIdentityDiHash()).isEqualTo(cipher.hmac("someone-else-di"));
        }

        @Test
        @DisplayName("GUEST 가 REVERIFY 를 시작하면 404")
        void guestCannotStartReverify() throws Exception {
            mockMvc.perform(post(START_PATH).header(HttpHeaders.AUTHORIZATION, registerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("purpose", "REVERIFY"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }
    }

    // ------------------------------------------------------------------ 파기

    @Nested
    @DisplayName("파기 (5절 · 1-2)")
    class Purge {

        @Test
        @DisplayName("I-19 탈퇴 파기 — users 의 identity_* 와 그 사용자의 인증 행 개인정보가 비워진다")
        void withdrawalPurgesIdentity() throws Exception {
            registerVerifiedUser("kim", registerToken);
            Users kim = userRepository.findByUsername("kim").orElseThrow();
            String token = userToken(kim);
            String id = start(token, "REVERIFY");
            complete(token, id).andExpect(status().isOk());       // 소비되지 않은 VERIFIED 행에 개인정보가 남아 있다

            transactionTemplate.executeWithoutResult(tx -> {
                Users u = userRepository.findById(kim.getId()).orElseThrow();
                u.purgeOnWithdrawal();
                verificationRepository.purgePersonalDataByUser(kim.getId());
            });

            Users purged = userRepository.findByUsername("kim").orElseThrow();
            assertThat(purged.getIdentityCiEnc()).isNull();
            assertThat(purged.getIdentityDiEnc()).isNull();
            assertThat(purged.getIdentityDiHash()).isNull();
            assertThat(purged.getIdentityOperator()).isNull();
            assertThat(purged.getIdentityForeigner()).isNull();
            IdentityVerification row = verificationRepository.findById(id).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);   // 상태는 남고
            assertPersonalDataPurged(row);                                                 // 개인정보만 비운다
        }
    }

    // ------------------------------------------------------------------ 단계 · 픽스처

    private String start(String token, String purpose) throws Exception {
        String body = mockMvc.perform(post(START_PATH).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("purpose", purpose))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.identityVerificationId");
    }

    private ResultActions complete(String token, String id) throws Exception {
        return mockMvc.perform(post(COMPLETE_PATH.formatted(id)).header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions signup(String token) throws Exception {
        return mockMvc.perform(post(SIGNUP_PATH).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("nickname", "닉네임" + (System.nanoTime() % 1000),
                        "ageAgree", true, "serviceAgree", true, "privacyAgree", true))));
    }

    private ResultActions reverify(String token) throws Exception {
        return mockMvc.perform(post(REVERIFY_PATH).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("agreeConsent", true))));
    }

    /** GUEST 시작 → 완료 → 가입까지 — 활성 USER 하나를 만든다(DI = defaultDiOf(username)). */
    private void registerVerifiedUser(String username, String token) throws Exception {
        String id = start(token, "SIGNUP");
        complete(token, id).andExpect(status().isOk());
        signup(token).andExpect(status().isCreated());
        assertThat(userRepository.findByUsername(username).orElseThrow().getRoleType()).isEqualTo(RoleType.USER);
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
