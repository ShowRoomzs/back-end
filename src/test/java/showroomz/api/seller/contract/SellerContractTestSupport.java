package showroomz.api.seller.contract;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.seller.contract.dto.ContractReviewRequestRequest;
import showroomz.api.seller.contract.dto.ContractUpdateRequest;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractClause;
import showroomz.domain.contract.entity.ContractClauseVersion;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.repository.ContractClauseVersionRepository;
import showroomz.domain.contract.repository.ContractDocumentRepository;
import showroomz.domain.contract.repository.ContractHistoryRepository;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.repository.ContractResendRequestRepository;
import showroomz.domain.contract.type.ContractClauseVersionStatus;
import showroomz.domain.contract.type.ContractCloseReasonCode;
import showroomz.domain.contract.type.ContractDeclineReason;
import showroomz.domain.contract.type.ContractReviewRejectReason;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.contract.type.SecondaryUsePeriodType;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.support.BrandFixture;
import showroomz.support.IntegrationTestSupport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §25·§26 파트너센터 계약 관리 테스트의 공통 배선.
 *
 * <p>브랜드 1곳 · 연결됨 상대 1명 · 진열 상품 2건 · 시행중 표준 조항 1버전을 깔고,
 * <b>하드 검증을 하나도 건드리지 않는 기준 폼</b>({@link #validForm})을 준다. 각 테스트는 이 폼에서
 * 항목 하나만 바꿔 규칙 하나를 찌른다 — 그래야 실패했을 때 어느 규칙이 깨졌는지 바로 읽힌다.
 *
 * <p>이미 진행된 계약({@link #seedInStatus})은 어드민·스튜디오 API를 타지 않고 직접 적재한다.
 * 파트너 화면의 분기는 서명 조합·체결 여부처럼 <b>다른 서피스가 만드는 값</b>에 달려 있어서,
 * 그 값을 직접 넣지 않으면 파트너 테스트가 남의 API 순서에 묶인다.
 */
abstract class SellerContractTestSupport extends IntegrationTestSupport {

    protected static final String CONTRACTS = "/v1/seller/contracts";

    /** 적재한 계약의 번호대 — 실제 발급기가 쓰는 001번대와 겹치면 유니크 제약에 걸린다. */
    private static final int SEEDED_NUMBER_BASE = 900;

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected CreatorRepository creatorRepository;
    @Autowired
    protected ConnectionRepository connectionRepository;
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
    protected ContractResendRequestRepository contractResendRequestRepository;
    @Autowired
    protected ContractClauseVersionRepository clauseVersionRepository;

    protected BrandFixture.Brand brand;
    protected String brandToken;
    protected Creator counterparty;
    protected Connection counterpartyConnection;
    protected Product serum;
    protected Product cream;
    protected ContractClauseVersion clauseVersion;

    private int seededNumberSeq;

    @BeforeEach
    void setUpContractFixtures() {
        brand = fixture.createBrand("brand@showroomz.test", "글로우랩");
        brandToken = sellerToken(brand.seller());

        counterparty = createConnectedCreator("글로우_지민", "jimin");
        counterpartyConnection = connectionRepository
                .findConnectedPair(brand.marketId(), counterparty.getId())
                .orElseThrow();

        serum = createProduct("수분진정 세럼 30ml", 32_000);
        cream = createProduct("수분진정 크림 50ml", 24_000);

        clauseVersion = seedClauseVersion();
    }

    // ------------------------------------------------------------------ 요청

    protected long createDraft() throws Exception {
        return createDraft(null);
    }

    /** {@code creatorId}를 보내면 스레드 경유 진입이다 — 상대가 고정된다(§25-5-1). */
    protected long createDraft(Long creatorId) throws Exception {
        String body = creatorId == null ? "{}" : "{\"creatorId\":%d}".formatted(creatorId);
        String response = mockMvc.perform(post(CONTRACTS)
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readLong(response, "$.contractId");
    }

    protected ResultActions save(long contractId, Form form) throws Exception {
        return mockMvc.perform(put(CONTRACTS + "/" + contractId)
                .header(HttpHeaders.AUTHORIZATION, brandToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(form.toRequest())));
    }

    protected String saveOk(long contractId, Form form) throws Exception {
        return save(contractId, form)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** 기준 폼으로 채워진 작성중 계약 — 검토 요청만 누르면 통과하는 상태. */
    protected long draftReadyForReview() throws Exception {
        long contractId = createDraft();
        saveOk(contractId, validForm(0L));
        return contractId;
    }

    protected ResultActions deleteContract(long contractId) throws Exception {
        return mockMvc.perform(delete(CONTRACTS + "/" + contractId)
                .header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    protected ResultActions detail(long contractId) throws Exception {
        return mockMvc.perform(get(CONTRACTS + "/" + contractId)
                .header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    protected String detailOk(long contractId) throws Exception {
        return detail(contractId)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    protected ResultActions validate(long contractId) throws Exception {
        return mockMvc.perform(post(CONTRACTS + "/" + contractId + "/validate")
                .header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    protected ResultActions reviewRequest(long contractId, String... acknowledgedWarnings) throws Exception {
        return mockMvc.perform(post(CONTRACTS + "/" + contractId + "/review-request")
                .header(HttpHeaders.AUTHORIZATION, brandToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new ContractReviewRequestRequest(List.of(acknowledgedWarnings)))));
    }

    protected String reviewRequestOk(long contractId, String... acknowledgedWarnings) throws Exception {
        return reviewRequest(contractId, acknowledgedWarnings)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    protected ResultActions cancelReviewRequest(long contractId) throws Exception {
        return mockMvc.perform(post(CONTRACTS + "/" + contractId + "/review-request/cancel")
                .header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    protected ResultActions requestResend(long contractId) throws Exception {
        return mockMvc.perform(post(CONTRACTS + "/" + contractId + "/resend-request")
                .header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    protected ResultActions recordFixedFeePayment(long contractId) throws Exception {
        return mockMvc.perform(post(CONTRACTS + "/" + contractId + "/fixed-fee/payment")
                .header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    protected ResultActions duplicate(long contractId) throws Exception {
        return mockMvc.perform(post(CONTRACTS + "/" + contractId + "/duplicate")
                .header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    // ------------------------------------------------------------------ 폼

    /**
     * 하드 검증을 하나도 건드리지 않는 기준 폼.
     * 기간 10일(3~30일) · 시작 D+10(리드타임 7일 통과) · 고정 지급비 0원(고지 확인 불필요) ·
     * 할인율 12.5%(W1 미만) · 리워드율 15%(W2 미만) · 항목 1건.
     */
    protected Form validForm(long version) {
        LocalDateTime startAt = baseStartAt();
        return new Form()
                .version(version)
                .creator(counterparty.getId())
                .title("가을 앰플 신제품 공구")
                .period(startAt, startAt.plusDays(9))
                .fixedFee(0, FixedFeeTrigger.POST_REGISTERED, false)
                .content(1, 1, 0, startAt.plusDays(12).toLocalDate())
                .secondaryUse(true, SecondaryUsePeriodType.FIXED, 12)
                .items(item(serum, 28_000, "15.0", 300));
    }

    protected LocalDateTime baseStartAt() {
        return LocalDateTime.now().plusDays(10).withNano(0);
    }

    protected ContractUpdateRequest.Item item(Product product, int groupBuyPrice, String rewardRate,
                                              int minQuantity) {
        return new ContractUpdateRequest.Item(
                null, product.getProductId(), groupBuyPrice, new BigDecimal(rewardRate), minQuantity);
    }

    /**
     * 임시저장 요청 빌더. 레코드에는 복사 생성자가 없어 한 항목만 바꾸려면 18개를 다시 나열해야 한다 —
     * 테스트가 무엇을 찌르는지 한 줄로 읽히도록 빌더를 둔다.
     */
    protected static final class Form {

        private long version;
        private Long creatorId;
        private String title;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private Integer fixedFeeAmount;
        private FixedFeeTrigger fixedFeeTrigger;
        private Boolean fixedFeeNoticeAgreed;
        private Integer feedCount;
        private Integer reelsCount;
        private Integer storyCount;
        private LocalDate contentDueDate;
        private Boolean secondaryUseAllowed;
        private SecondaryUsePeriodType secondaryUsePeriodType;
        private Integer secondaryUseMonths;
        private Boolean brandPreReview = false;
        private String note;
        private List<ContractUpdateRequest.Item> items = new ArrayList<>();

        Form version(long version) {
            this.version = version;
            return this;
        }

        Form creator(Long creatorId) {
            this.creatorId = creatorId;
            return this;
        }

        Form title(String title) {
            this.title = title;
            return this;
        }

        Form period(LocalDateTime startAt, LocalDateTime endAt) {
            this.startAt = startAt;
            this.endAt = endAt;
            return this;
        }

        Form fixedFee(Integer amount, FixedFeeTrigger trigger, Boolean noticeAgreed) {
            this.fixedFeeAmount = amount;
            this.fixedFeeTrigger = trigger;
            this.fixedFeeNoticeAgreed = noticeAgreed;
            return this;
        }

        Form content(Integer feedCount, Integer reelsCount, Integer storyCount, LocalDate dueDate) {
            this.feedCount = feedCount;
            this.reelsCount = reelsCount;
            this.storyCount = storyCount;
            this.contentDueDate = dueDate;
            return this;
        }

        Form secondaryUse(Boolean allowed, SecondaryUsePeriodType periodType, Integer months) {
            this.secondaryUseAllowed = allowed;
            this.secondaryUsePeriodType = periodType;
            this.secondaryUseMonths = months;
            return this;
        }

        Form note(String note) {
            this.note = note;
            return this;
        }

        Form items(ContractUpdateRequest.Item... items) {
            return items(List.of(items));
        }

        Form items(List<ContractUpdateRequest.Item> items) {
            this.items = new ArrayList<>(items);
            return this;
        }

        ContractUpdateRequest toRequest() {
            return new ContractUpdateRequest(
                    version, creatorId, title, startAt, endAt,
                    fixedFeeAmount, fixedFeeTrigger, fixedFeeNoticeAgreed,
                    feedCount, reelsCount, storyCount, contentDueDate,
                    secondaryUseAllowed, secondaryUsePeriodType, secondaryUseMonths,
                    brandPreReview, note, items);
        }
    }

    // ------------------------------------------------------------------ 응답 읽기

    protected long readLong(String json, String path) {
        return JsonPath.parse(json).read(path, Number.class).longValue();
    }

    protected String readString(String json, String path) {
        return JsonPath.parse(json).read(path, String.class);
    }

    protected long versionOf(String json) {
        return readLong(json, "$.version");
    }

    /**
     * 지금 저장에 써야 하는 버전. 임시저장은 <b>응답이 돌려준 버전</b>으로 이어 저장하는 것이 실제 흐름이라
     * 테스트도 값을 세지 않고 물어본다 — 서버가 몇 씩 올리는지는 FE가 알 바가 아니다.
     */
    protected long currentVersion(long contractId) throws Exception {
        return versionOf(detailOk(contractId));
    }

    // ------------------------------------------------------------------ 계약 적재

    protected Contract seedInStatus(ContractStatus status) {
        return seedInStatus(status, contract -> {
        });
    }

    /**
     * 조건이 채워진 계약 1건을 원하는 상태로 적재한다. {@code shape}는 상태 전이 <b>뒤에</b> 돌아
     * 서명 조합·종결 사유처럼 화면 분기를 가르는 값을 덧씌운다.
     */
    protected Contract seedInStatus(ContractStatus status, Consumer<Contract> shape) {
        return transactionTemplate.execute(tx -> {
            Contract contract = Contract.createDraft(brand.market(), counterparty, null);
            terms(contract, "가을 앰플 신제품 공구", baseStartAt(), baseStartAt().plusDays(9));
            contract.replaceItems(new ArrayList<>(List.of(seedItem(serum, 28_000))));

            advanceTo(contract, status);
            shape.accept(contract);

            Contract saved = contractRepository.saveAndFlush(contract);
            if (status == ContractStatus.DECLINED) {
                // 거절은 엔티티 메서드가 없다 — 스튜디오와 같은 조건부 UPDATE를 그대로 태운다.
                contractRepository.declineByCreator(saved.getId(), counterparty.getId(),
                        ContractDeclineReason.SCHEDULE_MISMATCH.name(), "일정이 맞지 않습니다.",
                        LocalDateTime.now().withNano(0));
                Contract declined = contractRepository.findById(saved.getId()).orElseThrow();
                assertThat(declined.getStatus()).isEqualTo(ContractStatus.DECLINED);
                return declined;
            }
            assertThat(saved.getStatus()).isEqualTo(status);
            return saved;
        });
    }

    protected ContractItem seedItem(Product product, int groupBuyPrice) {
        return ContractItem.builder()
                .product(product)
                .productName(product.getName())
                .regularPrice(product.getRegularPrice())
                .groupBuyPrice(groupBuyPrice)
                .rewardRate(new BigDecimal("15.0"))
                .minQuantity(300)
                .sortOrder(0)
                .build();
    }

    /** 조건 일괄 세팅 — 목록 테스트가 공구명·기간만 갈아 끼우려고 쓴다. */
    protected void terms(Contract contract, String title, LocalDateTime startAt, LocalDateTime endAt) {
        terms(contract, title, startAt, endAt, 500_000);
    }

    protected void terms(Contract contract, String title, LocalDateTime startAt, LocalDateTime endAt,
                         Integer fixedFeeAmount) {
        contract.updateTerms(
                title, startAt, endAt,
                fixedFeeAmount, FixedFeeTrigger.POST_REGISTERED, LocalDateTime.now().withNano(0),
                1, 1, 0, endAt == null ? null : endAt.toLocalDate().plusDays(3),
                true, SecondaryUsePeriodType.FIXED, 12, false, "비고");
    }

    /** 다른 서피스가 만드는 값들을 직접 넣는다 — 전이 API 호출 순서에 묶이지 않기 위해서다. */
    private void advanceTo(Contract contract, ContractStatus status) {
        if (status == ContractStatus.DRAFT) {
            return;
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        contract.applyReviewRequested(nextSeededNumber(), clauseVersion, null, now.minusDays(3));
        if (status == ContractStatus.REVIEW_PENDING) {
            return;
        }
        if (status == ContractStatus.REVIEW_REJECTED) {
            contract.rejectReview(ContractReviewRejectReason.INFO_MISMATCH.name(),
                    "상품 정보가 계약 조건과 맞지 않습니다.", now.minusDays(2));
            return;
        }

        contract.approveReview(now.minusDays(2), now.plusDays(5), now.minusDays(2));
        switch (status) {
            // 거절은 저장 뒤 조건부 UPDATE로 만든다 — 여기서는 서명 전 상태로 둔다.
            case SIGNING, DECLINED -> {
            }
            case CONCLUSION_PENDING -> contract.updateSignatures(
                    now.minusDays(1), now.minusDays(1), now.minusDays(1));
            case CONCLUDED -> {
                contract.updateSignatures(now.minusDays(1), now.minusDays(1), now.minusDays(1));
                contract.conclude(now.minusHours(2));
            }
            case EXPIRED -> contract.expire(now.minusDays(1));
            // 브랜드에게 계약 취소는 없다 — 취소는 서명 요청 발송 이후 운영자가 한다.
            case CANCELED -> contract.applyCanceledByAdmin(ContractCloseReasonCode.SCHEDULE_CHANGE.name(),
                    "브랜드 요청으로 서명 요청을 회수했습니다.", now.minusDays(1));
            default -> throw new IllegalArgumentException("적재할 수 없는 상태: " + status);
        }
    }

    private String nextSeededNumber() {
        return "CTR-%s-%03d".formatted(
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
                SEEDED_NUMBER_BASE + ++seededNumberSeq);
    }

    // ------------------------------------------------------------------ 픽스처

    protected Creator createConnectedCreator(String showroomName, String accountId) {
        return createConnectedCreator(showroomName, accountId, CreatorBusinessType.INDIVIDUAL);
    }

    /** 사업 형태는 정산 요약의 원천징수 표기를 가르는 값이다(§25-5-6) — 픽스처에서 골라 쓴다. */
    protected Creator createConnectedCreator(String showroomName, String accountId,
                                             CreatorBusinessType businessType) {
        Creator creator = createCreator(showroomName, accountId, businessType);
        Connection connection = Connection.requestPair(brand.market(), creator);
        connection.markConnected();
        connectionRepository.save(connection);
        return creator;
    }

    protected Creator createCreator(String showroomName, String accountId) {
        return createCreator(showroomName, accountId, CreatorBusinessType.INDIVIDUAL);
    }

    protected Creator createCreator(String showroomName, String accountId, CreatorBusinessType businessType) {
        LocalDateTime now = LocalDateTime.now();
        Users owner = userRepository.save(new Users(
                "creator-" + accountId, showroomName, accountId + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.CREATOR, now, now));

        return creatorRepository.save(Creator.builder()
                .user(owner)
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/" + accountId)
                .accountId(accountId)
                .followerCount(12_000)
                .businessEmail(accountId + "-biz@showroomz.test")
                .showroomName(showroomName)
                .businessType(businessType)
                .build());
    }

    protected Product createProduct(String name, int regularPrice) {
        return createProduct(brand, name, regularPrice);
    }

    protected Product createProduct(BrandFixture.Brand owner, String name, int regularPrice) {
        Category category = new Category();
        category.setName("뷰티 " + name);
        categoryRepository.save(category);

        Product product = new Product();
        product.setMarket(owner.market());
        product.setCategory(category);
        product.setName(name);
        product.setRegularPrice(regularPrice);
        product.setSalePrice(regularPrice);
        product.setDisplayStatus(ProductDisplayStatus.DISPLAY);
        return productRepository.save(product);
    }

    protected void changeDisplayStatus(Product product, ProductDisplayStatus displayStatus) {
        product.setDisplayStatus(displayStatus);
        productRepository.save(product);
    }

    protected void changeRegularPrice(Product product, int regularPrice) {
        product.setRegularPrice(regularPrice);
        productRepository.save(product);
    }

    /** Flyway가 꺼진 통합 테스트 프로필에는 V120의 조항 seed가 없다 — 최소 버전을 직접 적재한다. */
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
