package showroomz.api.admin.changerequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.domain.changerequest.entity.BrandChangeRequest;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.support.BrandFixture;
import showroomz.support.ChangeRequestSteps;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §16 어드민 변경 요청 검토·승인·반려 통합 테스트. */
@DisplayName("[통합] 어드민 변경 요청 승인")
class AdminChangeRequestIntegrationTest extends IntegrationTestSupport {

    private static final String BRAND_NAME = "코코브라운";
    private static final String OTHER_BRAND_NAME = "오하브라운";
    private static final String NEW_ACCOUNT_HOLDER = "코코브라운 주식회사";

    private BrandFixture.Brand brand;
    private BrandFixture.Brand otherBrand;
    private Seller operator;
    private String brandToken;
    private String otherBrandToken;
    private String opsToken;

    @BeforeEach
    void setUpActors() {
        brand = fixture.createBrand("brand@showroomz.test", BRAND_NAME);
        otherBrand = fixture.createBrand("other@showroomz.test", OTHER_BRAND_NAME);
        operator = fixture.createAdmin("ops@showroomz.test", "정운영");
        brandToken = sellerToken(brand.seller());
        otherBrandToken = sellerToken(otherBrand.seller());
        opsToken = adminToken(operator);
        fixture.createBank("088", "신한은행");
        fixture.createBank("004", "KB국민은행");
    }

    /** 대표자명 변경 요청 1건. 목록·상세·승인 시나리오의 기본 재료다. */
    private long pendingBusinessInfoRequest() throws Exception {
        Map<String, String> items = new LinkedHashMap<>();
        items.put("REPRESENTATIVE_NAME", "이대표");
        items.put("BUSINESS_ADDRESS", "서울특별시 성동구 성수이로 10, 3층");
        return changeRequests.createBusinessInfo(brandToken, "대표자 변경 및 사업장 이전", items);
    }

    private long pendingSettlementRequest() throws Exception {
        return changeRequests.createSettlement(brandToken, "004", "9876543210", NEW_ACCOUNT_HOLDER);
    }

    private void approve(long requestId) throws Exception {
        mockMvc.perform(post("/v1/admin/change-requests/{id}/approve", requestId)
                        .header(HttpHeaders.AUTHORIZATION, opsToken))
                .andExpect(status().isOk());
    }

    private void reject(long requestId, String reasonType, String reasonDetail) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reasonType", reasonType);
        body.put("reasonDetail", reasonDetail);
        mockMvc.perform(post("/v1/admin/change-requests/{id}/reject", requestId)
                        .header(HttpHeaders.AUTHORIZATION, opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk());
    }

    @Nested
    @DisplayName("목록 (§16-1)")
    class ListView {

        @Test
        @DisplayName("검토 대기가 항상 위 — 그 안에서는 오래 기다린 순, 처리 완료 건은 최신순으로 내려간다")
        void ordering() throws Exception {
            long oldPending = pendingBusinessInfoRequest();
            fixture.backdateRequestedAt(oldPending, LocalDateTime.now().minusDays(3));

            long recentPending = changeRequests.createBusinessInfo(otherBrandToken, "상호 변경",
                    Map.of("COMPANY_NAME", "주식회사 오하"));
            fixture.backdateRequestedAt(recentPending, LocalDateTime.now().minusHours(1));

            long processed = pendingSettlementRequest();
            fixture.backdateRequestedAt(processed, LocalDateTime.now().minusDays(5));
            reject(processed, "BANKBOOK_UNREADABLE", null);

            mockMvc.perform(get("/v1/admin/change-requests")
                            .param("status", "ALL")
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(3)))
                    .andExpect(jsonPath("$.content[*].requestId",
                            contains((int) oldPending, (int) recentPending, (int) processed)))
                    // 경과는 요청 시각 기준, 처리 완료 건은 표시하지 않는다
                    .andExpect(jsonPath("$.content[0].elapsedText").value("3일 0h"))
                    .andExpect(jsonPath("$.content[0].slaExceeded").value(true))
                    .andExpect(jsonPath("$.content[1].elapsedText").value("1h"))
                    .andExpect(jsonPath("$.content[1].slaExceeded").value(false))
                    .andExpect(jsonPath("$.content[2].elapsedText").value(nullValue()))
                    .andExpect(jsonPath("$.content[2].slaExceeded").value(false))
                    .andExpect(jsonPath("$.content[2].processedAt").value(notNullValue()));
        }

