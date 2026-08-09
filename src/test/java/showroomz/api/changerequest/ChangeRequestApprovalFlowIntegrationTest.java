package showroomz.api.changerequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.support.BrandFixture;
import showroomz.support.ChangeRequestSteps;
import showroomz.support.IntegrationTestSupport;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 요청 → 어드민 심사 → 파트너센터 배너·반영값 확인까지 한 흐름으로 태운다.
 * 개별 엔드포인트 검증은 각 API 테스트가 맡고, 여기서는 <b>두 화면이 같은 레코드를 같은 뜻으로 읽는지</b>를 본다.
 */
@DisplayName("[통합] 변경 요청 승인 플로우 (파트너센터 ↔ 어드민)")
class ChangeRequestApprovalFlowIntegrationTest extends IntegrationTestSupport {

    private static final String BRAND_EMAIL = "brand@showroomz.test";
    private static final String BRAND_NAME = "코코브라운";

    private BrandFixture.Brand brand;
    private String brandToken;
    private String opsToken;

    @BeforeEach
    void setUpActors() {
        brand = fixture.createBrand(BRAND_EMAIL, BRAND_NAME);
        brandToken = sellerToken(brand.seller());
        opsToken = adminToken(fixture.createAdmin("ops@showroomz.test", "정운영"));
        fixture.createBank("088", "신한은행");
        fixture.createBank("004", "KB국민은행");
    }

    private String requestCode(int sequence) {
        return String.format("CHG-%d-%04d", Year.now().getValue(), sequence);
    }

