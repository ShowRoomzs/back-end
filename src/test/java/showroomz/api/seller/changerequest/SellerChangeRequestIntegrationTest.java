package showroomz.api.seller.changerequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import showroomz.domain.changerequest.entity.BrandChangeRequest;
import showroomz.domain.changerequest.entity.BrandChangeRequestItem;
import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.support.BrandFixture;
import showroomz.support.ChangeRequestSteps;
import showroomz.support.IntegrationTestSupport;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §15-6·§15-7 변경 요청(M1·M2) 생성·취소·확인 통합 테스트. */
@DisplayName("[통합] 파트너센터 변경 요청")
class SellerChangeRequestIntegrationTest extends IntegrationTestSupport {

    private static final String BRAND_EMAIL = "brand@showroomz.test";
    private static final String BRAND_NAME = "코코브라운";
    private static final String SHINHAN_CODE = "088";
    /** 요청값이 현재값과 같으면 거부되므로(§15-6), 예금주도 현재 값과 다른 문구를 쓴다. */
    private static final String NEW_ACCOUNT_HOLDER = "코코브라운 주식회사";

    private BrandFixture.Brand brand;
    private String token;

    @BeforeEach
    void setUpBrand() {
        brand = fixture.createBrand(BRAND_EMAIL, BRAND_NAME);
        token = sellerToken(brand.seller());
        fixture.createBank(SHINHAN_CODE, "신한은행");
        fixture.createBank("004", "KB국민은행");
    }

    private String requestCode(int sequence) {
        return String.format("CHG-%d-%04d", Year.now().getValue(), sequence);
    }

    private List<BrandChangeRequestItem> itemsOf(long requestId) {
        return inTransaction(() -> {
            BrandChangeRequest request = changeRequestRepository.findById(requestId).orElseThrow();
            return request.getItems().stream()
                    .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                    .toList();
        });
    }

    @Nested
    @DisplayName("사업자 정보 변경 요청 (M1)")
    class BusinessInfoRequest {

        @Test
        @DisplayName("생성 성공 — 현재값은 클라이언트 입력이 아니라 요청 접수 시점의 서버 값으로 스냅샷된다")
        void create() throws Exception {
            Map<String, String> items = new LinkedHashMap<>();
            items.put("REPRESENTATIVE_NAME", "이대표");
            items.put("BUSINESS_ADDRESS", "서울특별시 성동구 성수이로 10, 3층");

            String body = changeRequests.request(token,
                            ChangeRequestSteps.businessInfoPayload("대표자 변경 및 사업장 이전", items))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.requestCode").value(requestCode(1)))
                    .andExpect(jsonPath("$.type").value("BUSINESS_INFO"))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    // 결과 통지는 로그인 이메일로 간다 — tax 이메일이 아니다
                    .andExpect(jsonPath("$.notifyEmail").value(BRAND_EMAIL))
                    .andReturn().getResponse().getContentAsString();

            long requestId = objectMapper.readTree(body).get("requestId").asLong();
            List<BrandChangeRequestItem> stored = itemsOf(requestId);

            assertThat(stored).hasSize(2);
            assertThat(stored.get(0).getFieldKey()).isEqualTo(ChangeRequestField.REPRESENTATIVE_NAME);
            assertThat(stored.get(0).getCurrentValue()).isEqualTo("김대표");
            assertThat(stored.get(0).getRequestedValue()).isEqualTo("이대표");
            assertThat(stored.get(1).getFieldKey()).isEqualTo(ChangeRequestField.BUSINESS_ADDRESS);
            assertThat(stored.get(1).getCurrentValue()).isEqualTo("서울특별시 강남구 테헤란로 123, 4층 401호");

            BrandChangeRequest saved = changeRequestRepository.findById(requestId).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(ChangeRequestStatus.PENDING);
            assertThat(saved.getRequesterName()).isEqualTo("김담당");
            assertThat(saved.getReason()).isEqualTo("대표자 변경 및 사업장 이전");
            assertThat(saved.getEvidenceFileUrl()).isEqualTo(ChangeRequestSteps.EVIDENCE_FILE_URL);
            assertThat(saved.getProcessedAt()).isNull();
        }

