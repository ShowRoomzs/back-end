package showroomz.api.creator.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.token.AuthTokenProvider;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.type.CreatorAccountVerificationStatus;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.utils.PersonalDataCipher;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스튜디오 온보딩 테스트 시나리오(dev/브랜드, 쇼룸 정보/스튜디오 온보딩/스튜디오_온보딩_테스트_시나리오.md 12-2) — 게이트 · 토큰 · 쇼룸명 ·
 * 사업자 여부 · 주민번호 · 정산 계좌 · 부수 효과 · 연계. 기본 흐름 4건은 {@link CreatorOnboardingIntegrationTest} 가 본다.
 *
 * <p>{@code registerToken} 은 실제 로그인과 같이 <b>role 클레임 없이</b> 서명한다 — 그래야 온보딩 전 스튜디오 기능이 막히는지(TK-02)를
 * 확인할 수 있다. 시나리오 13절의 미결 동작(SN-08 · BZ-05 · BZ-11b 등)은 기대값이 정해지지 않아 여기서 고정하지 않는다.
 */
@DisplayName("[통합] 스튜디오 온보딩 — 시나리오")
class CreatorOnboardingScenarioIntegrationTest extends IntegrationTestSupport {

    private static final String INFO_PATH = "/v1/creator/auth/registration-info";
    private static final String COMPLETE_PATH = "/v1/creator/auth/complete-registration";
    private static final String CHECK_NAME_PATH = "/v1/creator/auth/check-showroom-name";
    private static final String SETTLEMENT_PATH = "/v1/creator/basic-info/settlement";
    private static final String PROFILE_PATH = "/v1/creator/profile";
    private static final String SHOWROOMS_PATH = "/v1/user/showrooms";
    private static final String PUBLIC_IMAGE_PATH = "/v1/common/images";

    private static final String RESIDENT_NUMBER = "900101-2345678";
    private static final String RESIDENT_DIGITS = "9001012345678";
    private static final String RESIDENT_MESSAGE = "주민등록번호를 정확히 입력해 주세요.";
    private static final String DUPLICATE_MESSAGE = "이미 사용 중인 쇼룸명입니다. 다른 이름을 입력해 주세요.";
    private static final String FORMAT_MESSAGE = "쇼룸명은 2~20자, 한글·영문·숫자·공백만 사용할 수 있습니다.";
    private static final String REGISTER_EXPIRED_MESSAGE = "회원가입 유효 시간이 만료되었습니다. 다시 로그인해주세요.";
    private static final String DEFAULT_INVALID_MESSAGE = "입력값이 올바르지 않습니다.";
    private static final String BANKBOOK_URL = "https://cdn.showroomz.test/creator-document/bankbook.jpg";
    private static final String LICENSE_URL = "https://cdn.showroomz.test/creator-document/license.pdf";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;
    @Autowired
    private AuthTokenProvider tokenProvider;
    @Autowired
    private PersonalDataCipher personalDataCipher;
    @Autowired
    private JdbcTemplate jdbc;

    private Creator newCreator;
    private String registerToken;

    @BeforeEach
    void setUpNewCreator() {
        fixture.createBank("004", "국민은행");
        newCreator = createApprovedCreator("soyeon", "beauty.soyeon", SnsType.INSTAGRAM);
        registerToken = registerTokenOf(newCreator.getUser(), 5 * 60 * 1000L);
    }

    // ── 2. 진입 게이트 · 토큰 ─────────────────────────────────────────────

