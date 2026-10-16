package showroomz.api.creator.basicinfo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.member.creator.entity.Creator;

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
                .andExpect(jsonPath("$.message").value("jpg, png, pdf 파일만 업로드할 수 있습니다."));
    }
}
