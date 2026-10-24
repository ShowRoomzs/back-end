package showroomz.api.creator.basicinfo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.member.creator.entity.Creator;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스튜디오 기본정보 관리 — 정산 정보 탭 조회(ui-studio-06 T1 · 50 설계서 14절 ST · UP). 사업자 구분 변경 배너는
 * {@code CreatorBusinessTypeChangeIntegrationTest} 가 본다.
 */
@DisplayName("스튜디오 기본정보 — 정산 정보 조회 · 서류 업로드 규칙")
class CreatorSettlementInfoIntegrationTest extends SettlementTestSupport {

    private static final String SETTLEMENT = "/v1/creator/basic-info/settlement";

    @Test
    @DisplayName("[ST-01] 비사업자 — 주민번호 뒤 7자리 마스킹(이전 저장분도) · 사업자 블록 없음 · 계좌 뒤 6자리 · 예금주 = 실명 마스킹 · 금액 없음")
    void individual() throws Exception {
        registerCreatorAccount();
        registerCreatorResidentNumber();

        JsonNode body = json(creatorGet(SETTLEMENT).andExpect(status().isOk())
                .andExpect(jsonPath("$.businessType.value").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.businessType.label").value("개인 (비사업자)"))
                .andExpect(jsonPath("$.residentNumber.masked").value("900101-*******"))
                .andExpect(jsonPath("$.business").doesNotExist())
                .andExpect(jsonPath("$.taxInvoiceRecipient").doesNotExist())
                .andExpect(jsonPath("$.account.bankName").value("국민은행"))
                .andExpect(jsonPath("$.account.accountNumber").value("******789012"))
                .andExpect(jsonPath("$.account.holderName").value("김*민"))
                .andExpect(jsonPath("$.account.holderRule").value("REAL_NAME"))
                .andExpect(jsonPath("$.account.verification.status").value("UNVERIFIED"))
                .andExpect(jsonPath("$.account.verification.label").value("확인 전"))
                .andExpect(jsonPath("$.businessTypeChange").doesNotExist())
                .andExpect(jsonPath("$.actions.businessTypeChange").value("REQUEST_TO_BUSINESS"))
                .andExpect(jsonPath("$.actions.accountChange").value(true)));
        assertThat(body.toString()).doesNotContain("123456789012").doesNotContain("김지민").doesNotContain("Amount");
    }

    @Test
    @DisplayName("[ST-02] 사업자 — 주민번호 카드 없음 · 등록번호 · 등록증 · 세금계산서 공급받는자 · 비사업자 전환 버튼")
    void business() throws Exception {
        registerCreatorAccount();
        makeCreatorBusiness();
        jdbc.update("UPDATE creator SET business_registration_number = ?, business_license_image_url = ? WHERE creator_id = ?",
                "123-45-67890", "https://cdn.showroomz.test/docs/license.pdf", creator.getId());

        creatorGet(SETTLEMENT).andExpect(status().isOk())
                .andExpect(jsonPath("$.businessType.value").value("BUSINESS"))
                .andExpect(jsonPath("$.residentNumber").doesNotExist())
                .andExpect(jsonPath("$.business.registrationNumber").value("123-45-67890"))
                .andExpect(jsonPath("$.business.license.url").value("https://cdn.showroomz.test/docs/license.pdf"))
                .andExpect(jsonPath("$.taxInvoiceRecipient").exists())
                .andExpect(jsonPath("$.account.holderRule").value("REAL_NAME_OR_COMPANY"))
                .andExpect(jsonPath("$.actions.businessTypeChange").value("REQUEST_TO_INDIVIDUAL"));
    }

    @Test
    @DisplayName("주민등록번호 등록 시각 — 등록하면 채우고, 사업자(null)면 비운다")
    void residentNumberRegisteredAt() {
        Creator target = creatorRepository.findById(creator.getId()).orElseThrow();
        target.registerResidentNumber("enc", "900101-*******");
        assertThat(target.getResidentNumberRegisteredAt()).isNotNull();
        target.registerResidentNumber(null, null);
        assertThat(target.getResidentNumberRegisteredAt()).isNull();
    }

    @Test
    @DisplayName("[UP-01] 로그인 인플루언서도 서류(CREATOR_DOCUMENT)를 올린다 — 10MB 초과 413 · heic 400 — 화면 문구")
    void creatorDocumentUploadRules() throws Exception {
        // 용량 초과는 기존 FILE_SIZE_EXCEEDED(413) — 문구만 스튜디오 화면 문구다.
        MockMultipartFile tooLarge = new MockMultipartFile("file", "통장사본.pdf", "application/pdf",
                new byte[10 * 1024 * 1024 + 1]);
        mockMvc.perform(multipart("/v1/creator/images").file(tooLarge).param("type", "CREATOR_DOCUMENT")
                        .header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("파일 크기가 10MB를 초과했습니다. 10MB 이하의 파일을 선택해 주세요."));

        MockMultipartFile heic = new MockMultipartFile("file", "통장사본.heic", "image/heic", new byte[1024]);
        mockMvc.perform(multipart("/v1/creator/images").file(heic).param("type", "CREATOR_DOCUMENT")
                        .header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"))
                .andExpect(jsonPath("$.message").value("jpg, png, pdf 파일만 업로드할 수 있습니다."));
    }

    // ------------------------------------------------------------------ 시나리오 보완(50 테스트 시나리오 14-2)

    @Test
    @DisplayName("[SI-01] 사업자 전환 뒤 주민번호 암호문이 남아 있어도 주민번호 카드는 없다(현재 구분만 그린다)")
    void businessWithRetainedResidentNumber() throws Exception {
        registerCreatorAccount();
        registerCreatorResidentNumber();
        makeCreatorBusiness();

        creatorGet(SETTLEMENT).andExpect(status().isOk())
                .andExpect(jsonPath("$.businessType.value").value("BUSINESS"))
                .andExpect(jsonPath("$.residentNumber").doesNotExist());
    }

    @Test
    @DisplayName("[SI-02] 계좌 확인 상태별 표시 — 운영자 확인 중 · 확인 완료(확인일) · 실패(사유 라벨 · 모르는 코드는 「계좌 확인 실패」) · 변경 필요(1021 기-22 라벨)")
    void verificationStatusDisplay() throws Exception {
        registerCreatorAccount();

        verification("VERIFYING", null, null);
        creatorGet(SETTLEMENT)
                .andExpect(jsonPath("$.account.verification.label").value("운영자 확인 중"))
                .andExpect(jsonPath("$.account.verification.tone").value("INFO"))
                .andExpect(jsonPath("$.account.verification.failReason").doesNotExist());

        verification("VERIFIED", null, LocalDateTime.of(2026, 7, 2, 10, 15, 30));
        creatorGet(SETTLEMENT)
                .andExpect(jsonPath("$.account.verification.label").value("확인 완료"))
                .andExpect(jsonPath("$.account.verification.tone").value("SUCCESS"))
                .andExpect(jsonPath("$.account.verification.verifiedAt").value("2026-07-02T10:15:30Z"));

        verification("FAILED", "ACCOUNT_HOLDER_MISMATCH", null);
        creatorGet(SETTLEMENT)
                .andExpect(jsonPath("$.account.verification.label").value("확인 실패"))
                .andExpect(jsonPath("$.account.verification.tone").value("WARNING"))
                .andExpect(jsonPath("$.account.verification.failReason").value("예금주 불일치"));

        verification("FAILED", "BANK_REJECTED", null);
        creatorGet(SETTLEMENT).andExpect(jsonPath("$.account.verification.failReason").value("계좌 확인 실패"));

        verification("CHANGE_REQUIRED", null, null);
        creatorGet(SETTLEMENT)
                .andExpect(jsonPath("$.account.verification.label").value("변경 필요"))
                .andExpect(jsonPath("$.account.verification.tone").value("WARNING"))
                .andExpect(jsonPath("$.account.verification.failReason").doesNotExist());
    }

    @Test
    @DisplayName("[SI-03] 온보딩 통장 사본 — URL 만 있고 파일명 · 제출 시각은 없다(가짜 값을 만들지 않는다)")
    void onboardingBankbookHasNoMeta() throws Exception {
        registerCreatorAccount();
        jdbc.update("UPDATE creator SET BANKBOOK_IMAGE_URL = ? WHERE creator_id = ?",
                "https://cdn.showroomz.test/creator-document/onboarding.jpg", creator.getId());

        creatorGet(SETTLEMENT)
                .andExpect(jsonPath("$.account.bankbook.url").value("https://cdn.showroomz.test/creator-document/onboarding.jpg"))
                .andExpect(jsonPath("$.account.bankbook.fileName").doesNotExist())
                .andExpect(jsonPath("$.account.bankbook.submittedAt").doesNotExist());
    }

    @Test
    @DisplayName("[UP-03 · UP-04] 비로그인 온보딩 경로도 10MB 상한 · 스튜디오 업로드는 인플루언서만(셀러 403 · 토큰 없음 401)")
    void creatorDocumentUploadPaths() throws Exception {
        MockMultipartFile tooLarge = new MockMultipartFile("file", "통장사본.pdf", "application/pdf",
                new byte[11 * 1024 * 1024]);
        mockMvc.perform(multipart("/v1/common/images").file(tooLarge).param("type", "CREATOR_DOCUMENT"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_SIZE_EXCEEDED"));

        MockMultipartFile pdf = new MockMultipartFile("file", "통장사본.pdf", "application/pdf", new byte[1024]);
        mockMvc.perform(multipart("/v1/creator/images").file(pdf).param("type", "CREATOR_DOCUMENT")
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/v1/creator/images").file(pdf).param("type", "CREATOR_DOCUMENT"))
                .andExpect(status().isUnauthorized());
    }

    private void verification(String status, String code, LocalDateTime verifiedAt) {
        jdbc.update("UPDATE creator SET account_verification_status = ?, account_verification_code = ?, "
                + "account_verified_at = ? WHERE creator_id = ?", status, code, verifiedAt, creator.getId());
    }
}
