package showroomz.api.creator.contract;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractClause;
import showroomz.domain.contract.entity.ContractClauseVersion;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.domain.contract.entity.ContractHistory;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.repository.ContractClauseVersionRepository;
import showroomz.domain.contract.repository.ContractDocumentRepository;
import showroomz.domain.contract.repository.ContractHistoryRepository;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.repository.ContractResendRequestRepository;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractClauseVersionStatus;
import showroomz.domain.contract.type.ContractCloseReasonCode;
import showroomz.domain.contract.type.ContractDeclineReason;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.contract.type.SecondaryUsePeriodType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.support.BrandFixture;
import showroomz.support.IntegrationTestSupport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * §27 쇼룸 스튜디오 계약 테스트 공통 배선.
 *
 * <p>스튜디오는 계약을 만들지 않는다 — 인플루언서에게 도착하는 계약은 전부 브랜드 작성 → 운영자 검토를
 * 거친 뒤다. 그래서 여기서는 상태를 <b>직접 적재</b>한다(설계서 9 — Q4는 어드민 P7과 순서 의존이 없고,
 * S3b 같은 분기는 값을 직접 넣어야 탄다). 실제 전이 API를 태우는 흐름은
 * {@code CreatorContractLifecycleIntegrationTest}가 따로 본다.
 */
abstract class CreatorContractTestSupport extends IntegrationTestSupport {

    protected static final String CONTRACTS = "/v1/creator/contracts";

    /** 응답의 LocalDateTime 직렬화 형식(JacksonConfig) — 시각을 문자열로 대조할 때 쓴다. */
    private static final DateTimeFormatter RESPONSE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected CreatorRepository creatorRepository;
    @Autowired
    protected ConnectionRepository connectionRepository;
    @Autowired
    protected MessageThreadRepository messageThreadRepository;
    @Autowired
    protected CategoryRepository categoryRepository;
    @Autowired
    protected ProductRepository productRepository;
    @Autowired
    protected ContractRepository contractRepository;
    @Autowired
    protected ContractHistoryRepository contractHistoryRepository;
    @Autowired
    protected ContractDocumentRepository contractDocumentRepository;
    @Autowired
    protected ContractResendRequestRepository resendRequestRepository;
    @Autowired
    protected ContractClauseVersionRepository clauseVersionRepository;

    protected BrandFixture.Brand brand;
    protected Creator me;
    protected String myToken;
    protected Connection myConnection;
    protected MessageThread myThread;
    protected ContractClauseVersion clauseVersion;
    protected Product serum;
    protected Product cream;

    private int contractNumberSeq = 1;
    private int extraBrandSeq = 1;