    @Test
    @DisplayName("사업자 정보 — 요청·승인 후 사업자 정보 탭에 반영값과 승인 배너가 함께 뜨고, 확인하면 배너가 사라지며 재요청이 열린다")
    void businessInfoApprovalFlow() throws Exception {
        // 1. 브랜드가 대표자명·업태 변경을 요청한다
        Map<String, String> items = new LinkedHashMap<>();
        items.put("REPRESENTATIVE_NAME", "이대표");
        items.put("BUSINESS_CONDITION", "제조업");
        long requestId = changeRequests.createBusinessInfo(brandToken, "대표자 변경", items);

        // 2. 사업자 정보 탭에는 아직 옛 값 + 검토 중 배너
        mockMvc.perform(get("/v1/seller/basic-info/business").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.representativeName").value("김대표"))
                .andExpect(jsonPath("$.changeRequest.requestId").value(requestId))
                .andExpect(jsonPath("$.changeRequest.status").value("PENDING"))
                .andExpect(jsonPath("$.changeRequest.cancelable").value(true))
                .andExpect(jsonPath("$.changeRequest.changedFieldLabels", contains("대표자명", "업태")));

        // 3. 어드민 GNB 배지와 목록에 잡힌다
        mockMvc.perform(get("/v1/admin/change-requests/summary").header(HttpHeaders.AUTHORIZATION, opsToken))
                .andExpect(jsonPath("$.pendingCount").value(1));
        mockMvc.perform(get("/v1/admin/change-requests").header(HttpHeaders.AUTHORIZATION, opsToken))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].requestCode").value(requestCode(1)));

        // 4. 운영자가 승인한다
        mockMvc.perform(post("/v1/admin/change-requests/{id}/approve", requestId)
                        .header(HttpHeaders.AUTHORIZATION, opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 5. 파트너센터에는 반영된 값과 승인 배너가 함께 보인다 — 취소 버튼은 사라진다
        mockMvc.perform(get("/v1/seller/basic-info/business").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.representativeName").value("이대표"))
                .andExpect(jsonPath("$.businessCondition").value("제조업"))
                .andExpect(jsonPath("$.changeRequest.status").value("APPROVED"))
                .andExpect(jsonPath("$.changeRequest.cancelable").value(false))
                .andExpect(jsonPath("$.changeRequest.processedAt").value(notNullValue()))
                .andExpect(jsonPath("$.changeRequest.rejectReason").value(nullValue()));

        // 6. 배너 [확인]을 누르면 더 이상 노출되지 않는다
        mockMvc.perform(post("/v1/seller/change-requests/{id}/acknowledge", requestId)
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/seller/basic-info/business").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeRequest").value(nullValue()));

        // 7. 처리가 끝났으므로 같은 유형으로 다시 요청할 수 있다 — 재요청은 신규 행이다(§16-6)
        changeRequests.request(brandToken, ChangeRequestSteps.businessInfoPayload("상호 변경",
                        Map.of("COMPANY_NAME", "주식회사 코코브라운코리아")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestCode").value(requestCode(2)));

        mockMvc.perform(get("/v1/admin/change-requests")
                        .param("status", "ALL")
                        .header(HttpHeaders.AUTHORIZATION, opsToken))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.statusCounts.pending").value(1))
                .andExpect(jsonPath("$.statusCounts.approved").value(1));
    }

    @Test
    @DisplayName("사업자 정보 — 반려하면 사유가 가공 없이 배너에 실리고, 원본 값은 그대로 남는다")
    void businessInfoRejectionFlow() throws Exception {
        long requestId = changeRequests.createBusinessInfo(brandToken, "대표자 변경",
                Map.of("REPRESENTATIVE_NAME", "이대표"));

        mockMvc.perform(post("/v1/admin/change-requests/{id}/reject", requestId)
                        .header(HttpHeaders.AUTHORIZATION, opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("reasonType", "OTHER",
                                "reasonDetail", "제출하신 서류의 발급일이 6개월을 초과했습니다."))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/seller/basic-info/business").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.representativeName").value("김대표"))
                .andExpect(jsonPath("$.changeRequest.status").value("REJECTED"))
                .andExpect(jsonPath("$.changeRequest.rejectReason").value("기타"))
                .andExpect(jsonPath("$.changeRequest.rejectReasonDetail")
                        .value("제출하신 서류의 발급일이 6개월을 초과했습니다."))
                .andExpect(jsonPath("$.changeRequest.cancelable").value(false));

        // 확인 후에는 배너가 닫히고, 같은 항목으로 재요청할 수 있다
        mockMvc.perform(post("/v1/seller/change-requests/{id}/acknowledge", requestId)
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/seller/change-requests/latest")
                        .param("type", "BUSINESS_INFO")
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        changeRequests.request(brandToken, ChangeRequestSteps.businessInfoPayload("서류 재발급 후 재요청",
                        Map.of("REPRESENTATIVE_NAME", "이대표")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestCode").value(requestCode(2)));
    }

    @Test
    @DisplayName("정산 계좌 — 승인 후 정산 계좌 탭의 마스킹 값이 새 계좌로 바뀐다")
    void settlementApprovalFlow() throws Exception {
        long requestId = changeRequests.createSettlement(brandToken, "004", "9876543210", "코코브라운 주식회사");

        mockMvc.perform(get("/v1/seller/basic-info/settlement").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("신한은행"))
                .andExpect(jsonPath("$.maskedAccountNumber").value("******456789"))
                .andExpect(jsonPath("$.changeRequest.status").value("PENDING"))
                .andExpect(jsonPath("$.changeRequest.requestedAccount.bankName").value("KB국민은행"))
                .andExpect(jsonPath("$.changeRequest.requestedAccount.maskedAccountNumber").value("****543210"));

        mockMvc.perform(post("/v1/admin/change-requests/{id}/approve", requestId)
                        .header(HttpHeaders.AUTHORIZATION, opsToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/seller/basic-info/settlement").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("KB국민은행"))
                .andExpect(jsonPath("$.maskedAccountNumber").value("****543210"))
                .andExpect(jsonPath("$.accountHolder").value("코코브라운 주식회사"))
                .andExpect(jsonPath("$.changeRequest.status").value("APPROVED"));

        Seller seller = sellerRepository.findById(brand.seller().getId()).orElseThrow();
        // 통장 사본이 새 증빙으로 교체됐는지까지 확인한다 — 정산 대조의 근거 문서다
        assertThat(seller.getBankbookImageUrl()).isEqualTo(ChangeRequestSteps.EVIDENCE_FILE_URL);
    }
}
