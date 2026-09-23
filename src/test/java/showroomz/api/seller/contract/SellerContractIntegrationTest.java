package showroomz.api.seller.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.contract.dto.ContractUpdateRequest;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.contract.entity.ContractClause;
import showroomz.domain.contract.entity.ContractClauseVersion;
import showroomz.domain.contract.repository.ContractClauseVersionRepository;
import showroomz.domain.contract.type.ContractClauseVersionStatus;
import showroomz.domain.contract.type.ContractCloseReasonCode;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.contract.type.SecondaryUsePeriodType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.user.entity.Users;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.support.BrandFixture;
import showroomz.support.IntegrationTestSupport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §25·§26 파트너센터 계약 관리 통합 테스트 — 초안 생성 → 임시저장 → 검증 → 검토 요청 → 요청 취소까지 태운다.
 *
 * <p>여기서 지키려는 것은 설계서가 "서버가 집행한다"고 못박은 지점들이다 —
 * 편집 잠금(0-4) · 필수/규칙 구분(2-5) · 경고 대조(2-3) · 되돌림 2경로 분리(3-2) · 낙관적 락(3-4).
 */
@DisplayName("[통합] 파트너센터 계약 관리")
class SellerContractIntegrationTest extends IntegrationTestSupport {

    private static final String CONTRACTS = "/v1/seller/contracts";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;
    @Autowired
    private ConnectionRepository connectionRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ContractClauseVersionRepository clauseVersionRepository;

    private BrandFixture.Brand brand;
    private String brandToken;
    private Creator counterparty;
    private Product serum;
    private Product cream;

    @BeforeEach
    void setUpActors() {
        brand = fixture.createBrand("brand@showroomz.test", "글로우랩");
        brandToken = sellerToken(brand.seller());

        counterparty = createConnectedCreator("글로우_지민");
        serum = createProduct("수분진정 세럼 30ml", 32_000);
        cream = createProduct("수분진정 크림 50ml", 24_000);

        seedClauseVersion();
    }