        @Test
        @DisplayName("탭별 필터 — 취소 건은 '전체'에서만 보인다")
        void statusFilter() throws Exception {
            long canceled = pendingBusinessInfoRequest();
            mockMvc.perform(post("/v1/seller/change-requests/{id}/cancel", canceled)
                    .header(HttpHeaders.AUTHORIZATION, brandToken));

            long rejected = pendingSettlementRequest();
            reject(rejected, "ACCOUNT_NUMBER_INVALID", null);

            long pending = changeRequests.createBusinessInfo(otherBrandToken, "상호 변경",
                    Map.of("COMPANY_NAME", "주식회사 오하"));

            mockMvc.perform(get("/v1/admin/change-requests").header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].requestId").value(pending));

            mockMvc.perform(get("/v1/admin/change-requests")
                            .param("status", "REJECTED")
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].requestId").value(rejected));

            mockMvc.perform(get("/v1/admin/change-requests")
                            .param("status", "ALL")
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(3)))
                    // 상태별 건수는 탭과 무관하게 항상 네 상태 전체를 담는다
                    .andExpect(jsonPath("$.statusCounts.pending").value(1))
                    .andExpect(jsonPath("$.statusCounts.rejected").value(1))
                    .andExpect(jsonPath("$.statusCounts.canceled").value(1))
                    .andExpect(jsonPath("$.statusCounts.approved").value(0))
                    .andExpect(jsonPath("$.statusCounts.all").value(3));
        }

        @Test
        @DisplayName("브랜드명 부분 일치로 검색한다")
        void keywordSearch() throws Exception {
            pendingBusinessInfoRequest();
            long otherRequest = changeRequests.createBusinessInfo(otherBrandToken, "상호 변경",
                    Map.of("COMPANY_NAME", "주식회사 오하"));

            mockMvc.perform(get("/v1/admin/change-requests")
                            .param("keyword", "오하")
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].requestId").value(otherRequest))
                    .andExpect(jsonPath("$.content[0].brandName").value(OTHER_BRAND_NAME))
                    .andExpect(jsonPath("$.statusCounts.pending").value(1));

            mockMvc.perform(get("/v1/admin/change-requests")
                            .param("keyword", "존재하지않는브랜드")
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.statusCounts.all").value(0));
        }

        @Test
        @DisplayName("페이지 번호는 1부터 세고, 응답 메타에 그대로 실린다")
        void pagination() throws Exception {
            long first = pendingBusinessInfoRequest();
            fixture.backdateRequestedAt(first, LocalDateTime.now().minusDays(2));
            changeRequests.createBusinessInfo(otherBrandToken, "상호 변경", Map.of("COMPANY_NAME", "주식회사 오하"));

            mockMvc.perform(get("/v1/admin/change-requests")
                            .param("page", "1")
                            .param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].requestId").value(first))
                    .andExpect(jsonPath("$.pageInfo.currentPage").value(1))
                    .andExpect(jsonPath("$.pageInfo.totalPages").value(2))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(2))
                    .andExpect(jsonPath("$.pageInfo.limit").value(1))
                    .andExpect(jsonPath("$.pageInfo.hasNext").value(true));

            mockMvc.perform(get("/v1/admin/change-requests")
                            .param("page", "2")
                            .param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.pageInfo.hasNext").value(false));
        }

        @Test
        @DisplayName("GNB 배지용 검토 대기 건수")
        void summary() throws Exception {
            pendingBusinessInfoRequest();
            long rejected = pendingSettlementRequest();
            reject(rejected, "BANKBOOK_MISSING", null);

            mockMvc.perform(get("/v1/admin/change-requests/summary").header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pendingCount").value(1));
        }
    }

    @Nested
    @DisplayName("반려 사유 목록 (§16-5)")
    class RejectReasons {

        @Test
        @DisplayName("사업자 정보는 6종 — 기타만 상세 사유가 필수다")
        void businessInfoReasons() throws Exception {
            mockMvc.perform(get("/v1/admin/change-requests/reject-reasons")
                            .param("type", "BUSINESS_INFO")
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(6)))
                    .andExpect(jsonPath("$[0].code").value("EVIDENCE_MISSING"))
                    .andExpect(jsonPath("$[0].label").value("증빙 서류 미첨부"))
                    .andExpect(jsonPath("$[0].detailRequired").value(false))
                    .andExpect(jsonPath("$[5].code").value("OTHER"))
                    .andExpect(jsonPath("$[5].detailRequired").value(true));
        }

        @Test
        @DisplayName("정산 계좌는 5종 — 입점 심사 사유 목록과 섞이지 않는다")
        void settlementReasons() throws Exception {
            mockMvc.perform(get("/v1/admin/change-requests/reject-reasons")
                            .param("type", "SETTLEMENT_ACCOUNT")
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(5)))
                    .andExpect(jsonPath("$[*].code", contains(
                            "BANKBOOK_MISSING", "HOLDER_NAME_MISMATCH", "ACCOUNT_NUMBER_INVALID",
                            "BANKBOOK_UNREADABLE", "OTHER")));
        }
    }

    @Nested
    @DisplayName("상세 대조표 (§16-2)")
    class Detail {

        @Test
        @DisplayName("사업자 정보 — 요청하지 않은 항목도 라이브 현재값으로 8행 전부 노출하고, 사업자등록번호는 잠긴 행이다")
        void businessInfoDetail() throws Exception {
            long requestId = pendingBusinessInfoRequest();

            mockMvc.perform(get("/v1/admin/change-requests/{id}", requestId)
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.brandName").value(BRAND_NAME))
                    .andExpect(jsonPath("$.marketId").value(brand.marketId()))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.requesterName").value("김담당"))
                    .andExpect(jsonPath("$.reason").value("대표자 변경 및 사업장 이전"))
                    .andExpect(jsonPath("$.changedFieldLabels", contains("대표자명", "사업장 주소")))
                    .andExpect(jsonPath("$.diff", hasSize(8)))
                    .andExpect(jsonPath("$.diff[*].fieldKey", contains(
                            "BUSINESS_TYPE", "MARKET_NAME", "REPRESENTATIVE_NAME", "COMPANY_NAME",
                            "BUSINESS_REG_NUMBER", "BUSINESS_CONDITION", "BUSINESS_ADDRESS",
                            "MAIL_ORDER_REG_NUMBER")))
                    // 요청 항목: 스냅샷된 현재값 + 요청값
                    .andExpect(jsonPath("$.diff[2].label").value("대표자명"))
                    .andExpect(jsonPath("$.diff[2].currentValue").value("김대표"))
                    .andExpect(jsonPath("$.diff[2].requestedValue").value("이대표"))
                    .andExpect(jsonPath("$.diff[2].changed").value(true))
                    .andExpect(jsonPath("$.diff[2].locked").value(false))
                    // 미요청 항목: 요청값 없음
                    .andExpect(jsonPath("$.diff[1].currentValue").value(BRAND_NAME))
                    .andExpect(jsonPath("$.diff[1].requestedValue").value(nullValue()))
                    .andExpect(jsonPath("$.diff[1].changed").value(false))
                    // 변경 불가 항목: 행은 있고 잠겨 있다
                    .andExpect(jsonPath("$.diff[4].label").value("사업자등록번호"))
                    .andExpect(jsonPath("$.diff[4].currentValue").value("123-45-67890"))
                    .andExpect(jsonPath("$.diff[4].locked").value(true))
                    .andExpect(jsonPath("$.diff[4].changed").value(false))
                    .andExpect(jsonPath("$.diff[0].label").value("사업자 유형"))
                    .andExpect(jsonPath("$.diff[0].currentValue").value("일반과세자"))
                    .andExpect(jsonPath("$.evidence.documentLabel").value("사업자등록증"))
                    .andExpect(jsonPath("$.evidence.fileName").value(ChangeRequestSteps.EVIDENCE_FILE_NAME))
                    .andExpect(jsonPath("$.evidence.extension").value("jpg"))
                    .andExpect(jsonPath("$.evidence.fileSizeBytes").value(ChangeRequestSteps.EVIDENCE_FILE_SIZE))
                    .andExpect(jsonPath("$.evidence.fileUrl").value(ChangeRequestSteps.EVIDENCE_FILE_URL))
                    .andExpect(jsonPath("$.referenceItems", hasSize(2)))
                    .andExpect(jsonPath("$.referenceItems[1].label").value("사업자등록번호"))
                    .andExpect(jsonPath("$.holderCheck").value(nullValue()))
                    .andExpect(jsonPath("$.history", hasSize(1)))
                    .andExpect(jsonPath("$.history[0].event").value("REQUESTED"))
                    .andExpect(jsonPath("$.history[0].actorLabel").value("브랜드 파트너센터"))
                    .andExpect(jsonPath("$.prevRequestId").value(nullValue()))
                    .andExpect(jsonPath("$.nextRequestId").value(nullValue()));
        }

        @Test
        @DisplayName("정산 계좌 — 예금주와 상호 불일치를 표시하고, 계좌번호는 마스킹하지 않는다")
        void settlementDetail() throws Exception {
            long requestId = pendingSettlementRequest();

            mockMvc.perform(get("/v1/admin/change-requests/{id}", requestId)
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.diff", hasSize(3)))
                    .andExpect(jsonPath("$.diff[*].fieldKey",
                            contains("BANK_CODE", "ACCOUNT_NUMBER", "ACCOUNT_HOLDER")))
                    .andExpect(jsonPath("$.diff[0].currentValue").value("신한은행"))
                    .andExpect(jsonPath("$.diff[0].requestedValue").value("KB국민은행"))
                    // §16-7 어드민은 통장 사본 대조가 목적이므로 전체 노출한다
                    .andExpect(jsonPath("$.diff[1].currentValue").value("110123456789"))
                    .andExpect(jsonPath("$.diff[1].requestedValue").value("9876543210"))
                    .andExpect(jsonPath("$.evidence.documentLabel").value("통장 사본"))
                    .andExpect(jsonPath("$.referenceItems", hasSize(1)))
                    .andExpect(jsonPath("$.holderCheck.requestedHolder").value(NEW_ACCOUNT_HOLDER))
                    .andExpect(jsonPath("$.holderCheck.companyName").value("주식회사 " + BRAND_NAME))
                    .andExpect(jsonPath("$.holderCheck.mismatch").value(true))
                    .andExpect(jsonPath("$.reason").value(nullValue()));
        }

        @Test
        @DisplayName("예금주가 사업자등록증 상호와 같으면 불일치 표시가 꺼진다")
        void holderCheckMatched() throws Exception {
            long requestId = changeRequests.createSettlement(brandToken, "004", "9876543210",
                    "주식회사 " + BRAND_NAME + "홀딩스");
            Seller seller = sellerRepository.findById(brand.seller().getId()).orElseThrow();
            seller.setCompanyName("주식회사 " + BRAND_NAME + "홀딩스");
            sellerRepository.save(seller);

            mockMvc.perform(get("/v1/admin/change-requests/{id}", requestId)
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.holderCheck.mismatch").value(false));
        }

        @Test
        @DisplayName("이전·다음 요청 id는 현재 탭의 정렬을 따른다")
        void prevAndNextFollowTabOrdering() throws Exception {
            long first = pendingBusinessInfoRequest();
            fixture.backdateRequestedAt(first, LocalDateTime.now().minusDays(3));
            long second = pendingSettlementRequest();
            fixture.backdateRequestedAt(second, LocalDateTime.now().minusDays(2));
            long third = changeRequests.createBusinessInfo(otherBrandToken, "상호 변경",
                    Map.of("COMPANY_NAME", "주식회사 오하"));
            fixture.backdateRequestedAt(third, LocalDateTime.now().minusDays(1));

            mockMvc.perform(get("/v1/admin/change-requests/{id}", second)
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.prevRequestId").value(first))
                    .andExpect(jsonPath("$.nextRequestId").value(third));
        }

        @Test
        @DisplayName("존재하지 않는 요청은 404")
        void unknownRequest() throws Exception {
            mockMvc.perform(get("/v1/admin/change-requests/{id}", 999_999)
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("승인 (§16-4)")
    class Approve {

        @Test
        @DisplayName("승인하면 요청값이 SELLER에 실제로 반영되고, 처리자·처리 시각이 감사 기록으로 남는다")
        void approveBusinessInfo() throws Exception {
            long requestId = pendingBusinessInfoRequest();

            mockMvc.perform(post("/v1/admin/change-requests/{id}/approve", requestId)
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestId").value(requestId))
                    .andExpect(jsonPath("$.brandName").value(BRAND_NAME))
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.processedAt").value(notNullValue()))
                    .andExpect(jsonPath("$.rejectReason").value(nullValue()));

            Seller seller = sellerRepository.findById(brand.seller().getId()).orElseThrow();
            assertThat(seller.getRepresentativeName()).isEqualTo("이대표");
            // 주소는 요청값 전체를 businessAddress에 넣고 상세주소를 비운다(§A-1)
            assertThat(seller.getBusinessAddress()).isEqualTo("서울특별시 성동구 성수이로 10, 3층");
            assertThat(seller.getDetailAddress()).isNull();

            BrandChangeRequest approved = changeRequestRepository.findById(requestId).orElseThrow();
            assertThat(approved.getStatus()).isEqualTo(ChangeRequestStatus.APPROVED);
            assertThat(approved.getProcessedBy()).isEqualTo(operator.getId());
            assertThat(approved.getProcessedAt()).isNotNull();

            verify(mailService).sendChangeRequestApprovedEmail(
                    eq("brand@showroomz.test"), eq(approved.getRequestCode()),
                    eq("대표자명, 사업장 주소"), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("정산 계좌 승인 시 통장 사본도 증빙 파일로 교체한다 — 옛 사본이 남으면 다음 정산 대조가 어긋난다")
        void approveSettlementReplacesBankbook() throws Exception {
            long requestId = pendingSettlementRequest();

            approve(requestId);

            Seller seller = sellerRepository.findById(brand.seller().getId()).orElseThrow();
            assertThat(seller.getBankName()).isEqualTo("KB국민은행");
            assertThat(seller.getAccountNumber()).isEqualTo("9876543210");
            assertThat(seller.getAccountHolder()).isEqualTo(NEW_ACCOUNT_HOLDER);
            assertThat(seller.getBankbookImageUrl()).isEqualTo(ChangeRequestSteps.EVIDENCE_FILE_URL);
        }

        @Test
        @DisplayName("브랜드명은 승인 시점에 다시 중복 검사한다 — 그 사이 다른 브랜드가 쓰고 있으면 반영하지 않는다")
        void rejectApproveOnDuplicateMarketName() throws Exception {
            long requestId = changeRequests.createBusinessInfo(brandToken, "브랜드명 변경",
                    Map.of("MARKET_NAME", OTHER_BRAND_NAME));

            mockMvc.perform(post("/v1/admin/change-requests/{id}/approve", requestId)
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_MARKET_NAME"));

            Market market = marketRepository.findById(brand.marketId()).orElseThrow();
            assertThat(market.getMarketName()).isEqualTo(BRAND_NAME);
            assertThat(changeRequestRepository.findById(requestId).orElseThrow().getStatus())
                    .isEqualTo(ChangeRequestStatus.PENDING);
            verify(mailService, never()).sendChangeRequestApprovedEmail(anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("검토 대기가 아닌 요청은 다시 처리할 수 없다")
        void rejectApproveTwice() throws Exception {
            long requestId = pendingBusinessInfoRequest();
            approve(requestId);

            mockMvc.perform(post("/v1/admin/change-requests/{id}/approve", requestId)
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_PENDING"));
        }

        @Test
        @DisplayName("브랜드가 취소한 요청은 승인할 수 없다")
        void rejectApproveOfCanceled() throws Exception {
            long requestId = pendingBusinessInfoRequest();
            mockMvc.perform(post("/v1/seller/change-requests/{id}/cancel", requestId)
                    .header(HttpHeaders.AUTHORIZATION, brandToken));

            mockMvc.perform(post("/v1/admin/change-requests/{id}/approve", requestId)
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_PENDING"));
        }

        @Test
        @DisplayName("승인 이력에는 처리한 운영자 이름이 남는다")
        void historyKeepsOperatorName() throws Exception {
            long requestId = pendingBusinessInfoRequest();
            approve(requestId);

            mockMvc.perform(get("/v1/admin/change-requests/{id}", requestId)
                            .param("status", "APPROVED")
                            .header(HttpHeaders.AUTHORIZATION, opsToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.history", hasSize(2)))
                    .andExpect(jsonPath("$.history[1].event").value("APPROVED"))
                    .andExpect(jsonPath("$.history[1].actorLabel").value("정운영"))
                    .andExpect(jsonPath("$.elapsedText").value(nullValue()));
        }
    }

    @Nested
    @DisplayName("반려 (§16-5)")
    class Reject {

        @Test
        @DisplayName("반려하면 정형 사유 문구가 가공 없이 응답·메일에 실리고 요청값은 반영되지 않는다")
        void rejectWithReason() throws Exception {
            long requestId = pendingBusinessInfoRequest();

            mockMvc.perform(post("/v1/admin/change-requests/{id}/reject", requestId)
                            .header(HttpHeaders.AUTHORIZATION, opsToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("reasonType", "REASON_INSUFFICIENT"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectReason").value("변경 사유 불충분"))
                    .andExpect(jsonPath("$.rejectReasonDetail").value(nullValue()));

            BrandChangeRequest rejected = changeRequestRepository.findById(requestId).orElseThrow();
            assertThat(rejected.getStatus()).isEqualTo(ChangeRequestStatus.REJECTED);
            // 저장은 enum name으로, 표시는 description으로 — 문구가 바뀌어도 데이터는 안전하다
            assertThat(rejected.getRejectReason()).isEqualTo("REASON_INSUFFICIENT");
            assertThat(rejected.getProcessedBy()).isEqualTo(operator.getId());
            assertThat(sellerRepository.findById(brand.seller().getId()).orElseThrow().getRepresentativeName())
                    .isEqualTo("김대표");

            verify(mailService).sendChangeRequestRejectedEmail(
                    eq("brand@showroomz.test"), eq(rejected.getRequestCode()), any(LocalDateTime.class),
                    eq("변경 사유 불충분"), eq(null));
        }

        @Test
        @DisplayName("기타 사유를 골랐으면 상세 사유가 필수다")
        void requireDetailForOther() throws Exception {
            long requestId = pendingBusinessInfoRequest();

            mockMvc.perform(post("/v1/admin/change-requests/{id}/reject", requestId)
                            .header(HttpHeaders.AUTHORIZATION, opsToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("reasonType", "OTHER"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_REJECT_DETAIL_REQUIRED"));

            assertThat(changeRequestRepository.findById(requestId).orElseThrow().getStatus())
                    .isEqualTo(ChangeRequestStatus.PENDING);
        }

        @Test
        @DisplayName("기타 사유 + 상세 사유는 그대로 저장된다")
        void rejectWithOtherDetail() throws Exception {
            long requestId = pendingBusinessInfoRequest();
            String detail = "제출하신 서류의 발급일이 6개월을 초과했습니다.";

            mockMvc.perform(post("/v1/admin/change-requests/{id}/reject", requestId)
                            .header(HttpHeaders.AUTHORIZATION, opsToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("reasonType", "OTHER", "reasonDetail", detail))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rejectReason").value("기타"))
                    .andExpect(jsonPath("$.rejectReasonDetail").value(detail));

            assertThat(changeRequestRepository.findById(requestId).orElseThrow().getRejectReasonDetail())
                    .isEqualTo(detail);
        }

        @Test
        @DisplayName("요청 유형에 없는 사유로는 반려할 수 없다 — 사업자 정보에 통장 사본 사유")
        void rejectReasonTypeMismatch() throws Exception {
            long requestId = pendingBusinessInfoRequest();

            mockMvc.perform(post("/v1/admin/change-requests/{id}/reject", requestId)
                            .header(HttpHeaders.AUTHORIZATION, opsToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("reasonType", "BANKBOOK_MISSING"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_REJECT_REASON_TYPE_MISMATCH"));
        }

        @Test
        @DisplayName("반려 사유 없이는 반려할 수 없다")
        void requireReasonType() throws Exception {
            long requestId = pendingBusinessInfoRequest();

            mockMvc.perform(post("/v1/admin/change-requests/{id}/reject", requestId)
                            .header(HttpHeaders.AUTHORIZATION, opsToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("접근 권한")
    class Authorization {

        @Test
        @DisplayName("브랜드 토큰으로는 어드민 API에 접근할 수 없다")
        void rejectSellerToken() throws Exception {
            long requestId = pendingBusinessInfoRequest();

            mockMvc.perform(get("/v1/admin/change-requests").header(HttpHeaders.AUTHORIZATION, brandToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/v1/admin/change-requests/{id}/approve", requestId)
                            .header(HttpHeaders.AUTHORIZATION, brandToken))
                    .andExpect(status().isForbidden());

            assertThat(changeRequestRepository.findById(requestId).orElseThrow().getStatus())
                    .isEqualTo(ChangeRequestStatus.PENDING);
        }

        @Test
        @DisplayName("토큰이 없으면 401")
        void rejectAnonymous() throws Exception {
            mockMvc.perform(get("/v1/admin/change-requests"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