    @BeforeEach
    void setUpActors() {
        brand = fixture.createBrand("brand@showroomz.test", "퓨어랩");
        serum = createProduct("수분진정 세럼 30ml", 32_000);
        cream = createProduct("수분진정 크림 50ml", 24_000);
        clauseVersion = seedClauseVersion();

        Users owner = userRepository.save(new Users(
                "creator-소연", "뷰티_소연", "soyeon@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.CREATOR, LocalDateTime.now(), LocalDateTime.now()));
        me = creatorRepository.save(Creator.builder()
                .user(owner)
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/soyeon")
                .accountId("soyeon")
                .followerCount(52_000)
                .businessEmail("biz@showroomz.test")
                .showroomName("뷰티_소연")
                .businessType(CreatorBusinessType.INDIVIDUAL)
                .build());
        myToken = bearerToken(owner.getUsername(), RoleType.CREATOR, owner.getId());

        myConnection = Connection.requestPair(brand.market(), me);
        myConnection.markConnected();
        connectionRepository.save(myConnection);
        myThread = messageThreadRepository.save(MessageThread.openFor(myConnection));
    }

    // ── 요청 ────────────────────────────────────────────────────────────────

    protected ResultActions list(String... params) throws Exception {
        var request = get(CONTRACTS).header(HttpHeaders.AUTHORIZATION, myToken);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions summary() throws Exception {
        return mockMvc.perform(get(CONTRACTS + "/summary").header(HttpHeaders.AUTHORIZATION, myToken));
    }

    protected ResultActions detail(Long contractId, String... params) throws Exception {
        var request = get(CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, myToken);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions decline(Long contractId, String body) throws Exception {
        return mockMvc.perform(post(CONTRACTS + "/" + contractId + "/decline")
                .header(HttpHeaders.AUTHORIZATION, myToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    protected ResultActions requestResend(Long contractId) throws Exception {
        return mockMvc.perform(post(CONTRACTS + "/" + contractId + "/resend-request")
                .header(HttpHeaders.AUTHORIZATION, myToken));
    }

    protected ResultActions document(Long contractId, ContractDocumentType type) throws Exception {
        return mockMvc.perform(get(CONTRACTS + "/" + contractId + "/documents/" + type.name())
                .header(HttpHeaders.AUTHORIZATION, myToken));
    }

    protected String iso(LocalDateTime time) {
        return time.format(RESPONSE_DATE_TIME);
    }

    // ── 상태별 계약 ──────────────────────────────────────────────────────────

    protected Long signingContract() {
        return saveContract(ContractStatus.SIGNING, this::approve);
    }

    protected Long conclusionPendingContract() {
        return saveContract(ContractStatus.CONCLUSION_PENDING, contract -> {
            approve(contract);
            contract.updateSignatures(LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
        });
    }

    protected Long concludedContract() {
        return saveContract(ContractStatus.CONCLUDED, contract -> {
            approve(contract);
            contract.updateSignatures(LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
            contract.conclude(LocalDateTime.now());
        });
    }

    protected Long expiredContract() {
        return saveContract(ContractStatus.EXPIRED, contract -> {
            approve(contract, LocalDateTime.now().minusDays(1));
            contract.expire(LocalDateTime.now());
        });
    }

    protected Long canceledContract() {
        return saveContract(ContractStatus.CANCELED, contract -> {
            approve(contract);
            contract.applyCanceled(ContractCloseReasonCode.SCHEDULE_CHANGE.name(),
                    "생산 일정이 밀려 공구 기간을 다시 잡아야 합니다.", LocalDateTime.now());
        });
    }

    /** 거절은 실제 조건부 UPDATE 경로를 태운다 — applyCanceled는 주체가 SELLER라 여기 쓸 수 없다. */
    protected Long declinedContract() {
        return declined(signingContract(), ContractDeclineReason.SCHEDULE_MISMATCH, null);
    }

    protected Long declined(Long contractId, ContractDeclineReason reason, String memo) {
        transactionTemplate.executeWithoutResult(tx -> contractRepository.declineByCreator(
                contractId, me.getId(), reason.name(), memo, LocalDateTime.now()));
        return contractId;
    }

    protected Long reviewRejectedContract() {
        return saveContract(ContractStatus.REVIEW_REJECTED, contract -> {
            requestReview(contract);
            contract.rejectReview("PRICE_POLICY", "공구가가 정가보다 높습니다.", LocalDateTime.now());
        });
    }

    protected void requestReview(Contract contract) {
        contract.applyReviewRequested(nextContractNumber(), clauseVersion, null, LocalDateTime.now());
    }

    protected void approve(Contract contract) {
        approve(contract, LocalDateTime.now().plusDays(8));
    }

    protected void approve(Contract contract, LocalDateTime deadlineAt) {
        approve(contract, LocalDateTime.now().minusDays(1), deadlineAt);
    }

    /** 검토 요청 → 운영자 검토 통과 → 양측 서명 요청 발송까지 한 번에 — 인플루언서에게 「도착」하는 지점이다. */
    protected void approve(Contract contract, LocalDateTime requestedAt, LocalDateTime deadlineAt) {
        contract.applyReviewRequested(nextContractNumber(), clauseVersion, null, requestedAt.minusHours(1));
        contract.approveReview(requestedAt, deadlineAt, requestedAt);
    }

    private String nextContractNumber() {
        return "CTR-20260813-" + String.format("%05d", contractNumberSeq++);
    }

    // ── 적재 ────────────────────────────────────────────────────────────────

    protected Long saveContract(ContractStatus status, Consumer<Contract> shape) {
        return saveContractFor(me, status, shape);
    }

    protected Long saveContractFor(Creator creator, ContractStatus status, Consumer<Contract> shape) {
        Connection connection = creator.getId().equals(me.getId()) ? myConnection : null;
        return saveContractFor(brand.market(), creator, connection, status, shape);
    }

    /**
     * 조건이 채워진 계약 1건을 원하는 상태로 적재한다. {@code shape}는 기본 조건 <b>뒤에</b> 돌아
     * 상태 전이·서명 조합·조건 덮어쓰기를 한다.
     */
    protected Long saveContractFor(Market market, Creator creator, Connection connection,
                                   ContractStatus status, Consumer<Contract> shape) {
        return transactionTemplate.execute(tx -> {
            Contract contract = Contract.createDraft(market, creator, connection);
            contract.updateTerms(
                    "여름 수분 세럼 공구",
                    LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(26),
                    1_200_000, FixedFeeTrigger.POST_REGISTERED, LocalDateTime.now(),
                    1, 1, 3, LocalDate.now().plusDays(30),
                    true, SecondaryUsePeriodType.FIXED, 12, false, "비고");
            contract.replaceItems(new ArrayList<>(List.of(item(serum, 28_000, "15.0", 300))));

            shape.accept(contract);
            // 종결·만료는 전이 메서드가 상태를 정하므로 요청한 상태와 어긋나지 않는지만 확인한다.
            Contract saved = contractRepository.saveAndFlush(contract);
            assertThat(saved.getStatus()).isEqualTo(status);
            return saved.getId();
        });
    }

    /**
     * 시안 S3 「여름 수분 세럼 공구」의 조건 그대로 — 8일짜리 공구 · 고정 지급비 1,200,000원(게시물 등록 후) ·
     * 피드 1 · 릴스 1 · 스토리 3 · 2차 활용 12개월 · 사전 검수 있음 · 상품 2건(15% · 12%).
     */
    protected void applyScreenTerms(Contract contract, String title) {
        LocalDateTime startAt = screenStartAt();
        LocalDateTime endAt = screenEndAt();
        contract.updateTerms(
                title, startAt, endAt,
                1_200_000, FixedFeeTrigger.POST_REGISTERED, LocalDateTime.now(),
                1, 1, 3, endAt.toLocalDate(),
                true, SecondaryUsePeriodType.FIXED, 12, true,
                "2차 활용 범위: 자사 상세페이지·인스타 광고 소재로 게시 후 12개월간 사용. 스토리 3회는 공구 기간 중 균등 분배.");
        contract.replaceItems(new ArrayList<>(List.of(
                item(serum, 28_000, "15.0", 300),
                item(cream, 22_000, "12.0", 150))));
    }

    /** 공구 시작 10:00 */
    protected LocalDateTime screenStartAt() {
        return LocalDate.now().plusDays(10).atTime(10, 0);
    }

    /** 공구 종료 7일 뒤 23:55 — 일자 양끝 포함 8일 */
    protected LocalDateTime screenEndAt() {
        return LocalDate.now().plusDays(17).atTime(LocalTime.of(23, 55));
    }

    protected ContractItem item(Product product, int groupBuyPrice, String rewardRate, int brandSupplyQuantity) {
        return ContractItem.builder()
                .product(product)
                .productName(product.getName())
                .regularPrice(product.getRegularPrice())
                .groupBuyPrice(groupBuyPrice)
                .rewardRate(new BigDecimal(rewardRate))
                .minQuantity(brandSupplyQuantity)
                .sortOrder(0)
                .build();
    }

    protected void saveHistory(Contract contract, ContractEventType eventType,
                               ContractActorType actorType, LocalDateTime occurredAt) {
        saveHistory(contract, eventType, actorType, "표시명", null, occurredAt);
    }

    protected void saveHistory(Contract contract, ContractEventType eventType, ContractActorType actorType,
                               String actorDisplayName, String detail, LocalDateTime occurredAt) {
        contractHistoryRepository.save(ContractHistory.of(
                contract, eventType, actorType, null, actorDisplayName, detail, occurredAt));
    }

    protected Contract load(Long contractId) {
        return contractRepository.findById(contractId).orElseThrow();
    }

    protected void registerDocument(Long contractId, ContractDocumentType type, String originalName) {
        contractDocumentRepository.save(ContractDocument.builder()
                .contract(load(contractId))
                .documentType(type)
                .s3Key("contracts/%d/%s.pdf".formatted(contractId, type.name()))
                .originalName(originalName)
                .sizeBytes(1_200_000L)
                .contentType("application/pdf")
                .uploadedAt(LocalDateTime.now().withNano(0))
                .build());
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────

    /** 목록 시안처럼 여러 브랜드에게서 계약을 받는 장면을 만든다 — 브랜드명은 market 조인으로 읽힌다. */
    protected Market otherBrand(String marketName) {
        return fixture.createBrand("brand-" + extraBrandSeq++ + "@showroomz.test", marketName).market();
    }

    protected Creator createOtherCreator() {
        Users owner = userRepository.save(new Users(
                "creator-지민", "글로우_지민", "jimin@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.CREATOR, LocalDateTime.now(), LocalDateTime.now()));
        return creatorRepository.save(Creator.builder()
                .user(owner)
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/jimin")
                .accountId("jimin")
                .followerCount(12_000)
                .businessEmail("biz2@showroomz.test")
                .showroomName("글로우_지민")
                .businessType(CreatorBusinessType.BUSINESS)
                .build());
    }

    protected Product createProduct(String name, int regularPrice) {
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
    private ContractClauseVersion seedClauseVersion() {
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

        return clauseVersionRepository.save(version);
    }
}