    @Test
    @DisplayName("초안은 공구명 없이 만들어지고 목록에 작성중으로 잡힌다 — 서버는 (공구명 미입력)을 지어내지 않는다")
    void createsEmptyDraft() throws Exception {
        long contractId = createDraft();

        mockMvc.perform(get(CONTRACTS).header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].contractId").value(contractId))
                .andExpect(jsonPath("$.content[0].title").doesNotExist())
                .andExpect(jsonPath("$.content[0].contractNumber").doesNotExist())
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.content[0].statusLabel").value("작성중"))
                .andExpect(jsonPath("$.content[0].statusTone").value("NEUTRAL"))
                .andExpect(jsonPath("$.content[0].entryMode").value("EDIT"));

        mockMvc.perform(get(CONTRACTS + "/summary").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tabCounts.ALL").value(1))
                .andExpect(jsonPath("$.tabCounts.DRAFT").value(1))
                // 작성중은 「내가 조치해야 하는」 건수가 아니다 — 배지는 검토 반려와 B4c만 센다.
                .andExpect(jsonPath("$.actionRequiredCount").value(0));
    }

    @Test
    @DisplayName("임시저장은 필수값이 비어 있어도 통과한다 — 「필수」는 검토 요청 시점에만 본다")
    void savesPartialDraft() throws Exception {
        long contractId = createDraft();

        ContractUpdateRequest request = new ContractUpdateRequest(
                0L, counterparty.getId(), "가을 앰플 신제품 공구",
                null, null, null, null, null,
                null, null, null, null,
                null, null, null, null, null,
                List.of());

        mockMvc.perform(put(CONTRACTS + "/" + contractId)
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("가을 앰플 신제품 공구"))
                .andExpect(jsonPath("$.counterparty.showroomName").value("글로우_지민"))
                .andExpect(jsonPath("$.counterparty.fixed").value(false))
                // 원천징수는 상대 계정 정보에서 자동으로 따라온다(§25-5-6).
                .andExpect(jsonPath("$.settlement.withholdingType").value("WITHHOLDING_3_3"))
                .andExpect(jsonPath("$.settlement.platformFeeRate").value(2))
                // PG 수수료율은 자문 회신 전이라 0이 아니라 null이어야 한다.
                .andExpect(jsonPath("$.settlement.pgFeeRate").doesNotExist());
    }

    @Test
    @DisplayName("임시저장은 형식 위반만 막는다 — 10원 단위가 아닌 공구가는 H2로 되돌린다")
    void rejectsMalformedPriceOnSave() throws Exception {
        long contractId = createDraft();

        RequestBuilder request = fullRequest(0L)
                .withItems(List.of(item(serum.getProductId(), 28_005, "15.0", 300)));

        mockMvc.perform(put(CONTRACTS + "/" + contractId)
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request.request())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.hardViolations[0].code").value("H2"))
                .andExpect(jsonPath("$.hardViolations[0].field").value("items[0].groupBuyPrice"));
    }

    @Test
    @DisplayName("검증은 미입력(REQUIRED)과 규칙 위반(RULE)을 갈라 내린다 — 빈 폼에 에러 문구가 뜨면 안 된다")
    void separatesRequiredFromRuleViolations() throws Exception {
        long contractId = createDraft();

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSubmit").value(false))
                // 빈 초안의 위반은 전부 미입력이다 — RULE이 하나라도 섞이면 FE가 문구를 뿌린다.
                .andExpect(jsonPath("$.hardViolations[?(@.kind == 'RULE')]").isEmpty())
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'H7')]").exists());
    }

    @Test
    @DisplayName("공구가가 정가를 넘으면 H1이 항목 index와 함께 돌아온다")
    void detectsPriceOverRegular() throws Exception {
        long contractId = createDraft();
        saveOk(contractId, fullRequest(0L)
                .withItems(List.of(item(serum.getProductId(), 40_000, "15.0", 300))));

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSubmit").value(false))
                .andExpect(jsonPath("$.hardViolations[*].code", hasItem("H1")))
                .andExpect(jsonPath("$.hardViolations[*].field", hasItem("items[0].groupBuyPrice")));
    }

    @Test
    @DisplayName("예상 리워드는 저장하지 않고 1원 단위 버림으로 계산해 내린다")
    void derivesUnitRewardByTruncation() throws Exception {
        long contractId = createDraft();
        // 28,010 × 15.5% = 4,341.55 → 4,341원(버림)
        saveOk(contractId, fullRequest(0L)
                .withItems(List.of(item(serum.getProductId(), 28_010, "15.5", 300))));

        mockMvc.perform(get(CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitReward").value(4341));
    }

    @Test
    @DisplayName("상품을 바꾼 행은 공구가·리워드율·최소 물량이 초기화된다")
    void resetsNegotiatedValuesWhenProductChanges() throws Exception {
        long contractId = createDraft();
        String saved = saveOk(contractId, fullRequest(0L)
                .withItems(List.of(item(serum.getProductId(), 28_000, "15.0", 300))));

        long itemId = readLong(saved, "$.items[0].contractItemId");
        long version = readLong(saved, "$.version");

        // 같은 행(contractItemId 유지)에서 상품만 바꾼다 — 값은 앞 상품 기준이라 살려두면 안 된다.
        ContractUpdateRequest.Item changed = new ContractUpdateRequest.Item(
                itemId, cream.getProductId(), 28_000, new BigDecimal("15.0"), 300);

        mockMvc.perform(put(CONTRACTS + "/" + contractId)
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(fullRequest(version).withItems(List.of(changed)).request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("수분진정 크림 50ml"))
                .andExpect(jsonPath("$.items[0].regularPrice").value(24_000))
                .andExpect(jsonPath("$.items[0].groupBuyPrice").doesNotExist())
                .andExpect(jsonPath("$.items[0].rewardRate").doesNotExist())
                .andExpect(jsonPath("$.items[0].minQuantity").doesNotExist());
    }

    @Test
    @DisplayName("다른 곳에서 먼저 저장됐으면 409다 — 탭 두 개로 같은 계약을 열 수 있다")
    void rejectsStaleVersion() throws Exception {
        long contractId = createDraft();
        saveOk(contractId, fullRequest(0L).withItems(List.of()));

        mockMvc.perform(put(CONTRACTS + "/" + contractId)
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(fullRequest(0L).withItems(List.of()).request())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_MODIFIED_ELSEWHERE"));
    }

    @Test
    @DisplayName("검토 요청은 계약번호를 붙이고 편집을 잠근다 — 잠금은 화면이 아니라 서버가 집행한다")
    void requestReviewAssignsNumberAndLocksEditing() throws Exception {
        long contractId = createDraft();
        String saved = saveOk(contractId, validContract(0L));
        long version = readLong(saved, "$.version");

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/review-request")
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acknowledgedWarnings\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_PENDING"))
                .andExpect(jsonPath("$.contractNumber").value(
                        org.hamcrest.Matchers.matchesPattern("CTR-\\d{8}-\\d{3}")))
                .andExpect(jsonPath("$.review.requestedAt").exists())
                .andExpect(jsonPath("$.permissions.canEdit").value(false))
                .andExpect(jsonPath("$.permissions.canCancelRequest").value(true))
                // 검토 대기 — [요청 취소](작성중으로)와 [계약 취소](종결) 둘 다 열려 있다. 서명 요청 발송 전이다.
                .andExpect(jsonPath("$.permissions.canCancel").value(true))
                .andExpect(jsonPath("$.history[?(@.eventType == 'REVIEW_REQUESTED')]").exists());

        mockMvc.perform(put(CONTRACTS + "/" + contractId)
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(validContract(version + 1).request())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_EDIT_LOCKED"));

        mockMvc.perform(delete(CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("확인한 경고가 서버 판정과 다르면 409로 되돌려 모달을 다시 띄우게 한다")
    void rejectsWarningMismatch() throws Exception {
        long contractId = createDraft();
        // 리워드율 45% → W2. 확인 없이 요청하면 통과시키지 않는다.
        saveOk(contractId, validContract(0L)
                .withItems(List.of(item(serum.getProductId(), 28_000, "45.0", 300))));

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/review-request")
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acknowledgedWarnings\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_WARNING_MISMATCH"));

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSubmit").value(true))
                .andExpect(jsonPath("$.warnings[0].code").value("W2"));

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/review-request")
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acknowledgedWarnings\":[\"W2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_PENDING"));
    }

    @Test
    @DisplayName("요청 취소는 작성중으로 되돌리되 종결이 아니고, 계약번호는 반납하지 않는다")
    void cancelReviewRequestReturnsToDraft() throws Exception {
        long contractId = createDraft();
        saveOk(contractId, validContract(0L));
        String requested = requestReview(contractId);
        String contractNumber = readString(requested, "$.contractNumber");

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/review-request/cancel")
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.contractNumber").value(contractNumber))
                .andExpect(jsonPath("$.review.requestedAt").doesNotExist())
                .andExpect(jsonPath("$.permissions.canEdit").value(true))
                .andExpect(jsonPath("$.closure.closedAt").doesNotExist());
    }

    @Test
    @DisplayName("검토 대기에서 [계약 취소]는 종결이다 — 작성중으로 되돌리는 [요청 취소]와 갈라져 있다")
    void cancelContractClosesWhileReviewPending() throws Exception {
        long contractId = createDraft();
        saveOk(contractId, validContract(0L));
        requestReview(contractId);

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new java.util.LinkedHashMap<>(java.util.Map.of(
                                "reasonCode", ContractCloseReasonCode.SCHEDULE_CHANGE.name())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.permissions.canEdit").value(false))
                .andExpect(jsonPath("$.permissions.canCancelRequest").value(false));

        // 종결된 계약은 [요청 취소]로 작성중에 되살릴 수 없다.
        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/review-request/cancel")
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("진행 중 계약은 같은 조건으로 다시 작성할 수 없다")
    void rejectsDuplicateWhileInProgress() throws Exception {
        long contractId = createDraft();

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/duplicate")
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_DUPLICABLE"));
    }

    @Test
    @DisplayName("남의 브랜드 계약은 403이다")
    void blocksOtherBrandsContract() throws Exception {
        long contractId = createDraft();

        BrandFixture.Brand other = fixture.createBrand("other@showroomz.test", "아더랩");
        mockMvc.perform(get(CONTRACTS + "/" + contractId)
                        .header(HttpHeaders.AUTHORIZATION, sellerToken(other.seller())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_OWNED_BY_SELLER"));
    }

    @Test
    @DisplayName("표준 조항은 요약과 전문을 같은 행에서 내려주고, 문안 미확정 조항은 전문이 비어 있다")
    void exposesClauseSummaryAndFullText() throws Exception {
        mockMvc.perform(get(CONTRACTS + "/clauses").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value("1.0"))
                .andExpect(jsonPath("$.clauses[0].code").value("PRICE_POLICY"))
                .andExpect(jsonPath("$.clauses[0].fullTitle").value("제3조 최저가 정책"))
                // 문안이 확정되지 않은 조항은 전문이 비어 있다 — 없는 문안을 지어내지 않는다.
                .andExpect(jsonPath("$.clauses[1].code").value("EXCLUSIVE_SALE"))
                .andExpect(jsonPath("$.clauses[1].summaryDescription").exists())
                .andExpect(jsonPath("$.clauses[1].fullTitle").doesNotExist())
                .andExpect(jsonPath("$.clauses[1].fullBody").doesNotExist());
    }

    @Test
    @DisplayName("작성 폼 선택지는 연결됨 상대와 진열 상품만 담는다")
    void formSourcesOnlyContainConnectedAndDisplayed() throws Exception {
        Product hidden = createProduct("단종 토너 200ml", 21_000);
        hidden.setDisplayStatus(ProductDisplayStatus.HIDDEN);
        productRepository.save(hidden);

        mockMvc.perform(get(CONTRACTS + "/form-sources").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counterparties[0].showroomName").value("글로우_지민"))
                .andExpect(jsonPath("$.products[*].productName", not(hasItem("단종 토너 200ml"))))
                .andExpect(jsonPath("$.products[*].productName", hasItem("수분진정 세럼 30ml")))
                .andExpect(jsonPath("$.products[*].regularPrice", hasItem(32_000)));
    }

    // ------------------------------------------------------------------ 요청 헬퍼

    private long createDraft() throws Exception {
        String body = mockMvc.perform(post(CONTRACTS)
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readLong(body, "$.contractId");
    }

    private String saveOk(long contractId, RequestBuilder builder) throws Exception {
        return mockMvc.perform(put(CONTRACTS + "/" + contractId)
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(builder.request())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String requestReview(long contractId) throws Exception {
        return mockMvc.perform(post(CONTRACTS + "/" + contractId + "/review-request")
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acknowledgedWarnings\":[]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** 하드 검증을 하나도 건드리지 않는 최소 구성 — 여기서 한 항목만 바꿔 각 규칙을 찌른다. */
    private RequestBuilder validContract(long version) {
        return fullRequest(version)
                .withItems(List.of(item(serum.getProductId(), 28_000, "15.0", 300)));
    }

    private RequestBuilder fullRequest(long version) {
        LocalDateTime startAt = LocalDateTime.now().plusDays(10).withNano(0);
        return new RequestBuilder(new ContractUpdateRequest(
                version, counterparty.getId(), "가을 앰플 신제품 공구",
                startAt, startAt.plusDays(9),
                0, FixedFeeTrigger.POST_REGISTERED, false,
                1, 1, 0, startAt.plusDays(12).toLocalDate(),
                true, SecondaryUsePeriodType.FIXED, 12, false, null,
                List.of()));
    }

    private ContractUpdateRequest.Item item(Long productId, int groupBuyPrice, String rewardRate, int minQuantity) {
        return new ContractUpdateRequest.Item(
                null, productId, groupBuyPrice, new BigDecimal(rewardRate), minQuantity);
    }

    /** 레코드는 복사 생성자가 없어 항목만 갈아 끼우는 헬퍼를 둔다. */
    private record RequestBuilder(ContractUpdateRequest request) {

        RequestBuilder withItems(List<ContractUpdateRequest.Item> items) {
            ContractUpdateRequest r = request;
            return new RequestBuilder(new ContractUpdateRequest(
                    r.version(), r.creatorId(), r.title(), r.groupBuyStartAt(), r.groupBuyEndAt(),
                    r.fixedFeeAmount(), r.fixedFeeTrigger(), r.fixedFeeNoticeAgreed(),
                    r.contentFeedCount(), r.contentReelsCount(), r.contentStoryCount(), r.contentDueDate(),
                    r.secondaryUseAllowed(), r.secondaryUsePeriodType(), r.secondaryUseMonths(),
                    r.brandPreReview(), r.note(), items));
        }
    }

    private long readLong(String json, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.parse(json).read(path, Number.class).longValue();
    }

    private String readString(String json, String path) {
        return com.jayway.jsonpath.JsonPath.parse(json).read(path, String.class);
    }

    // ------------------------------------------------------------------ 픽스처

    private Creator createConnectedCreator(String showroomName) {
        LocalDateTime now = LocalDateTime.now();
        Users owner = userRepository.save(new Users(
                "creator-" + showroomName, showroomName, showroomName + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now));

        Creator creator = creatorRepository.save(Creator.builder()
                .user(owner)
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/" + showroomName)
                .accountId(showroomName)
                .followerCount(12_000)
                .businessEmail("biz@showroomz.test")
                .showroomName(showroomName)
                .businessType(CreatorBusinessType.INDIVIDUAL)
                .build());

        Connection connection = Connection.requestPair(brand.market(), creator);
        connection.markConnected();
        connectionRepository.save(connection);

        return creator;
    }

    private Product createProduct(String name, int regularPrice) {
        Category category = new Category();
        category.setName("뷰티 " + name);
        categoryRepository.save(category);

        Product product = new Product();
        product.setMarket(brand.market());
        product.setCategory(category);
        product.setName(name);
        product.setRegularPrice(regularPrice);
        product.setSalePrice(regularPrice);
        product.setDisplayStatus(ProductDisplayStatus.DISPLAY);
        return productRepository.save(product);
    }

    /** Flyway가 꺼진 통합 테스트 프로필에서는 V120의 조항 seed가 없다 — 최소 버전을 직접 적재한다. */
    private void seedClauseVersion() {
        ContractClauseVersion version = ContractClauseVersion.builder()
                .versionNumber("1.0")
                .effectiveDate(LocalDate.now().minusDays(1))
                .status(ContractClauseVersionStatus.EFFECTIVE)
                .clauses(new ArrayList<>())
                .build();

        version.getClauses().add(ContractClause.builder()
                .clauseVersion(version)
                .code("PRICE_POLICY")
                .sortOrder(1)
                .summaryTitle("가격 정책")
                .summaryDescription("공구 기간 중 타 채널 최저가 준수")
                .fullTitle("제3조 최저가 정책")
                .fullBody("브랜드는 공구 기간 중 동일 상품을 공구가보다 낮은 가격으로 타 채널에 판매하지 않는다.")
                .build());

        // 문안이 법률 검토 대기인 조항 — 전문이 비어 있는 상태 그대로 내려가야 한다(설계서 미결 #5).
        version.getClauses().add(ContractClause.builder()
                .clauseVersion(version)
                .code("EXCLUSIVE_SALE")
                .sortOrder(2)
                .summaryTitle("단독 판매")
                .summaryDescription("공구 기간 중 해당 상품은 이 공구에서만 판매합니다.")
                .build());

        clauseVersionRepository.save(version);
    }
}