        @Test
        @DisplayName("변경 불가 항목은 enum에 없으므로 역직렬화 단계에서 거부된다 — 사업자등록번호")
        void rejectFieldOutsideEnum() throws Exception {
            changeRequests.request(token, ChangeRequestSteps.businessInfoPayload("사업자등록번호 정정",
                            Map.of("BUSINESS_REG_NUMBER", "999-88-77777")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

            assertThat(changeRequestRepository.count()).isZero();
        }

        @Test
        @DisplayName("다른 유형의 항목을 섞으면 400 — M1에 정산 계좌 항목")
        void rejectFieldOfOtherType() throws Exception {
            changeRequests.request(token, ChangeRequestSteps.businessInfoPayload("계좌도 같이 바꿔주세요",
                            Map.of("ACCOUNT_HOLDER", "김대표")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_FIELD_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("현재 값과 같은 값은 요청할 수 없다")
        void rejectUnchangedValue() throws Exception {
            changeRequests.request(token, ChangeRequestSteps.businessInfoPayload("변경 사유",
                            Map.of("REPRESENTATIVE_NAME", "김대표")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_VALUE_UNCHANGED"));

            assertThat(changeRequestRepository.count()).isZero();
        }

        @Test
        @DisplayName("M1은 변경 사유가 필수다")
        void rejectMissingReason() throws Exception {
            changeRequests.request(token, ChangeRequestSteps.businessInfoPayload(null,
                            Map.of("REPRESENTATIVE_NAME", "이대표")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_REASON_REQUIRED"));
        }

        @Test
        @DisplayName("증빙 서류는 두 유형 모두 필수다")
        void rejectMissingEvidence() throws Exception {
            Map<String, Object> payload = ChangeRequestSteps.businessInfoPayload("변경 사유",
                    Map.of("REPRESENTATIVE_NAME", "이대표"));
            payload.put("evidenceFileUrl", "  ");

            changeRequests.request(token, payload)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_EVIDENCE_REQUIRED"));
        }

        @Test
        @DisplayName("변경 항목이 비어 있으면 400")
        void rejectEmptyItems() throws Exception {
            changeRequests.request(token, ChangeRequestSteps.businessInfoPayload("변경 사유", Map.of()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("같은 유형에 검토 중인 요청이 있으면 409 — 중복 요청 차단(§15-7)")
        void rejectDuplicatePending() throws Exception {
            changeRequests.createBusinessInfo(token, "변경 사유", Map.of("REPRESENTATIVE_NAME", "이대표"));

            changeRequests.request(token, ChangeRequestSteps.businessInfoPayload("또 바꿔주세요",
                            Map.of("COMPANY_NAME", "주식회사 오하")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_ALREADY_PENDING"));

            assertThat(changeRequestRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("정산 계좌는 사업자 정보와 별개 유형이라 동시에 검토 중일 수 있다")
        void allowPendingOfDifferentType() throws Exception {
            changeRequests.createBusinessInfo(token, "변경 사유", Map.of("REPRESENTATIVE_NAME", "이대표"));

            changeRequests.request(token,
                            ChangeRequestSteps.settlementPayload("004", "9876543210", NEW_ACCOUNT_HOLDER))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.requestCode").value(requestCode(2)));
        }

        @Test
        @DisplayName("요청 코드는 연도별로 이어 채번한다")
        void requestCodeIsSequentialPerYear() throws Exception {
            changeRequests.createBusinessInfo(token, "변경 사유", Map.of("REPRESENTATIVE_NAME", "이대표"));

            BrandFixture.Brand other = fixture.createBrand("other@showroomz.test", "오하브라운");
            changeRequests.request(sellerToken(other.seller()),
                            ChangeRequestSteps.businessInfoPayload("변경 사유", Map.of("REPRESENTATIVE_NAME", "박대표")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.requestCode").value(requestCode(2)));
        }
    }

    @Nested
    @DisplayName("정산 계좌 변경 요청 (M2)")
    class SettlementAccountRequest {

        @Test
        @DisplayName("생성 성공 — 은행 코드는 은행명으로 변환해 저장하고, 사유는 선택 입력이다")
        void create() throws Exception {
            long requestId = changeRequests.createSettlement(token, "004", "9876543210", NEW_ACCOUNT_HOLDER);

            List<BrandChangeRequestItem> stored = itemsOf(requestId);
            assertThat(stored).hasSize(3);
            assertThat(stored.get(0).getFieldKey()).isEqualTo(ChangeRequestField.BANK_CODE);
            assertThat(stored.get(0).getCurrentValue()).isEqualTo("신한은행");
            assertThat(stored.get(0).getRequestedValue()).isEqualTo("KB국민은행");
            assertThat(stored.get(1).getRequestedValue()).isEqualTo("9876543210");
            assertThat(stored.get(2).getRequestedValue()).isEqualTo(NEW_ACCOUNT_HOLDER);
            assertThat(changeRequestRepository.findById(requestId).orElseThrow().getReason()).isNull();
        }

        @Test
        @DisplayName("계좌번호는 하이픈 없이 숫자 10~16자리만 받는다")
        void rejectMalformedAccountNumber() throws Exception {
            changeRequests.request(token, ChangeRequestSteps.settlementPayload("004", "987-654-3210", NEW_ACCOUNT_HOLDER))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

            changeRequests.request(token, ChangeRequestSteps.settlementPayload("004", "123456789", NEW_ACCOUNT_HOLDER))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("은행·계좌번호·예금주 3개가 모두 있어야 한다")
        void rejectPartialItems() throws Exception {
            Map<String, String> items = new LinkedHashMap<>();
            items.put("ACCOUNT_NUMBER", "9876543210");
            items.put("ACCOUNT_HOLDER", NEW_ACCOUNT_HOLDER);

            changeRequests.request(token, ChangeRequestSteps.payload("SETTLEMENT_ACCOUNT", null, items))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_ITEMS_REQUIRED"));
        }

        @Test
        @DisplayName("존재하지 않는 은행 코드는 404")
        void rejectUnknownBankCode() throws Exception {
            changeRequests.request(token, ChangeRequestSteps.settlementPayload("999", "9876543210", NEW_ACCOUNT_HOLDER))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("BANK_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("요청 가능 항목 조회 (GET /fields)")
    class Fields {

        @Test
        @DisplayName("M1은 6개 항목 — 변경 불가 항목은 애초에 목록에 없다")
        void businessInfoFields() throws Exception {
            mockMvc.perform(get("/v1/seller/change-requests/fields")
                            .param("type", "BUSINESS_INFO")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(6)))
                    .andExpect(jsonPath("$[*].fieldKey", contains(
                            "MARKET_NAME", "REPRESENTATIVE_NAME", "COMPANY_NAME",
                            "BUSINESS_CONDITION", "BUSINESS_ADDRESS", "MAIL_ORDER_REG_NUMBER")))
                    .andExpect(jsonPath("$[1].label").value("대표자명"))
                    .andExpect(jsonPath("$[1].currentValue").value("김대표"))
                    .andExpect(jsonPath("$[4].currentValue").value("서울특별시 강남구 테헤란로 123, 4층 401호"));
        }

        @Test
        @DisplayName("M2는 3개 항목이고 은행의 현재값은 은행명이다")
        void settlementFields() throws Exception {
            mockMvc.perform(get("/v1/seller/change-requests/fields")
                            .param("type", "SETTLEMENT_ACCOUNT")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].fieldKey").value("BANK_CODE"))
                    .andExpect(jsonPath("$[0].currentValue").value("신한은행"))
                    .andExpect(jsonPath("$[1].currentValue").value("110123456789"));
        }
    }

    @Nested
    @DisplayName("배너 조회 (GET /latest)")
    class Banner {

        @Test
        @DisplayName("검토 중 배너 — 변경 항목 라벨과 취소 가능 여부를 함께 내려준다")
        void pendingBanner() throws Exception {
            Map<String, String> items = new LinkedHashMap<>();
            items.put("REPRESENTATIVE_NAME", "이대표");
            items.put("MARKET_NAME", "코코브라운서울");
            changeRequests.createBusinessInfo(token, "리브랜딩", items);

            mockMvc.perform(get("/v1/seller/change-requests/latest")
                            .param("type", "BUSINESS_INFO")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestCode").value(requestCode(1)))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.cancelable").value(true))
                    // 라벨 순서는 서버 enum 순서가 SoT다(브랜드명 → 대표자명)
                    .andExpect(jsonPath("$.changedFieldLabels", contains("브랜드명", "대표자명")))
                    .andExpect(jsonPath("$.processedAt").value(nullValue()))
                    .andExpect(jsonPath("$.rejectReason").value(nullValue()))
                    .andExpect(jsonPath("$.requestedAccount").value(nullValue()));
        }

        @Test
        @DisplayName("정산 계좌 배너에는 요청한 새 계좌가 마스킹되어 함께 붙는다")
        void settlementBanner() throws Exception {
            changeRequests.createSettlement(token, "004", "9876543210", NEW_ACCOUNT_HOLDER);

            mockMvc.perform(get("/v1/seller/change-requests/latest")
                            .param("type", "SETTLEMENT_ACCOUNT")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestedAccount.bankName").value("KB국민은행"))
                    .andExpect(jsonPath("$.requestedAccount.maskedAccountNumber").value("****543210"));
        }

        @Test
        @DisplayName("요청 이력이 없으면 배너 없이 빈 응답")
        void noBanner() throws Exception {
            mockMvc.perform(get("/v1/seller/change-requests/latest")
                            .param("type", "BUSINESS_INFO")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(content().string(""));
        }
    }

    @Nested
    @DisplayName("요청 취소")
    class Cancel {

        @Test
        @DisplayName("검토 중 요청을 취소하면 행은 CANCELED로 보존되고 배너는 사라진다")
        void cancelPending() throws Exception {
            long requestId = changeRequests.createBusinessInfo(token, "변경 사유", Map.of("REPRESENTATIVE_NAME", "이대표"));

            mockMvc.perform(post("/v1/seller/change-requests/{id}/cancel", requestId)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNoContent());

            BrandChangeRequest canceled = changeRequestRepository.findById(requestId).orElseThrow();
            assertThat(canceled.getStatus()).isEqualTo(ChangeRequestStatus.CANCELED);
            assertThat(canceled.getProcessedAt()).isNotNull();

            mockMvc.perform(get("/v1/seller/change-requests/latest")
                            .param("type", "BUSINESS_INFO")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(content().string(""));
        }

        @Test
        @DisplayName("취소하면 같은 유형으로 다시 요청할 수 있다")
        void allowNewRequestAfterCancel() throws Exception {
            long requestId = changeRequests.createBusinessInfo(token, "변경 사유", Map.of("REPRESENTATIVE_NAME", "이대표"));
            mockMvc.perform(post("/v1/seller/change-requests/{id}/cancel", requestId)
                    .header(HttpHeaders.AUTHORIZATION, token));

            changeRequests.request(token, ChangeRequestSteps.businessInfoPayload("다시 요청",
                            Map.of("REPRESENTATIVE_NAME", "박대표")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.requestCode").value(requestCode(2)));
        }

        @Test
        @DisplayName("이미 취소된 요청은 다시 취소할 수 없다")
        void rejectCancelTwice() throws Exception {
            long requestId = changeRequests.createBusinessInfo(token, "변경 사유", Map.of("REPRESENTATIVE_NAME", "이대표"));
            mockMvc.perform(post("/v1/seller/change-requests/{id}/cancel", requestId)
                    .header(HttpHeaders.AUTHORIZATION, token));

            mockMvc.perform(post("/v1/seller/change-requests/{id}/cancel", requestId)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_PENDING"));
        }

        @Test
        @DisplayName("다른 브랜드의 요청은 취소할 수 없다")
        void rejectCancelOfOtherBrand() throws Exception {
            long requestId = changeRequests.createBusinessInfo(token, "변경 사유", Map.of("REPRESENTATIVE_NAME", "이대표"));
            BrandFixture.Brand other = fixture.createBrand("other@showroomz.test", "오하브라운");

            mockMvc.perform(post("/v1/seller/change-requests/{id}/cancel", requestId)
                            .header(HttpHeaders.AUTHORIZATION, sellerToken(other.seller())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_ACCESS_DENIED"));

            assertThat(changeRequestRepository.findById(requestId).orElseThrow().getStatus())
                    .isEqualTo(ChangeRequestStatus.PENDING);
        }

        @Test
        @DisplayName("존재하지 않는 요청은 404")
        void rejectUnknownRequest() throws Exception {
            mockMvc.perform(post("/v1/seller/change-requests/{id}/cancel", 999_999)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("결과 확인 (acknowledge)")
    class Acknowledge {

        @Test
        @DisplayName("아직 검토 중인 요청은 확인 대상이 아니다")
        void rejectAcknowledgeOfPending() throws Exception {
            long requestId = changeRequests.createBusinessInfo(token, "변경 사유", Map.of("REPRESENTATIVE_NAME", "이대표"));

            mockMvc.perform(post("/v1/seller/change-requests/{id}/acknowledge", requestId)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }
}
