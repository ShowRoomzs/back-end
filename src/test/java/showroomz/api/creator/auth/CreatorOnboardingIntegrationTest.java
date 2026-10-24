package showroomz.api.creator.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스튜디오 온보딩(ui-studio-03 rev.3) — 승인 후 첫 로그인의 필수 정보 입력.
 *
 * <p>핵심은 <b>쇼룸 주소가 계정 아이디(인스타그램 핸들) 기준</b>이라는 것이다([정정]). 쇼룸명은 한글이라 핸들로 옮길 글자가 없고,
 * 계정 아이디 기준이라 쇼룸명을 입력하기 전에도 화면에 주소를 미리 보여줄 수 있다.
 */
@DisplayName("[통합] 스튜디오 온보딩")
class CreatorOnboardingIntegrationTest extends IntegrationTestSupport {

    private static final String INFO_PATH = "/v1/creator/auth/registration-info";
    private static final String COMPLETE_PATH = "/v1/creator/auth/complete-registration";
    private static final String CHECK_NAME_PATH = "/v1/creator/auth/check-showroom-name";
    private static final String DUPLICATE_MESSAGE = "이미 사용 중인 쇼룸명입니다. 다른 이름을 입력해 주세요.";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;

    private Creator newCreator;
    private String registerToken;

    @BeforeEach
    void setUpNewCreator() {
        fixture.createBank("004", "국민은행");
        newCreator = createApprovedCreator("soyeon", "beauty.soyeon");
        Users owner = newCreator.getUser();
        registerToken = bearerToken(owner.getUsername(), RoleType.CREATOR, owner.getId());
    }

    @Test
    @DisplayName("등록 정보 — 예금주는 실명 가운데 마스킹, 쇼룸 주소는 계정 아이디로 미리 보여준다")
    void registrationInfoCarriesMaskedHolderAndAddressPreview() throws Exception {
        mockMvc.perform(get(INFO_PATH).header(HttpHeaders.AUTHORIZATION, registerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("beauty.soyeon"))
                .andExpect(jsonPath("$.maskedRealName").value("소*연"))
                .andExpect(jsonPath("$.showroomAddress").value("beauty_soyeon"));
    }

    @Test
    @DisplayName("등록 정보 — 같은 핸들을 이미 쓰는 쇼룸이 있으면 꼬리표가 붙은 주소를 미리 보여준다")
    void addressPreviewAvoidsTakenHandle() throws Exception {
        Creator other = createApprovedCreator("other", "beauty_soyeon");
        other.assignShowroomAddressIfAbsent("beauty_soyeon");
        creatorRepository.save(other);

        mockMvc.perform(get(INFO_PATH).header(HttpHeaders.AUTHORIZATION, registerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showroomAddress").value("beauty_soyeon_2"));
    }

    @Test
    @DisplayName("입력 완료 — 한글 쇼룸명이어도 쇼룸 주소는 계정 아이디로 발급되고 주민번호는 뒤 7자리 전부 가린다")
    void completeRegistrationAssignsAddressFromAccountId() throws Exception {
        mockMvc.perform(post(COMPLETE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, registerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(individualBody("소연의 뷰티로그"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewMember").value(false));

        Creator saved = creatorRepository.findById(newCreator.getId()).orElseThrow();
        assertThat(saved.getShowroomAddress()).isEqualTo("beauty_soyeon");
        assertThat(saved.getShowroomName()).isEqualTo("소연의 뷰티로그");
        assertThat(saved.getResidentRegistrationNumberMasked()).isEqualTo("900101-*******");
        assertThat(saved.getIsNewMember()).isFalse();
    }

    @Test
    @DisplayName("쇼룸명 중복 — 확인 API 와 입력 완료 모두 화면과 같은 문구")
    void duplicateShowroomNameUsesScreenMessage() throws Exception {
        Creator taken = createApprovedCreator("glow", "glowlab");
        taken.completeRegistration("글로우랩", null, null, null, "국민은행", "1101234567", "https://cdn/bankbook.jpg");
        creatorRepository.save(taken);

        mockMvc.perform(get(CHECK_NAME_PATH).param("showroomName", "글로우랩"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(false))
                .andExpect(jsonPath("$.message").value(DUPLICATE_MESSAGE));

        mockMvc.perform(post(COMPLETE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, registerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(individualBody("글로우랩"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SHOWROOM_NAME"))
                .andExpect(jsonPath("$.message").value(DUPLICATE_MESSAGE));
    }

    private Map<String, Object> individualBody(String showroomName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("showroomName", showroomName);
        body.put("businessType", "INDIVIDUAL");
        body.put("residentRegistrationNumber", "900101-2345678");
        body.put("bankCode", "004");
        body.put("accountNumber", "110123456789");
        body.put("bankBookImageUrl", "https://cdn.showroomz.test/creator-document/bankbook.jpg");
        return body;
    }

    private Creator createApprovedCreator(String key, String accountId) {
        LocalDateTime now = LocalDateTime.now();
        Users owner = userRepository.save(new Users(
                "creator-" + key, key, key + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.CREATOR, now, now));
        return creatorRepository.save(Creator.builder()
                .user(owner)
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/" + accountId)
                .accountId(accountId)
                .followerCount(1000)
                .businessEmail(key + "-biz@showroomz.test")
                .realName("소지연")
                .birthday("1990-01-01")
                .phoneNumber("010-1234-5678")
                .build());
    }
}