    @Test
    @DisplayName("[TK-02 · TK-03] registerToken(role 없음)으로는 스튜디오 API 가 401 — 온보딩 API 는 열린다 · 완료 뒤 발급 토큰으로는 열린다")
    void registerTokenCannotReachStudioApis() throws Exception {
        for (String path : List.of(SETTLEMENT_PATH, PROFILE_PATH, "/v1/creator/basic-info/account")) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, registerToken))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get(INFO_PATH).header(HttpHeaders.AUTHORIZATION, registerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(CHECK_NAME_PATH).param("showroomName", "소연의 뷰티로그"))
                .andExpect(status().isOk());

        String accessToken = complete(individualBody("소연의 뷰티로그"));

        mockMvc.perform(get(SETTLEMENT_PATH).header(HttpHeaders.AUTHORIZATION, accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[TK-04] 토큰 없음 · Bearer 접두사 없음 — 401 「회원가입 유효 시간이 만료되었습니다」 · 부수 효과 없음")
    void missingTokenIsRegisterExpired() throws Exception {
        String rawToken = registerToken.substring("Bearer ".length());

        mockMvc.perform(get(INFO_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(REGISTER_EXPIRED_MESSAGE));
        mockMvc.perform(post(COMPLETE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(individualBody("소연의 뷰티로그"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post(COMPLETE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(individualBody("소연의 뷰티로그"))))
                .andExpect(status().isUnauthorized());

        assertUntouched();
    }

    @Test
    @DisplayName("[TK-05 · TK-06] 만료된 registerToken · 형식이 깨진 토큰 — 401 · 부수 효과 없음")
    void expiredOrMalformedTokenIsRejected() throws Exception {
        String expired = registerTokenOf(newCreator.getUser(), -1000L);

        submit(expired, individualBody("소연의 뷰티로그"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(REGISTER_EXPIRED_MESSAGE));
        submit("Bearer not-a-jwt", individualBody("소연의 뷰티로그"))
                .andExpect(status().isUnauthorized());

        assertUntouched();
    }

    /**
     * 시나리오 13절 #16 회귀 — 서명이 틀린 토큰은 인증 필터에서 예외를 던지지 않고 인증 없이 통과해야 한다. 온보딩 API 는 401,
     * 인증이 필요한 API 도 401 이다(이전에는 {@code SignatureException} 이 필터 밖으로 나갔다).
     */
    @Test
    @DisplayName("[TK-06] 서명이 틀린 토큰 — 온보딩 API · 스튜디오 API 모두 401")
    void forgedSignatureTokenIsRejected() throws Exception {
        String forged = registerToken + "tampered";

        mockMvc.perform(get(INFO_PATH).header(HttpHeaders.AUTHORIZATION, forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        submit(forged, individualBody("소연의 뷰티로그"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(SETTLEMENT_PATH).header(HttpHeaders.AUTHORIZATION, forged))
                .andExpect(status().isUnauthorized());

        assertUntouched();
    }

    @Test
    @DisplayName("[TK-07 · TK-09] 소비자 계정 토큰 400 ACCOUNT_ROLE_MISMATCH · 인플루언서 행 없는 CREATOR 404 CREATOR_NOT_FOUND")
    void nonCreatorTokensAreRejected() throws Exception {
        Users consumer = createUser("mia", RoleType.USER);
        Users orphan = createUser("orphan", RoleType.CREATOR);

        submit(registerTokenOf(consumer, 60_000L), individualBody("소연의 뷰티로그"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ROLE_MISMATCH"));
        mockMvc.perform(get(INFO_PATH).header(HttpHeaders.AUTHORIZATION, registerTokenOf(orphan, 60_000L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
    }

    @Test
    @DisplayName("[TK-08 · CP-07] 완료 뒤 같은 토큰으로 재제출 · 등록 정보 조회 — 400 ALREADY_REGISTERED · 채널 1개 · 값은 첫 제출 그대로")
    void secondSubmissionIsAlreadyRegistered() throws Exception {
        complete(individualBody("소연의 뷰티로그"));
        Creator first = reload();

        submit(registerToken, individualBody("다른 쇼룸"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.message").value("이미 회원가입이 완료된 사용자입니다."));
        mockMvc.perform(get(INFO_PATH).header(HttpHeaders.AUTHORIZATION, registerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"));

        Creator after = reload();
        assertThat(after.getShowroomName()).isEqualTo("소연의 뷰티로그");
        assertThat(after.getConnectionCode()).isEqualTo(first.getConnectionCode());
        assertThat(operatorChannelCount()).isEqualTo(1);
    }

    // ── 3. 등록 정보 조회 ─────────────────────────────────────────────────

    @Test
    @DisplayName("[RI-05] 미리보기는 선점이 아니다 — 같은 핸들을 다른 쇼룸이 먼저 가져가면 제출 때 꼬리표가 붙는다")
    void addressPreviewIsNotReserved() throws Exception {
        mockMvc.perform(get(INFO_PATH).header(HttpHeaders.AUTHORIZATION, registerToken))
                .andExpect(jsonPath("$.showroomAddress").value("beauty_soyeon"));

        Creator other = createApprovedCreator("other", "beauty_soyeon", SnsType.INSTAGRAM);
        other.assignShowroomAddressIfAbsent("beauty_soyeon");
        creatorRepository.save(other);

        complete(individualBody("소연의 뷰티로그"));

        assertThat(reload().getShowroomAddress()).isEqualTo("beauty_soyeon_2");
    }

    @Test
    @DisplayName("[RI-06] 등록 정보는 여러 번 조회해도 같은 값이고 주소를 저장하지 않는다")
    void registrationInfoDoesNotWrite() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(INFO_PATH).header(HttpHeaders.AUTHORIZATION, registerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.realName").value("소지연"))
                    .andExpect(jsonPath("$.maskedRealName").value("소*연"))
                    .andExpect(jsonPath("$.showroomAddress").value("beauty_soyeon"));
        }

        assertThat(reload().getShowroomAddress()).isNull();
    }

    // ── 4. 쇼룸명 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[SN-01 · SN-03 · SN-03b · SN-04 · SN-05] 확인 API — 토큰 없이 · 사용 가능 · 경계 · 형식 오류 · 빈 값")
    void checkShowroomName() throws Exception {
        expectCheck("소연의 뷰티로그", true, "AVAILABLE", "사용 가능한 쇼룸명입니다.");
        for (String valid : List.of("뷰티", "가".repeat(20), "Soyeon Beauty 01")) {
            expectCheck(valid, true, "AVAILABLE", "사용 가능한 쇼룸명입니다.");
        }
        for (String invalid : List.of("가", "가".repeat(21), "뷰티💄", "뷰티!", "ㅋㅋ", "뷰티\t로그", "뷰티\n로그")) {
            expectCheck(invalid, false, "INVALID_FORMAT", FORMAT_MESSAGE);
        }
        for (String blank : List.of("", "   ")) {
            expectCheck(blank, false, "INVALID_FORMAT", "쇼룸명은 필수 입력값입니다.");
        }
    }

    @Test
    @DisplayName("[SN-07 · SN-07b] 입력 완료 — 쇼룸명 길이 · 문자 · 누락 오류는 400 INVALID_INPUT · 부수 효과 없음")
    void completeRejectsInvalidShowroomName() throws Exception {
        expectInvalid(individualBody("가"), "쇼룸명은 2~20자로 입력해주세요.");
        expectInvalid(individualBody("뷰티!"), "쇼룸명은 한글·영문·숫자·공백만 사용할 수 있습니다.");
        expectInvalid(individualBody(null), "쇼룸명은 필수 입력값입니다.");

        assertUntouched();
    }

    // ── 5. 사업자 여부 · 주민등록번호 ─────────────────────────────────────

    @Test
    @DisplayName("[BZ-01 · CP-09] 비사업자 — 주민번호는 암호문(복호화 = 13자리) · 마스킹 · 등록 시각 · 사업자 값 없음 · 응답에 원문 없음")
    void individualStoresEncryptedResidentNumber() throws Exception {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        String body = submit(registerToken, individualBody("소연의 뷰티로그"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Creator saved = reload();
        assertThat(saved.getBusinessType()).isEqualTo(CreatorBusinessType.INDIVIDUAL);
        assertThat(saved.getResidentRegistrationNumberEnc()).isNotBlank().doesNotContain(RESIDENT_DIGITS);
        assertThat(personalDataCipher.decrypt(saved.getResidentRegistrationNumberEnc())).isEqualTo(RESIDENT_DIGITS);
        assertThat(saved.getResidentRegistrationNumberMasked()).isEqualTo("900101-*******");
        assertThat(saved.getResidentNumberRegisteredAt()).isAfter(before);
        assertThat(saved.getBusinessRegistrationNumber()).isNull();
        assertThat(saved.getBusinessLicenseImageUrl()).isNull();
        assertThat(body).doesNotContain(RESIDENT_DIGITS).doesNotContain("2345678");
    }

    @Test
    @DisplayName("[BZ-02] 하이픈 없음 · 공백 구분도 같은 13자리로 정규화해 저장한다")
    void residentNumberIsNormalized() throws Exception {
        Map<String, Object> body = individualBody("소연의 뷰티로그");
        body.put("residentRegistrationNumber", "900101 2345678");

        complete(body);

        Creator saved = reload();
        assertThat(personalDataCipher.decrypt(saved.getResidentRegistrationNumberEnc())).isEqualTo(RESIDENT_DIGITS);
        assertThat(saved.getResidentRegistrationNumberMasked()).isEqualTo("900101-*******");
    }

    @Test
    @DisplayName("[BZ-03 · BZ-04 · CP-09] 비사업자 주민번호 누락 · 형식 오류 — 400 같은 문구 · 응답에 입력값 없음 · 부수 효과 없음")
    void individualRequiresValidResidentNumber() throws Exception {
        Map<String, Object> missing = individualBody("소연의 뷰티로그");
        missing.remove("residentRegistrationNumber");
        expectInvalid(missing, RESIDENT_MESSAGE);

        List<String> invalids = List.of("", "900101-234567", "900101-23456789", "90010a-2345678",
                "900001-2345678", "901301-2345678", "900100-2345678", "900132-2345678",
                "900101-0345678", "900101-9345678");
        for (String invalid : invalids) {
            Map<String, Object> body = individualBody("소연의 뷰티로그");
            body.put("residentRegistrationNumber", invalid);
            String response = expectInvalid(body, RESIDENT_MESSAGE);
            if (!invalid.isEmpty()) {
                assertThat(response).as(invalid).doesNotContain(invalid);
            }
        }

        assertUntouched();
    }

    @Test
    @DisplayName("[BZ-04b] 성별 자리 1 · 8 · 체크섬이 맞지 않는 번호도 통과한다(2020.10 이후 임의 번호)")
    void residentNumberBoundariesPass() throws Exception {
        Map<String, Object> body = individualBody("소연의 뷰티로그");
        body.put("residentRegistrationNumber", "900101-8000000");

        complete(body);

        assertThat(personalDataCipher.decrypt(reload().getResidentRegistrationNumberEnc())).isEqualTo("9001018000000");
    }

    @Test
    @DisplayName("[BZ-06] businessType 생략 — 기본값 비사업자로 보고 주민번호 규칙을 적용한다")
    void omittedBusinessTypeDefaultsToIndividual() throws Exception {
        Map<String, Object> withoutType = individualBody("소연의 뷰티로그");
        withoutType.remove("businessType");
        withoutType.remove("residentRegistrationNumber");
        expectInvalid(withoutType, RESIDENT_MESSAGE);

        withoutType.put("residentRegistrationNumber", RESIDENT_NUMBER);
        complete(withoutType);

        assertThat(reload().getBusinessType()).isEqualTo(CreatorBusinessType.INDIVIDUAL);
    }

    @Test
    @DisplayName("[BZ-06b · BZ-06c] businessType null 400 「사업자 여부는 필수」 · 법인(CORPORATION) · 소문자 값 400 · 부수 효과 없음")
    void invalidBusinessTypeIsRejected() throws Exception {
        Map<String, Object> nullType = individualBody("소연의 뷰티로그");
        nullType.put("businessType", null);
        expectInvalid(nullType, "사업자 여부는 필수 입력값입니다.");

        for (String unknown : List.of("CORPORATION", "individual")) {
            Map<String, Object> body = individualBody("소연의 뷰티로그");
            body.put("businessType", unknown);
            expectInvalid(body, DEFAULT_INVALID_MESSAGE);
        }

        assertUntouched();
    }

    @Test
    @DisplayName("[BZ-07 · BZ-08] 개인사업자 — 등록번호 · 등록증 저장 · 주민번호는 보내도 무시(3컬럼 null)")
    void businessStoresRegistrationAndIgnoresResidentNumber() throws Exception {
        Map<String, Object> body = businessBody("소연의 뷰티로그");
        body.put("residentRegistrationNumber", RESIDENT_NUMBER);

        complete(body);

        Creator saved = reload();
        assertThat(saved.getBusinessType()).isEqualTo(CreatorBusinessType.BUSINESS);
        assertThat(saved.getBusinessRegistrationNumber()).isEqualTo("123-45-67890");
        assertThat(saved.getBusinessLicenseImageUrl()).isEqualTo(LICENSE_URL);
        assertThat(saved.getResidentRegistrationNumberEnc()).isNull();
        assertThat(saved.getResidentRegistrationNumberMasked()).isNull();
        assertThat(saved.getResidentNumberRegisteredAt()).isNull();
    }

    @Test
    @DisplayName("[BZ-09 · BZ-10] 개인사업자 — 등록번호 · 등록증 누락 400 기본 문구 · 등록번호 형식 오류 400 화면 문구 · 부수 효과 없음")
    void businessRequiresRegistrationFields() throws Exception {
        Map<String, Object> noNumber = businessBody("소연의 뷰티로그");
        noNumber.remove("businessRegistrationNumber");
        expectInvalid(noNumber, DEFAULT_INVALID_MESSAGE);

        Map<String, Object> noLicense = businessBody("소연의 뷰티로그");
        noLicense.remove("businessLicenseImageUrl");
        expectInvalid(noLicense, DEFAULT_INVALID_MESSAGE);

        for (String invalid : List.of("1234567890", "123-456-7890", "")) {
            Map<String, Object> body = businessBody("소연의 뷰티로그");
            body.put("businessRegistrationNumber", invalid);
            expectInvalid(body, "올바른 사업자등록번호 형식으로 입력해 주세요. (예: 000-00-00000)");
        }

        assertUntouched();
    }

    @Test
    @DisplayName("[BZ-11] 비사업자가 사업자등록번호 · 등록증을 같이 보내도 저장하지 않는다")
    void individualIgnoresBusinessFields() throws Exception {
        Map<String, Object> body = individualBody("소연의 뷰티로그");
        body.put("businessRegistrationNumber", "123-45-67890");
        body.put("businessLicenseImageUrl", LICENSE_URL);

        complete(body);

        Creator saved = reload();
        assertThat(saved.getBusinessRegistrationNumber()).isNull();
        assertThat(saved.getBusinessLicenseImageUrl()).isNull();
    }

    // ── 6. 정산 계좌 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("[BA-01 · BA-05 · BA-07] 은행은 코드가 아니라 이름으로 저장 · 예금주는 받지 않는다 · 계좌 확인 상태는 「확인 전」")
    void accountIsStoredWithBankName() throws Exception {
        Map<String, Object> body = individualBody("소연의 뷰티로그");
        body.put("holderName", "아무개");

        complete(body);

        Creator saved = reload();
        assertThat(saved.getBankName()).isEqualTo("국민은행");
        assertThat(saved.getAccountNumber()).isEqualTo("110123456789");
        assertThat(saved.getBankbookImageUrl()).isEqualTo(BANKBOOK_URL);
        assertThat(saved.getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.UNVERIFIED);
        assertThat(saved.getAccountVerifiedAt()).isNull();
    }

    @Test
    @DisplayName("[BA-03 · BA-04] 은행 코드 자릿수 · 누락 · 통장 사본 누락 — 400 필드 문구 · 부수 효과 없음")
    void accountFieldValidation() throws Exception {
        Map<String, Object> shortCode = individualBody("소연의 뷰티로그");
        shortCode.put("bankCode", "04");
        expectInvalid(shortCode, "은행 코드는 3자리여야 합니다.");

        Map<String, Object> noCode = individualBody("소연의 뷰티로그");
        noCode.remove("bankCode");
        expectInvalid(noCode, "은행 코드는 필수 입력값입니다.");

        Map<String, Object> noBankbook = individualBody("소연의 뷰티로그");
        noBankbook.remove("bankBookImageUrl");
        expectInvalid(noBankbook, "통장 사본 URL은 필수 입력값입니다.");

        assertUntouched();
    }

    @Test
    @DisplayName("[BA-03b · CP-06] 없는 은행 404 BANK_NOT_FOUND — 쇼룸명 · 주민번호 검사를 통과한 뒤여도 아무것도 남지 않는다")
    void unknownBankLeavesNothing() throws Exception {
        Map<String, Object> body = individualBody("소연의 뷰티로그");
        body.put("bankCode", "999");

        submit(registerToken, body)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BANK_NOT_FOUND"));

        assertUntouched();
    }

    // ── 7. 서류 업로드 ────────────────────────────────────────────────────

    @Test
    @DisplayName("[DU-07] 비로그인 업로드는 서류 타입만 — PRODUCT · 없는 타입 400 · CREATOR_DOCUMENT 형식 오류는 화면 문구")
    void publicUploadAcceptsOnlyDocumentTypes() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bankbook.jpg", "image/jpeg", new byte[16]);

        for (String type : List.of("PRODUCT", "UNKNOWN")) {
            mockMvc.perform(multipart(PUBLIC_IMAGE_PATH).file(file).param("type", type))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        MockMultipartFile hwp = new MockMultipartFile("file", "통장사본.hwp", "application/octet-stream", new byte[16]);
        mockMvc.perform(multipart(PUBLIC_IMAGE_PATH).file(hwp).param("type", "CREATOR_DOCUMENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"))
                .andExpect(jsonPath("$.message").value("jpg, png, pdf 파일만 업로드할 수 있습니다."));
    }

    // ── 8. 입력 완료 · 부수 효과 ─────────────────────────────────────────

    @Test
    @DisplayName("[CP-01 · CP-02 · CP-03] 완료 — 토큰 응답 · 연결코드 발급 · 운영팀 채널 1개와 쇼룸명이 든 환영 메시지")
    void completionSideEffects() throws Exception {
        submit(registerToken, individualBody("소연의 뷰티로그"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.isNewMember").value(false))
                .andExpect(jsonPath("$.role").value("CREATOR"));

        Creator saved = reload();
        assertThat(saved.getIsNewMember()).isFalse();
        assertThat(saved.getConnectionCode()).isNotBlank();
        assertThat(saved.getConnectionCodeIssuedAt()).isNotNull();
        assertThat(operatorChannelCount()).isEqualTo(1);
        assertThat(operatorChannelMessages())
                .containsExactly("안녕하세요, 소연의 뷰티로그님. 아직 연결된 브랜드가 없네요. 브랜드가 연결 요청을 보내면 [요청함] 탭에서 확인하실 수 있어요.");
    }

    @Test
    @DisplayName("[CP-04] 인스타그램 가입은 공개 인스타그램 URL 기본값 = 채널 주소 · 다른 채널 가입은 비워 둔다")
    void instagramUrlDefaultsFromChannel() throws Exception {
        complete(individualBody("소연의 뷰티로그"));
        assertThat(reload().getInstagramUrl()).isEqualTo("https://instagram.com/beauty.soyeon");

        Creator tiktok = createApprovedCreator("tiktok", "tiktok.soyeon", SnsType.TIKTOK);
        submit(registerTokenOf(tiktok.getUser(), 60_000L), individualBody("틱톡 쇼룸"))
                .andExpect(status().isOk());

        Creator saved = creatorRepository.findById(tiktok.getId()).orElseThrow();
        assertThat(saved.getInstagramUrl()).isNull();
        assertThat(saved.getShowroomAddress()).isEqualTo("tiktok_soyeon");
    }

    @Test
    @DisplayName("[CP-05] 이미 쇼룸 주소가 있는 신규 행은 미리보기 · 완료 모두 그 주소를 쓰고 덮어쓰지 않는다")
    void existingAddressIsKept() throws Exception {
        newCreator.assignShowroomAddressIfAbsent("fixed_handle");
        creatorRepository.save(newCreator);

        mockMvc.perform(get(INFO_PATH).header(HttpHeaders.AUTHORIZATION, registerToken))
                .andExpect(jsonPath("$.showroomAddress").value("fixed_handle"));
        complete(individualBody("소연의 뷰티로그"));

        assertThat(reload().getShowroomAddress()).isEqualTo("fixed_handle");
    }

    @Test
    @DisplayName("[CP-06] 쇼룸명 중복으로 실패하면 연결코드 · 채널 · 주소 · 주민번호 아무것도 남지 않는다")
    void duplicateNameLeavesNothing() throws Exception {
        Creator taken = createApprovedCreator("glow", "glowlab", SnsType.INSTAGRAM);
        taken.completeRegistration("글로우랩", null, null, null, "국민은행", "1101234567", BANKBOOK_URL);
        creatorRepository.save(taken);

        submit(registerToken, individualBody("글로우랩"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SHOWROOM_NAME"))
                .andExpect(jsonPath("$.message").value(DUPLICATE_MESSAGE));

        assertUntouched();
    }

    // ── 9. 화면 간 연계 ───────────────────────────────────────────────────

    @Test
    @DisplayName("[XS-01 · XS-03] 비사업자 완료 → 발급 토큰으로 정산 정보 · 내 쇼룸 — 온보딩 값이 그대로 · 통장 사본 파일명 · 제출일은 null")
    void individualOnboardingFeedsSettlementInfo() throws Exception {
        String accessToken = complete(individualBody("소연의 뷰티로그"));

        String body = mockMvc.perform(get(SETTLEMENT_PATH).header(HttpHeaders.AUTHORIZATION, accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessType.value").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.businessType.label").value("개인 (비사업자)"))
                .andExpect(jsonPath("$.businessType.confirmedAt").doesNotExist())
                .andExpect(jsonPath("$.residentNumber.masked").value("900101-*******"))
                .andExpect(jsonPath("$.residentNumber.registeredAt").isNotEmpty())
                .andExpect(jsonPath("$.business").doesNotExist())
                .andExpect(jsonPath("$.account.bankName").value("국민은행"))
                .andExpect(jsonPath("$.account.accountNumber").value("******456789"))
                .andExpect(jsonPath("$.account.holderName").value("소*연"))
                .andExpect(jsonPath("$.account.holderRule").value("REAL_NAME"))
                .andExpect(jsonPath("$.account.bankbook.url").value(BANKBOOK_URL))
                .andExpect(jsonPath("$.account.bankbook.fileName").doesNotExist())
                .andExpect(jsonPath("$.account.bankbook.submittedAt").doesNotExist())
                .andExpect(jsonPath("$.account.verification.status").value("UNVERIFIED"))
                .andExpect(jsonPath("$.actions.businessTypeChange").value("REQUEST_TO_BUSINESS"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(RESIDENT_DIGITS).doesNotContain("110123456789");

        mockMvc.perform(get(PROFILE_PATH).header(HttpHeaders.AUTHORIZATION, accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedResidentNumber").value("900101-*******"));
    }

    @Test
    @DisplayName("[XS-02] 개인사업자 완료 → 정산 정보 — 주민번호 카드 없음 · 등록번호 · 등록증 URL · 세금계산서 공급받는자 · 비사업자 전환 버튼")
    void businessOnboardingFeedsSettlementInfo() throws Exception {
        String accessToken = complete(businessBody("소연의 뷰티로그"));

        mockMvc.perform(get(SETTLEMENT_PATH).header(HttpHeaders.AUTHORIZATION, accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessType.value").value("BUSINESS"))
                .andExpect(jsonPath("$.residentNumber").doesNotExist())
                .andExpect(jsonPath("$.business.registrationNumber").value("123-45-67890"))
                .andExpect(jsonPath("$.business.license.url").value(LICENSE_URL))
                .andExpect(jsonPath("$.taxInvoiceRecipient").exists())
                .andExpect(jsonPath("$.account.holderRule").value("REAL_NAME_OR_COMPANY"))
                .andExpect(jsonPath("$.actions.businessTypeChange").value("REQUEST_TO_INDIVIDUAL"));
    }

    @Test
    @DisplayName("[XS-04] 온보딩 전 쇼룸은 소비자 쇼룸 목록에 없고, 완료하면 계정 아이디 핸들로 찾힌다")
    void showroomBecomesPublicAfterOnboarding() throws Exception {
        Users consumer = createUser("mia", RoleType.USER);
        String userToken = bearerToken(consumer.getUsername(), RoleType.USER, consumer.getId());

        mockMvc.perform(get(SHOWROOMS_PATH).param("keyword", "beauty_soyeon")
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(0));

        complete(individualBody("소연의 뷰티로그"));

        mockMvc.perform(get(SHOWROOMS_PATH).param("keyword", "beauty_soyeon")
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].showroomAddress").value("beauty_soyeon"));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** 실제 로그인의 registerToken 과 같다 — role 클레임 없이 subject(username)와 만료만 담는다. */
    private String registerTokenOf(Users user, long validMillis) {
        Date expiry = new Date(System.currentTimeMillis() + validMillis);
        return "Bearer " + tokenProvider.createAuthToken(user.getUsername(), expiry).getToken();
    }

    private ResultActions submit(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(COMPLETE_PATH)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)));
    }

    /** 성공해야 하는 제출 — 발급된 access 토큰을 Authorization 헤더 값으로 돌려준다. */
    private String complete(Map<String, Object> body) throws Exception {
        String response = submit(registerToken, body)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return "Bearer " + json.get("accessToken").asText();
    }

    private String expectInvalid(Map<String, Object> body, String message) throws Exception {
        return submit(registerToken, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value(message))
                .andReturn().getResponse().getContentAsString();
    }

    private void expectCheck(String name, boolean available, String code, String message) throws Exception {
        mockMvc.perform(get(CHECK_NAME_PATH).param("showroomName", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(available))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message));
    }

    /** 시나리오 1-2 #1 — 실패한 제출은 인플루언서 행 · 연결코드 · 운영팀 채널에 아무것도 남기지 않는다. */
    private void assertUntouched() {
        Creator current = reload();
        assertThat(current.getIsNewMember()).isTrue();
        assertThat(current.getShowroomName()).isNull();
        assertThat(current.getShowroomAddress()).isNull();
        assertThat(current.getConnectionCode()).isNull();
        assertThat(current.getResidentRegistrationNumberEnc()).isNull();
        assertThat(current.getResidentRegistrationNumberMasked()).isNull();
        assertThat(current.getResidentNumberRegisteredAt()).isNull();
        assertThat(current.getBusinessType()).isNull();
        assertThat(current.getBankName()).isNull();
        assertThat(current.getAccountNumber()).isNull();
        assertThat(current.getBankbookImageUrl()).isNull();
        assertThat(operatorChannelCount()).isZero();
    }

    private Creator reload() {
        return creatorRepository.findById(newCreator.getId()).orElseThrow();
    }

    private int operatorChannelCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM connection WHERE type = 'OPERATOR_CREATOR' AND creator_id = ?",
                Integer.class, newCreator.getId());
        return count == null ? 0 : count;
    }

    private List<String> operatorChannelMessages() {
        return jdbc.queryForList("""
                SELECT m.content FROM message m
                JOIN message_thread t ON t.thread_id = m.thread_id
                JOIN connection c ON c.connection_id = t.connection_id
                WHERE c.type = 'OPERATOR_CREATOR' AND c.creator_id = ?
                """, String.class, newCreator.getId());
    }

    private Map<String, Object> individualBody(String showroomName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("showroomName", showroomName);
        body.put("businessType", "INDIVIDUAL");
        body.put("residentRegistrationNumber", RESIDENT_NUMBER);
        body.put("bankCode", "004");
        body.put("accountNumber", "110123456789");
        body.put("bankBookImageUrl", BANKBOOK_URL);
        return body;
    }

    private Map<String, Object> businessBody(String showroomName) {
        Map<String, Object> body = individualBody(showroomName);
        body.remove("residentRegistrationNumber");
        body.put("businessType", "BUSINESS");
        body.put("businessRegistrationNumber", "123-45-67890");
        body.put("businessLicenseImageUrl", LICENSE_URL);
        return body;
    }

    private Users createUser(String key, RoleType roleType) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(new Users(
                "user-" + key, key, key + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, roleType, now, now));
    }

    private Creator createApprovedCreator(String key, String accountId, SnsType snsType) {
        Users owner = createUser("creator-" + key, RoleType.CREATOR);
        String channelHost = snsType == SnsType.INSTAGRAM ? "https://instagram.com/" : "https://tiktok.com/@";
        return creatorRepository.save(Creator.builder()
                .user(owner)
                .snsType(snsType)
                .channelUrl(channelHost + accountId)
                .accountId(accountId)
                .followerCount(1000)
                .businessEmail(key + "-biz@showroomz.test")
                .realName("소지연")
                .birthday("1990-01-01")
                .phoneNumber("010-1234-5678")
                .build());
    }
}
