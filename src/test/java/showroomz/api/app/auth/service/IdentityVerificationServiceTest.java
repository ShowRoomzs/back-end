package showroomz.api.app.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.app.auth.DTO.IdentityVerificationCompleteResponse;
import showroomz.api.app.auth.DTO.IdentityVerificationStartResponse;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.IdentityVerification;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.IdentityVerificationRepository;
import showroomz.domain.member.user.type.IdentityFailReason;
import showroomz.domain.member.user.type.IdentityVerificationPurpose;
import showroomz.domain.member.user.type.IdentityVerificationStatus;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.portone.PortOneProperties;
import showroomz.global.payment.portone.identity.FakeIdentityGateway;
import showroomz.global.payment.portone.identity.IdentityVerificationResult;
import showroomz.global.payment.portone.identity.PortOneIdentityGateway;
import showroomz.global.utils.PersonalDataCipher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link IdentityVerificationService} 분기(57 테스트 시나리오 SV 절) — 저장소 · 게이트웨이는 목, 암호화는 실제 {@link PersonalDataCipher}.
 *
 * <p>통합 테스트(`IdentityVerificationIntegrationTest`)가 HTTP 부터 DB 까지 흐름을 보고, 여기서는 통합으로 재현하기 번거로운 분기
 * — 판정 순서 · 킬 스위치 · 게이트웨이 예외 종류 · UNIQUE 충돌 변환 · 저장된 사유 → 코드 — 를 하나씩 끊어 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("[57 SV] 본인인증 서비스 — 시작 · 완료 판정 · 소비 · 판정식")
class IdentityVerificationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 10, 0, 0);
    private static final String USERNAME = "kim";
    private static final long USER_ID = 7L;
    private static final String ID = "idv01TESTID";
    private static final String DI = "DI-PLAIN";
    private static final String CI = "CI-PLAIN";

    @Mock
    private IdentityVerificationRepository verificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PortOneIdentityGateway gateway;

    private final PersonalDataCipher cipher = new PersonalDataCipher("", "unit-test-secret-long-enough-for-key-derivation");
    private PortOneProperties properties;
    private IdentityVerificationService service;
    private Users user;

    @BeforeEach
    void setUp() {
        properties = new PortOneProperties();
        service = new IdentityVerificationService(verificationRepository, userRepository, gateway, cipher, properties);
        user = newUser(USER_ID, USERNAME, RoleType.GUEST);
        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
        given(gateway.storeId()).willReturn("store-x");
        given(gateway.channelKey()).willReturn("channel-key-x");
        given(verificationRepository.save(any())).willAnswer(returnsFirstArg());
        given(verificationRepository.findRecentReady(any(), any(), any())).willReturn(Optional.empty());
        given(userRepository.findActiveByIdentityDiHash(anyString())).willReturn(Optional.empty());
    }

    private static Users newUser(long id, String username, RoleType role) {
        Users u = new Users(username, "닉" + id, username + "@showroomz.test", "Y", null, ProviderType.LOCAL, role, NOW, NOW);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private IdentityVerification readyRow(IdentityVerificationPurpose purpose) {
        IdentityVerification row = IdentityVerification.ready(ID, USER_ID, purpose, "channel-key-x", NOW.minusMinutes(1), NOW.plusMinutes(9));
        given(verificationRepository.findById(ID)).willReturn(Optional.of(row));
        return row;
    }

    private static IdentityVerificationResult verified(String birthDate, String ci, String di, LocalDateTime verifiedAt) {
        return IdentityVerificationResult.verified(ID, new IdentityVerificationResult.VerifiedCustomer(
                "홍길동", birthDate, "FEMALE", "01012345678", "SKT", false, ci, di), "pgtx-1", verifiedAt);
    }

    private void gatewayReturns(IdentityVerificationResult result) {
        given(gateway.find(ID)).willReturn(Optional.ofNullable(result));
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", code);
    }

    // ================================================================== 시작

    @Nested
    @DisplayName("시작 (3-1)")
    class Start {

        @Test
        @DisplayName("SV-01 GUEST · SIGNUP — READY 행 저장 · 만료 = 지금 + ttl · 채널 키는 게이트웨이 것 · 응답에 storeId · channelKey")
        void savesReadyRow() {
            properties.getIdentity().setTtlMinutes(5);

            IdentityVerificationStartResponse response = service.start(USERNAME, IdentityVerificationPurpose.SIGNUP, NOW);

            ArgumentCaptor<IdentityVerification> saved = ArgumentCaptor.forClass(IdentityVerification.class);
            verify(verificationRepository).save(saved.capture());
            IdentityVerification row = saved.getValue();
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.READY);
            assertThat(row.getUserId()).isEqualTo(USER_ID);
            assertThat(row.getPurpose()).isEqualTo(IdentityVerificationPurpose.SIGNUP);
            assertThat(row.getChannelKey()).isEqualTo("channel-key-x");
            assertThat(row.getRequestedAt()).isEqualTo(NOW);
            assertThat(row.getExpiresAt()).isEqualTo(NOW.plusMinutes(5));
            assertThat(response.getIdentityVerificationId()).isEqualTo(row.getId()).startsWith("idv").hasSize(29);
            assertThat(response.getStoreId()).isEqualTo("store-x");
            assertThat(response.getChannelKey()).isEqualTo("channel-key-x");
            assertThat(response.getExpiresAt()).isEqualTo(NOW.plusMinutes(5));
        }

        @Test
        @DisplayName("SV-02 연타 멱등 — 60초 창 안의 READY 행을 그대로 · 새로 저장하지 않는다 · 창 시작 = 지금 − 60초")
        void reusesRecentReady() {
            IdentityVerification existing = IdentityVerification.ready("idvEXISTING", USER_ID, IdentityVerificationPurpose.SIGNUP,
                    "channel-key-x", NOW.minusSeconds(30), NOW.plusMinutes(9));
            given(verificationRepository.findRecentReady(USER_ID, IdentityVerificationPurpose.SIGNUP, NOW.minusSeconds(60)))
                    .willReturn(Optional.of(existing));

            IdentityVerificationStartResponse response = service.start(USERNAME, IdentityVerificationPurpose.SIGNUP, NOW);

            assertThat(response.getIdentityVerificationId()).isEqualTo("idvEXISTING");
            assertThat(response.getExpiresAt()).isEqualTo(existing.getExpiresAt());
            verify(verificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("SV-03 이미 가입한 USER 가 SIGNUP — 400 ALREADY_REGISTERED · 저장 없음")
        void registeredUserCannotStartSignup() {
            ReflectionTestUtils.setField(user, "roleType", RoleType.USER);

            assertCode(() -> service.start(USERNAME, IdentityVerificationPurpose.SIGNUP, NOW), ErrorCode.ALREADY_REGISTERED);
            verify(verificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("SV-04 GUEST 가 REVERIFY — 404 USER_NOT_FOUND")
        void guestCannotReverify() {
            assertCode(() -> service.start(USERNAME, IdentityVerificationPurpose.REVERIFY, NOW), ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("SV-05 탈퇴 회원이 REVERIFY — 403 USER_WITHDRAWN(역할보다 먼저 본다)")
        void withdrawnCannotReverify() {
            ReflectionTestUtils.setField(user, "roleType", RoleType.USER);
            user.updateStatus(UserStatus.WITHDRAWN);

            assertCode(() -> service.start(USERNAME, IdentityVerificationPurpose.REVERIFY, NOW), ErrorCode.USER_WITHDRAWN);
        }

        @Test
        @DisplayName("SV-06 정지 회원 · 인플루언서(CREATOR)의 REVERIFY 는 허용 — 본인 정보 갱신은 새로 벌이는 일이 아니다(5절)")
        void suspendedAndCreatorMayReverify() {
            ReflectionTestUtils.setField(user, "roleType", RoleType.USER);
            user.updateStatus(UserStatus.SUSPENDED);
            assertThat(service.start(USERNAME, IdentityVerificationPurpose.REVERIFY, NOW).getIdentityVerificationId()).isNotBlank();

            ReflectionTestUtils.setField(user, "roleType", RoleType.CREATOR);
            user.updateStatus(UserStatus.NORMAL);
            assertThat(service.start(USERNAME, IdentityVerificationPurpose.REVERIFY, NOW).getIdentityVerificationId()).isNotBlank();
        }

        @Test
        @DisplayName("SV-07 모르는 사용자 — 404 USER_NOT_FOUND")
        void unknownUser() {
            assertCode(() -> service.start("nobody", IdentityVerificationPurpose.SIGNUP, NOW), ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("SV-08 게이트웨이가 Fake 면 id ↔ username 을 알려 준다 — 기본 인물의 DI 가 사용자마다 달라진다")
        void bindsUsernameToFake() {
            FakeIdentityGateway fake = new FakeIdentityGateway();
            IdentityVerificationService withFake = new IdentityVerificationService(verificationRepository, userRepository, fake, cipher, properties);

            String id = withFake.start(USERNAME, IdentityVerificationPurpose.SIGNUP, NOW).getIdentityVerificationId();

            assertThat(fake.find(id).orElseThrow().customer().di()).isEqualTo(FakeIdentityGateway.defaultDiOf(USERNAME));
        }
    }

    // ================================================================== 완료

    @Nested
    @DisplayName("완료 — 행 상태 (3-2 ① ~ ③)")
    class CompleteRowState {

        @Test
        @DisplayName("SV-10 행이 없거나 남의 행 — 404 · 포트원을 묻지 않는다")
        void notFoundOrNotOwned() {
            given(verificationRepository.findById(ID)).willReturn(Optional.empty());
            assertCode(() -> service.complete(USERNAME, ID, null, NOW), ErrorCode.IDENTITY_VERIFICATION_NOT_FOUND);

            given(verificationRepository.findById(ID)).willReturn(Optional.of(IdentityVerification.ready(ID, 999L,
                    IdentityVerificationPurpose.SIGNUP, "k", NOW, NOW.plusMinutes(10))));
            assertCode(() -> service.complete(USERNAME, ID, null, NOW), ErrorCode.IDENTITY_VERIFICATION_NOT_FOUND);

            verifyNoInteractions(gateway);
        }

        @Test
        @DisplayName("SV-11 CONSUMED — 409 CONSUMED · 포트원을 묻지 않는다")
        void consumed() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            row.verified("홍길동", "1998-04-12", "FEMALE", "010", "SKT", false, null, "d", "tx", NOW);
            row.consume(NOW);

            assertCode(() -> service.complete(USERNAME, ID, null, NOW), ErrorCode.IDENTITY_VERIFICATION_CONSUMED);
            verify(gateway, never()).find(any());
        }

        @ParameterizedTest(name = "SV-12 FAILED({0}) → {1}")
        @CsvSource({
                "UNDER_MIN_AGE, UNDER_MIN_AGE",
                "DUPLICATE_IDENTITY, IDENTITY_ALREADY_REGISTERED",
                "NOT_COMPLETED, IDENTITY_VERIFICATION_EXPIRED",
                "PG_FAILED, IDENTITY_VERIFICATION_FAILED",
                "DI_MISSING, IDENTITY_VERIFICATION_FAILED"
        })
        @DisplayName("SV-12 닫힌 행을 다시 완료하면 저장된 사유의 코드 그대로 · 포트원을 묻지 않는다")
        void failedRowReturnsStoredReason(IdentityFailReason reason, ErrorCode expected) {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            row.failed(reason, null, null, null);

            assertCode(() -> service.complete(USERNAME, ID, null, NOW), expected);
            verify(gateway, never()).find(any());
        }

        @Test
        @DisplayName("SV-13 사유 없는 FAILED(옛 행) → 422 일반 실패")
        void failedWithoutReason() {
            assertThat(IdentityVerificationService.errorOf(null)).isEqualTo(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        @ParameterizedTest(name = "SV-14 모든 사유가 코드로 매핑된다 — {0}")
        @EnumSource(IdentityFailReason.class)
        @DisplayName("SV-14 사유 enum 에 값이 늘면 매핑이 컴파일에서 막힌다 — 전부 null 아닌 코드")
        void everyReasonMaps(IdentityFailReason reason) {
            assertThat(IdentityVerificationService.errorOf(reason)).isNotNull();
        }

        @Test
        @DisplayName("SV-15 VERIFIED · 만료 전 — 200 멱등 · 포트원을 다시 묻지 않는다 · 마스킹 응답")
        void verifiedIsIdempotent() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            row.verified("홍길동", "1998-04-12", "FEMALE", "01012345678", "SKT", false, null, "d", "tx", NOW.minusSeconds(10));

            IdentityVerificationCompleteResponse response = service.complete(USERNAME, ID, null, NOW);

            assertThat(response.getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
            assertThat(response.getMaskedName()).isEqualTo("홍길*");
            assertThat(response.getMaskedPhone()).isEqualTo("010-****-5678");
            verify(gateway, never()).find(any());
        }

        @Test
        @DisplayName("SV-16 VERIFIED · 만료 뒤 — 409 EXPIRED · 행 FAILED(NOT_COMPLETED) · 개인정보 비움")
        void verifiedButExpired() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            row.verified("홍길동", "1998-04-12", "FEMALE", "010", "SKT", false, "c", "d", "tx", NOW);

            assertCode(() -> service.complete(USERNAME, ID, null, NOW.plusMinutes(9)), ErrorCode.IDENTITY_VERIFICATION_EXPIRED);
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.FAILED);
            assertThat(row.getFailReason()).isEqualTo(IdentityFailReason.NOT_COMPLETED);
            assertThat(row.getVerifiedName()).isNull();
            assertThat(row.getVerifiedDiEnc()).isNull();
            verify(gateway, never()).find(any());
        }

        @Test
        @DisplayName("SV-17 READY · 만료 뒤 — 409 EXPIRED · 포트원을 묻지 않는다(창이 이미 닫혔다)")
        void readyButExpired() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);

            assertCode(() -> service.complete(USERNAME, ID, null, NOW.plusMinutes(9)), ErrorCode.IDENTITY_VERIFICATION_EXPIRED);
            assertThat(row.getFailReason()).isEqualTo(IdentityFailReason.NOT_COMPLETED);
            verify(gateway, never()).find(any());
        }
    }

    @Nested
    @DisplayName("완료 — 포트원 응답 (3-2 ④)")
    class CompleteGateway {

        @Test
        @DisplayName("SV-20 포트원이 모름(empty) — 409 NOT_COMPLETED · 행 READY 유지")
        void notFoundAtPortOne() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            gatewayReturns(null);

            assertCode(() -> service.complete(USERNAME, ID, null, NOW), ErrorCode.IDENTITY_VERIFICATION_NOT_COMPLETED);
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.READY);
        }

        @Test
        @DisplayName("SV-21 포트원 READY — 409 NOT_COMPLETED · 행 READY 유지")
        void readyAtPortOne() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            gatewayReturns(IdentityVerificationResult.ready(ID));

            assertCode(() -> service.complete(USERNAME, ID, null, NOW), ErrorCode.IDENTITY_VERIFICATION_NOT_COMPLETED);
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.READY);
        }

        @Test
        @DisplayName("SV-22 포트원 FAILED — 422 · 행 FAILED(PG_FAILED) · pgCode · pgMessage · pgTxId 보존")
        void failedAtPortOne() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            gatewayReturns(IdentityVerificationResult.failed(ID,
                    new IdentityVerificationResult.Failure("PG_PROVIDER", "CS24", "정보조회가 올바르지 않습니다."), "pgtx-9"));

            assertCode(() -> service.complete(USERNAME, ID, null, NOW), ErrorCode.IDENTITY_VERIFICATION_FAILED);
            assertThat(row.getFailReason()).isEqualTo(IdentityFailReason.PG_FAILED);
            assertThat(row.getPgCode()).isEqualTo("CS24");
            assertThat(row.getPgMessage()).isEqualTo("정보조회가 올바르지 않습니다.");
            assertThat(row.getPgTxId()).isEqualTo("pgtx-9");
        }

        @Test
        @DisplayName("SV-23 포트원 FAILED 인데 failure 가 비어 옴 — 422 · NPE 없이 사유만 남긴다")
        void failedWithoutFailureBody() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            gatewayReturns(IdentityVerificationResult.failed(ID, null, null));

            assertCode(() -> service.complete(USERNAME, ID, null, NOW), ErrorCode.IDENTITY_VERIFICATION_FAILED);
            assertThat(row.getFailReason()).isEqualTo(IdentityFailReason.PG_FAILED);
            assertThat(row.getPgCode()).isNull();
        }

        @Test
        @DisplayName("SV-24 포트원 거절(자격 · 채널 설정 오류) — 502 IDENTITY_GATEWAY_ERROR · 행 READY 유지")
        void rejectedByPortOne() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            given(gateway.find(ID)).willThrow(new PaymentGatewayRejectedException(400, "Unauthorized", "bad secret"));

            assertCode(() -> service.complete(USERNAME, ID, null, NOW), ErrorCode.IDENTITY_GATEWAY_ERROR);
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.READY);
        }

        @Test
        @DisplayName("SV-25 통신 실패(결과 모름) — 502 IDENTITY_GATEWAY_ERROR · 행 READY 유지(같은 id 로 다시 부를 수 있다)")
        void unknownGatewayFailure() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            given(gateway.find(ID)).willThrow(new PaymentGatewayException("timeout"));

            assertCode(() -> service.complete(USERNAME, ID, null, NOW), ErrorCode.IDENTITY_GATEWAY_ERROR);
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.READY);
        }
    }

    @Nested
    @DisplayName("완료 — VERIFIED 판정 (3-2 ⑤ ~ ⑧)")
    class CompleteJudgement {

        @Test
        @DisplayName("SV-30 정상 — 행 VERIFIED · CI · DI 는 암호문으로(복호화하면 원문) · 마스킹 응답 · verifiedAt 은 포트원 값")
        void storesEncrypted() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            LocalDateTime pgVerifiedAt = NOW.minusSeconds(5);
            gatewayReturns(verified("1998-04-12", CI, DI, pgVerifiedAt));

            IdentityVerificationCompleteResponse response = service.complete(USERNAME, ID, null, NOW);

            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
            assertThat(row.getVerifiedCiEnc()).isNotEqualTo(CI);
            assertThat(cipher.decrypt(row.getVerifiedCiEnc())).isEqualTo(CI);
            assertThat(cipher.decrypt(row.getVerifiedDiEnc())).isEqualTo(DI);
            assertThat(row.getVerifiedAt()).isEqualTo(pgVerifiedAt);
            assertThat(row.getPgTxId()).isEqualTo("pgtx-1");
            assertThat(response.getMaskedName()).isEqualTo("홍길*");
            assertThat(response.getPurpose()).isEqualTo(IdentityVerificationPurpose.SIGNUP);
        }

        @Test
        @DisplayName("SV-31 포트원 verifiedAt 이 비면 서버 시각으로")
        void verifiedAtFallsBackToNow() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            gatewayReturns(verified("1998-04-12", CI, DI, null));

            service.complete(USERNAME, ID, null, NOW);

            assertThat(row.getVerifiedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("SV-32 판정 순서 — 14세 미만이면 DI 중복을 보지도 않고 403 · 개인정보 없이 닫는다")
        void ageIsJudgedBeforeDuplicate() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            gatewayReturns(verified(NOW.toLocalDate().minusYears(13).toString(), CI, DI, NOW));

            assertCode(() -> service.complete(USERNAME, ID, null, NOW), ErrorCode.UNDER_MIN_AGE);
            assertThat(row.getFailReason()).isEqualTo(IdentityFailReason.UNDER_MIN_AGE);
            assertThat(row.getVerifiedName()).isNull();
            assertThat(row.getVerifiedBirthDate()).isNull();
            verify(userRepository, never()).findActiveByIdentityDiHash(any());
        }

        @Test
        @DisplayName("SV-33 DI 가 null · 빈 문자열 — 422 · FAILED(DI_MISSING) · 중복 조회 없음")
        void missingDi() {
            for (String di : new String[]{null, "", "  "}) {
                IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
                gatewayReturns(verified("1998-04-12", CI, di, NOW));

                assertCode(() -> service.complete(USERNAME, ID, null, NOW), ErrorCode.IDENTITY_VERIFICATION_FAILED);
                assertThat(row.getFailReason()).as("di=[%s]", di).isEqualTo(IdentityFailReason.DI_MISSING);
                assertThat(row.getVerifiedName()).isNull();
            }
            verify(userRepository, never()).findActiveByIdentityDiHash(any());
        }

        @Test
        @DisplayName("SV-34 같은 DI 의 다른 활성 회원 — 409 · FAILED(DUPLICATE_IDENTITY) · 조회 키는 cipher.hmac(DI)")
        void duplicateIsBlocked() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            gatewayReturns(verified("1998-04-12", CI, DI, NOW));
            given(userRepository.findActiveByIdentityDiHash(cipher.hmac(DI))).willReturn(Optional.of(newUser(99L, "other", RoleType.USER)));

            assertCode(() -> service.complete(USERNAME, ID, null, NOW), ErrorCode.IDENTITY_ALREADY_REGISTERED);
            assertThat(row.getFailReason()).isEqualTo(IdentityFailReason.DUPLICATE_IDENTITY);
            assertThat(row.getVerifiedName()).isNull();
            assertThat(row.getVerifiedDiEnc()).isNull();
        }

        @Test
        @DisplayName("SV-35 같은 DI 로 찾은 회원이 나 자신(재인증) — 중복이 아니다")
        void selfIsNotDuplicate() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.REVERIFY);
            ReflectionTestUtils.setField(user, "roleType", RoleType.USER);
            gatewayReturns(verified("1998-04-12", CI, DI, NOW));
            given(userRepository.findActiveByIdentityDiHash(cipher.hmac(DI))).willReturn(Optional.of(user));

            service.complete(USERNAME, ID, null, NOW);

            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
        }

        @Test
        @DisplayName("SV-36 킬 스위치(duplicate-block=false) — 중복 조회를 하지 않고 VERIFIED")
        void killSwitchSkipsLookup() {
            properties.getIdentity().setDuplicateBlock(false);
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            gatewayReturns(verified("1998-04-12", CI, DI, NOW));
            given(userRepository.findActiveByIdentityDiHash(any())).willReturn(Optional.of(newUser(99L, "other", RoleType.USER)));

            service.complete(USERNAME, ID, null, NOW);

            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
            verify(userRepository, never()).findActiveByIdentityDiHash(any());
        }

        @Test
        @DisplayName("SV-37 CI 가 비어 와도 통과 — 암호문 null(KCP 가맹점 CI 설정 미적용은 WARN 만)")
        void missingCiIsTolerated() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            gatewayReturns(verified("1998-04-12", null, DI, NOW));

            service.complete(USERNAME, ID, null, NOW);

            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
            assertThat(row.getVerifiedCiEnc()).isNull();
            assertThat(row.getVerifiedDiEnc()).isNotNull();
        }

        @Test
        @DisplayName("SV-38 재인증인데 DI 가 기존과 다름(타인 명의) — 정책 미정이라 허용(13절 #7)")
        void differentOwnerOnReverifyIsAllowed() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.REVERIFY);
            ReflectionTestUtils.setField(user, "roleType", RoleType.USER);
            user.applyIdentity("기존", "1990-01-01", "MALE", "010", "KT", false, null, null, cipher.hmac("OLD-DI"), NOW.minusDays(1));
            gatewayReturns(verified("1998-04-12", CI, DI, NOW));

            service.complete(USERNAME, ID, null, NOW);

            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
        }

        @Test
        @DisplayName("SV-39 앱이 보낸 SDK 값은 판정 전에 기록된다 — 포트원 FAILED 의 pgCode 가 앱 값을 이긴다")
        void clientValuesAreRecordedButGatewayWins() {
            IdentityVerification row = readyRow(IdentityVerificationPurpose.SIGNUP);
            gatewayReturns(IdentityVerificationResult.failed(ID, new IdentityVerificationResult.Failure(null, "CS24", "m"), null));
            showroomz.api.app.auth.DTO.IdentityVerificationCompleteRequest request = new showroomz.api.app.auth.DTO.IdentityVerificationCompleteRequest();
            ReflectionTestUtils.setField(request, "identityVerificationTxId", "iv-tx-1");
            ReflectionTestUtils.setField(request, "pgCode", "CLIENT");

            assertCode(() -> service.complete(USERNAME, ID, request, NOW), ErrorCode.IDENTITY_VERIFICATION_FAILED);
            assertThat(row.getIdentityVerificationTxId()).isEqualTo("iv-tx-1");
            assertThat(row.getPgCode()).isEqualTo("CS24");
        }
    }

    // ================================================================== 소비

    @Nested
    @DisplayName("소비 (3-3 · 3-4)")
    class Consume {

        private IdentityVerification verifiedRow(IdentityVerificationPurpose purpose) {
            IdentityVerification row = IdentityVerification.ready(ID, USER_ID, purpose, "k", NOW.minusMinutes(2), NOW.plusMinutes(8));
            row.verified("홍길동", "1998-04-12", "FEMALE", "01012345678", "LGU", true,
                    cipher.encrypt(CI), cipher.encrypt(DI), "tx", NOW.minusMinutes(1));
            return row;
        }

        @Test
        @DisplayName("SV-40 가입 — Users 에 인증 값 · CI/DI 암호문 그대로 · DI 해시 = hmac(복호화 DI) · 행 CONSUMED · 개인정보 비움 · saveAndFlush")
        void signupConsumes() {
            IdentityVerification row = verifiedRow(IdentityVerificationPurpose.SIGNUP);
            String ciEnc = row.getVerifiedCiEnc();
            given(verificationRepository.findLatestVerifiedUnconsumed(USER_ID, IdentityVerificationPurpose.SIGNUP, NOW))
                    .willReturn(Optional.of(row));

            service.consumeForSignup(user, NOW);

            assertThat(user.getName()).isEqualTo("홍길동");
            assertThat(user.getBirthday()).isEqualTo("1998-04-12");
            assertThat(user.getGender()).isEqualTo("FEMALE");
            assertThat(user.getPhoneNumber()).isEqualTo("01012345678");
            assertThat(user.getIdentityOperator()).isEqualTo("LGU");
            assertThat(user.getIdentityForeigner()).isTrue();
            assertThat(user.getIdentityCiEnc()).isEqualTo(ciEnc);
            assertThat(user.getIdentityDiHash()).isEqualTo(cipher.hmac(DI));
            assertThat(user.getIdentityVerifiedAt()).isEqualTo(NOW.minusMinutes(1));
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.CONSUMED);
            assertThat(row.getConsumedAt()).isEqualTo(NOW);
            assertThat(row.getVerifiedDiEnc()).isNull();
            verify(userRepository).saveAndFlush(user);
        }

        @Test
        @DisplayName("SV-41 재인증은 REVERIFY 행만 찾는다 · 가입은 SIGNUP 행만")
        void purposeIsSeparated() {
            given(verificationRepository.findLatestVerifiedUnconsumed(any(), any(), any())).willReturn(Optional.empty());

            assertCode(() -> service.consumeForReverify(user, NOW), ErrorCode.IDENTITY_VERIFICATION_REQUIRED);
            verify(verificationRepository).findLatestVerifiedUnconsumed(USER_ID, IdentityVerificationPurpose.REVERIFY, NOW);

            assertCode(() -> service.consumeForSignup(user, NOW), ErrorCode.IDENTITY_VERIFICATION_REQUIRED);
            verify(verificationRepository).findLatestVerifiedUnconsumed(USER_ID, IdentityVerificationPurpose.SIGNUP, NOW);
        }

        @Test
        @DisplayName("SV-42 소비할 행이 없으면 400 · Users 를 건드리지 않는다")
        void nothingToConsume() {
            given(verificationRepository.findLatestVerifiedUnconsumed(any(), any(), any())).willReturn(Optional.empty());

            assertCode(() -> service.consumeForSignup(user, NOW), ErrorCode.IDENTITY_VERIFICATION_REQUIRED);
            assertThat(user.getName()).isNull();
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("SV-43 UNIQUE(identity_di_hash) 충돌 — 409 IDENTITY_ALREADY_REGISTERED 로 바꾼다(동시 가입 경쟁의 2차 방어)")
        void uniqueViolationBecomesConflict() {
            IdentityVerification row = verifiedRow(IdentityVerificationPurpose.SIGNUP);
            given(verificationRepository.findLatestVerifiedUnconsumed(any(), any(), any())).willReturn(Optional.of(row));
            given(userRepository.saveAndFlush(eq(user))).willThrow(new DataIntegrityViolationException("uk_users_identity_di_hash"));

            assertCode(() -> service.consumeForSignup(user, NOW), ErrorCode.IDENTITY_ALREADY_REGISTERED);
        }
    }

    // ================================================================== 판정식 · id

    @Nested
    @DisplayName("만 14세 판정")
    class MinAge {

        private final LocalDate today = LocalDate.of(2026, 10, 24);

        @Test
        @DisplayName("SV-50 오늘이 14번째 생일이면 통과")
        void fourteenthBirthdayPasses() {
            assertThat(IdentityVerificationService.isUnderMinAge("2012-10-24", today)).isFalse();
        }

        @Test
        @DisplayName("SV-51 14번째 생일 하루 전이면 차단")
        void dayBeforeFourteenthBirthdayIsBlocked() {
            assertThat(IdentityVerificationService.isUnderMinAge("2012-10-25", today)).isTrue();
        }

        @Test
        @DisplayName("SV-52 윤일생(2012-02-29) — 2026-02-28 은 아직 13세 · 2026-03-01 에 14세")
        void leapDayBirth() {
            assertThat(IdentityVerificationService.isUnderMinAge("2012-02-29", LocalDate.of(2026, 2, 28))).isTrue();
            assertThat(IdentityVerificationService.isUnderMinAge("2012-02-29", LocalDate.of(2026, 3, 1))).isFalse();
        }

        @Test
        @DisplayName("SV-53 해석할 수 없는 생년월일은 막지 않는다 — 인증사가 이미 걸렀다")
        void unparsableBirthDateIsNotBlocked() {
            assertThat(IdentityVerificationService.isUnderMinAge("19981204", today)).isFalse();
            assertThat(IdentityVerificationService.isUnderMinAge("1998-13-40", today)).isFalse();
            assertThat(IdentityVerificationService.isUnderMinAge(null, today)).isFalse();
            assertThat(IdentityVerificationService.isUnderMinAge("", today)).isFalse();
        }
    }

    @Nested
    @DisplayName("identityVerificationId 발급 (R-4 — KCP: 영문·숫자 40자 이하)")
    class IdGenerator {

        private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

        @Test
        @DisplayName("SV-60 idv + 26자 · 영문 대문자 · 숫자만 · 1만 회 전부 통과 · 중복 없음")
        void idsSatisfyKcpConstraint() {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 10_000; i++) {
                String id = IdentityVerificationIdGenerator.next();
                assertThat(id).startsWith("idv").hasSize(29);
                assertThat(IdentityVerificationIdGenerator.KCP_ALLOWED.matcher(id).matches()).as(id).isTrue();
                assertThat(seen.add(id)).as("중복 id: " + id).isTrue();
            }
        }

        @Test
        @DisplayName("SV-61 앞 10자는 시각(ms)을 Crockford base32 로 — 되읽으면 같은 값 · 나중 id 가 사전순으로 뒤")
        void timePrefixRoundTrips() {
            long epoch = 1_792_800_000_000L;     // 2026-10 무렵
            String id = IdentityVerificationIdGenerator.next(epoch);

            long decoded = 0;
            for (char c : id.substring(3, 13).toCharArray()) {
                decoded = (decoded << 5) | ALPHABET.indexOf(c);
            }
            assertThat(decoded).isEqualTo(epoch);
            assertThat(IdentityVerificationIdGenerator.next(epoch + 1).substring(3, 13)).isGreaterThan(id.substring(3, 13));
        }

        @Test
        @DisplayName("SV-62 Crockford 알파벳만 — 헷갈리는 I · L · O · U 가 없다")
        void crockfordAlphabetOnly() {
            for (int i = 0; i < 1_000; i++) {
                assertThat(IdentityVerificationIdGenerator.next().substring(3)).doesNotContain("I", "L", "O", "U");
            }
        }
    }
}
