package showroomz.api.admin.contract;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import showroomz.api.admin.contract.dto.AdminContractDto.*;
import showroomz.api.admin.contract.service.ContractDocumentStorage;
import showroomz.api.admin.contract.service.ContractPdfRenderer;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.contract.entity.*;
import showroomz.domain.contract.repository.*;
import showroomz.domain.contract.service.ContractNotifier;
import showroomz.domain.contract.type.*;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.seller.entity.Seller;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ui-admin-01-contracts(rev.1) 어드민 계약 관리 테스트의 공통 배선.
 *
 * <p>시안의 주 계약 두 건과 같은 당사자를 깐다 — 브랜드 「글로우랩」 · 인플루언서 「뷰티_하윤」(연결됨 · 스레드 있음) ·
 * 운영자 「김운영」. 시안이 이력 주체를 실명 「김운영」으로 적었으므로 운영자 이름도 그대로 쓴다.
 *
 * <p>진행된 계약은 {@link #seed}로 직접 적재한다. 검토 요청·서명·체결 시각을 시안처럼 서로 다른 값으로
 * 박아야 정렬·경과·스텝퍼 검증이 의미를 가지므로, 시각은 전부 {@link #now} 기준 상대값으로 받는다.
 *
 * <p>S3와 크로미움은 외부 의존이라 모의 객체로 둔다. 통지는 스텁이지만 「승인 → 양측」 「반려 → 브랜드」
 * 호출 지점이 기획이므로 스파이로 호출을 검증한다.
 */
public abstract class AdminContractTestSupport extends IntegrationTestSupport {

    protected static final String BASE = "/v1/admin/contracts";
    protected static final String SELLER_CONTRACTS = "/v1/seller/contracts";
    protected static final String CREATOR_CONTRACTS = "/v1/creator/contracts";
    protected static final String OPERATOR_NAME = "김운영";

    @Autowired protected ContractRepository contracts;
    @Autowired protected ContractHistoryRepository histories;
    @Autowired protected ContractDocumentRepository documents;
    @Autowired protected ContractResendRequestRepository resends;
    @Autowired protected ContractClauseVersionRepository clauseVersions;
    @Autowired protected CreatorRepository creators;
    @Autowired protected UserRepository users;
    @Autowired protected ConnectionRepository connections;
    @Autowired protected MessageThreadRepository threads;
    @Autowired protected CategoryRepository categories;
    @Autowired protected ProductRepository products;
    @Autowired protected JdbcTemplate jdbc;

    @MockitoBean protected ContractDocumentStorage storage;
    @MockitoBean protected ContractPdfRenderer renderer;
    @MockitoSpyBean protected ContractNotifier notifier;

    /** 초 단위로 자른 기준 시각 — 응답 JSON과 문자열로 대조하기 위해 나노초를 버린다. */
    protected final LocalDateTime now = LocalDateTime.now().withNano(0);

    protected Seller admin;
    protected String adminToken;
    protected BrandFixture.Brand brand;
    protected String brandToken;
    protected Creator creator;
    protected String creatorToken;
    protected MessageThread thread;
    protected Product serum;
    protected Product cream;
    protected ContractClauseVersion clauseVersion;

    private int numberSeq;

    @BeforeEach
    void setUpAdminContractFixtures() {
        admin = fixture.createAdmin("operator@showroomz.test", OPERATOR_NAME);
        adminToken = adminToken(admin);

        brand = fixture.createBrand("glowlab@showroomz.test", "글로우랩");
        brandToken = sellerToken(brand.seller());

        Users owner = users.save(new Users("creator-hayun", "뷰티_하윤", "hayun@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.CREATOR, now, now));
        creator = createCreator(owner, "뷰티_하윤", "hayun");
        creatorToken = bearerToken(owner.getUsername(), RoleType.CREATOR, owner.getId());
        Connection connection = Connection.requestPair(brand.market(), creator);
        connection.markConnected();
        connections.save(connection);
        thread = threads.save(MessageThread.openFor(connection));

        serum = createProduct(brand, "수분진정 세럼 30ml", 32_000);
        cream = createProduct(brand, "리페어 크림 60ml", 48_000);
        clauseVersion = seedClauseVersion();

        when(storage.download(any())).thenAnswer(invocation -> {
            ContractDocument d = invocation.getArgument(0);
            return new DownloadResponse("https://signed.test/" + d.getS3Key(), d.getOriginalName(), d.getSizeBytes(),
                    300, d.getSourceReviewRequestedAt());
        });
        when(storage.presign(anyString())).thenAnswer(invocation -> {
            String prefix = invocation.getArgument(0);
            return new PresignResponse(prefix + java.util.UUID.randomUUID() + ".pdf", "https://upload.test/" + prefix,
                    "application/pdf", 900);
        });
        when(storage.sealUpload(anyString(), anyLong(), anyLong()))
                .thenAnswer(invocation -> "contracts/" + invocation.getArgument(1) + "/documents/" + java.util.UUID.randomUUID() + ".pdf");
        when(storage.putGenerated(anyLong(), any()))
                .thenAnswer(invocation -> "contracts/" + invocation.getArgument(0) + "/documents/generated-" + java.util.UUID.randomUUID() + ".pdf");
        when(renderer.render(anyString(), anyString())).thenReturn("%PDF-1.7 test".getBytes());
    }

    // ------------------------------------------------------------------ 시안 시각

    /**
     * 시안이 그려진 시점. B1의 「08.13 16:52 검토 요청 · 대기 경과 1일 2시간」에서 역산했다.
     * 시안 시각은 {@link #spec}으로 이 시점과의 간격을 유지한 채 {@link #now} 쪽으로 옮겨 쓴다 —
     * 절대 날짜를 박으면 테스트가 도는 날에 따라 만료 큐 판정이 뒤집힌다.
     */
    protected static final LocalDateTime SPEC_NOW = LocalDateTime.of(2026, 8, 14, 18, 52);

    protected LocalDateTime spec(String isoDateTime) {
        return now.minus(java.time.Duration.between(LocalDateTime.parse(isoDateTime), SPEC_NOW));
    }

    // ------------------------------------------------------------------ 적재

    /** 시안 B1의 「겨울 리페어 크림 공구」와 같은 모양의 계약을 원하는 상태로 적재한다. */
    protected Contract seed(ContractStatus status) {
        return seed(status, c -> { });
    }

    protected Contract seed(ContractStatus status, Consumer<Seed> shape) {
        Seed seed = new Seed(status);
        shape.accept(seed);
        return persist(seed);
    }

    /**
     * 적재 명세. 필드 기본값은 「오늘 검토 요청이 들어와 이후 절차가 전부 과거에 끝난」 계약이고,
     * 각 테스트는 판정에 관여하는 값만 덮어쓴다.
     */
    protected final class Seed {
        final ContractStatus status;
        BrandFixture.Brand owner = brand;
        Creator counterparty = creator;
        String title = "겨울 리페어 크림 공구";
        LocalDateTime startAt = now.plusDays(10).withHour(10).withMinute(0).withSecond(0);
        int days = 8;
        int itemCount = 1;
        LocalDateTime reviewRequestedAt = now.minusDays(1).minusHours(2);
        LocalDateTime signatureRequestedAt = now.minusHours(20);
        LocalDateTime signatureDeadlineAt = now.plusDays(5);
        LocalDateTime brandSignedAt;
        LocalDateTime creatorSignedAt;
        LocalDateTime signatureAsOf;
        LocalDateTime createdAt;

        Seed(ContractStatus status) {
            this.status = status;
            if (status == ContractStatus.CONCLUSION_PENDING || status == ContractStatus.CONCLUDED) {
                brandSignedAt = now.minusHours(4);
                creatorSignedAt = now.minusHours(2);
                signatureAsOf = now.minusHours(1);
            }
        }

        public Seed owner(BrandFixture.Brand owner) { this.owner = owner; return this; }
        public Seed counterparty(Creator counterparty) { this.counterparty = counterparty; return this; }
        public Seed title(String title) { this.title = title; return this; }
        public Seed startAt(LocalDateTime startAt) { this.startAt = startAt; return this; }
        public Seed days(int days) { this.days = days; return this; }
        public Seed items(int itemCount) { this.itemCount = itemCount; return this; }
        public Seed reviewRequestedAt(LocalDateTime at) { this.reviewRequestedAt = at; return this; }
        public Seed sentAt(LocalDateTime at) { this.signatureRequestedAt = at; return this; }
        public Seed deadlineAt(LocalDateTime at) { this.signatureDeadlineAt = at; return this; }
        public Seed brandSignedAt(LocalDateTime at) { this.brandSignedAt = at; return this; }
        public Seed creatorSignedAt(LocalDateTime at) { this.creatorSignedAt = at; return this; }
        public Seed asOf(LocalDateTime at) { this.signatureAsOf = at; return this; }
        public Seed createdAt(LocalDateTime at) { this.createdAt = at; return this; }
    }

    private Contract persist(Seed s) {
        Contract saved = transactionTemplate.execute(tx -> {
            Contract c = Contract.createDraft(s.owner.market(), s.counterparty, null);
            LocalDateTime endAt = s.startAt.plusDays(s.days - 1).withHour(23).withMinute(55);
            c.updateTerms(s.title, s.startAt, endAt, 1_500_000, FixedFeeTrigger.GROUP_BUY_ENDED, s.reviewRequestedAt,
                    1, 2, 0, endAt.toLocalDate(), true, SecondaryUsePeriodType.UNLIMITED, null, false,
                    "겨울 시즌 리페어 라인 신규 런칭 건.");
            List<ContractItem> items = new ArrayList<>();
            for (int i = 0; i < s.itemCount; i++) items.add(item(i % 2 == 0 ? cream : serum, i));
            c.replaceItems(items);
            if (s.status != ContractStatus.DRAFT) {
                c.applyReviewRequested(nextNumber(), clauseVersion, "W2", s.reviewRequestedAt);
            }
            switch (s.status) {
                case DRAFT, REVIEW_PENDING -> { }
                case REVIEW_REJECTED -> c.rejectReview(ContractReviewRejectReason.AGREEMENT_MISMATCH.name(),
                        "2차 활용 기간이 무기한으로 되어 있습니다.", s.reviewRequestedAt.plusMinutes(13));
                default -> {
                    c.approveReview(s.signatureRequestedAt, s.signatureDeadlineAt, s.signatureRequestedAt.minusMinutes(80));
                    if (s.brandSignedAt != null || s.creatorSignedAt != null || s.signatureAsOf != null) {
                        c.updateSignatures(s.brandSignedAt, s.creatorSignedAt,
                                s.signatureAsOf != null ? s.signatureAsOf : now.minusHours(1));
                    }
                    switch (s.status) {
                        case CONCLUDED -> c.conclude(now.minusMinutes(30));
                        case EXPIRED -> c.expire(now.minusHours(3));
                        case CANCELED -> c.applyCanceled(ContractCloseReasonCode.SCHEDULE_CHANGE.name(), null, now.minusHours(3));
                        default -> { }
                    }
                }
            }
            return contracts.saveAndFlush(c);
        });
        if (s.status == ContractStatus.DECLINED) {
            // 거절은 엔티티 메서드가 없다 — 스튜디오가 쓰는 조건부 UPDATE를 그대로 태운다.
            transactionTemplate.execute(tx -> contracts.declineByCreator(saved.getId(), s.counterparty.getId(),
                    ContractDeclineReason.SCHEDULE_MISMATCH.name(), "일정이 맞지 않습니다.", now.minusHours(3)));
        }
        if (s.createdAt != null) {
            jdbc.update("UPDATE contract SET created_at = ? WHERE contract_id = ?", s.createdAt, saved.getId());
        }
        return contracts.findById(saved.getId()).orElseThrow();
    }

    private ContractItem item(Product product, int sortOrder) {
        return ContractItem.builder()
                .product(product)
                .productName(product.getName())
                .regularPrice(product.getRegularPrice())
                .groupBuyPrice(product == cream ? 33_600 : 28_000)
                .rewardRate(new BigDecimal(product == cream ? "45.0" : "15.0"))
                .minQuantity(product == cream ? 200 : 300)
                .sortOrder(sortOrder)
                .build();
    }

    private String nextNumber() {
        return "CTR-20260813-%03d".formatted(700 + ++numberSeq);
    }

    /** 체결 문서를 API를 거치지 않고 등록한다 — 업로드 경로 자체를 보지 않는 테스트용. */
    protected void attach(Contract contract, ContractDocumentType type) {
        documents.save(ContractDocument.builder().contract(contract).documentType(type)
                .s3Key("contracts/" + contract.getId() + "/documents/" + type + ".pdf")
                .originalName(contract.getContractNumber() + "_" + type + ".pdf").sizeBytes(1_200_000L)
                .uploadedAt(now.minusMinutes(10)).uploadedBy(admin.getId()).build());
    }

    protected void resendRequested(Contract contract, ContractActorType requester, LocalDateTime at) {
        Long requesterId = requester == ContractActorType.CREATOR ? contract.getCreator().getId() : contract.getMarket().getId();
        resends.save(ContractResendRequest.of(contract, requester, requesterId, at));
    }

    protected List<ContractHistory> historyOf(Contract contract) {
        return histories.findByContractIdOrderByOccurredAtAscIdAsc(contract.getId());
    }

    protected Contract reload(Contract contract) {
        return contracts.findById(contract.getId()).orElseThrow();
    }

    // ------------------------------------------------------------------ 픽스처

    protected Creator createCreator(String showroomName, String accountId) {
        Users owner = users.save(new Users("creator-" + accountId, showroomName, accountId + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.CREATOR, now, now));
        return createCreator(owner, showroomName, accountId);
    }

    private Creator createCreator(Users owner, String showroomName, String accountId) {
        return creators.save(Creator.builder().user(owner).snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/" + accountId).accountId(accountId).followerCount(12_000)
                .businessEmail(accountId + "-biz@showroomz.test").showroomName(showroomName).realName("김" + accountId)
                .businessType(CreatorBusinessType.INDIVIDUAL).build());
    }

    protected Product createProduct(BrandFixture.Brand owner, String name, int regularPrice) {
        Category category = new Category();
        category.setName("뷰티 " + name + " " + owner.marketId());
        categories.save(category);
        Product product = new Product();
        product.setMarket(owner.market());
        product.setCategory(category);
        product.setName(name);
        product.setRegularPrice(regularPrice);
        product.setSalePrice(regularPrice);
        product.setDisplayStatus(ProductDisplayStatus.DISPLAY);
        return products.save(product);
    }

    /** Flyway가 꺼진 통합 테스트 프로필에는 V120 조항 seed가 없다 — 최소 버전을 직접 적재한다. */
    private ContractClauseVersion seedClauseVersion() {
        ContractClauseVersion version = ContractClauseVersion.builder().versionNumber("1.0")
                .effectiveDate(LocalDate.now().minusDays(1)).status(ContractClauseVersionStatus.EFFECTIVE)
                .clauses(new ArrayList<>()).build();
        version.getClauses().add(ContractClause.builder().clauseVersion(version).code("PRICE_POLICY").sortOrder(1)
                .summaryTitle("가격 정책").summaryDescription("공구 기간 중 타 채널 최저가 준수")
                .fullTitle("제3조 최저가 정책").fullBody("브랜드는 공구 기간 중 동일 상품을 공구가보다 낮은 가격으로 판매하지 않는다.")
                .build());
        return clauseVersions.save(version);
    }

    // ------------------------------------------------------------------ 어드민 요청

    protected ResultActions list(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(BASE).header(HttpHeaders.AUTHORIZATION, adminToken);
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return mockMvc.perform(request);
    }

    protected ResultActions summary() throws Exception {
        return mockMvc.perform(get(BASE + "/summary").header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    protected ResultActions detail(Contract contract) throws Exception {
        return mockMvc.perform(get(BASE + "/" + contract.getId()).header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    protected ResultActions approve(Contract contract, ApproveRequest request) throws Exception {
        return mockMvc.perform(post(BASE + "/" + contract.getId() + "/review/approve")
                .header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON).content(exactJson(request)));
    }

    /** C1 체크 3종을 전부 확인한 승인 요청. */
    protected ApproveRequest checked(LocalDateTime sentAt, LocalDateTime deadlineAt) {
        return new ApproveRequest(true, true, true, sentAt, deadlineAt);
    }

    protected ResultActions reject(Contract contract, ContractReviewRejectReason code, String detail) throws Exception {
        return mockMvc.perform(post(BASE + "/" + contract.getId() + "/review/reject")
                .header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new RejectRequest(code, detail))));
    }

    protected ResultActions signatures(Contract contract, LocalDateTime brandSignedAt, LocalDateTime creatorSignedAt,
                                       long version) throws Exception {
        return mockMvc.perform(put(BASE + "/" + contract.getId() + "/signatures")
                .header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON)
                .content(exactJson(new SignatureRequest(brandSignedAt, creatorSignedAt, version))));
    }

    protected ResultActions conclude(Contract contract) throws Exception {
        return mockMvc.perform(post(BASE + "/" + contract.getId() + "/conclude").header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    protected ResultActions expire(Contract contract, Boolean dashboardRechecked) throws Exception {
        return mockMvc.perform(post(BASE + "/" + contract.getId() + "/expire")
                .header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new ExpireRequest(dashboardRechecked))));
    }

    protected ResultActions handleResend(Contract contract) throws Exception {
        return mockMvc.perform(post(BASE + "/" + contract.getId() + "/resend/handle").header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    protected ResultActions presign(Contract contract, ContractDocumentType type, String contentType, String fileName)
            throws Exception {
        return mockMvc.perform(post(BASE + "/" + contract.getId() + "/documents/presign")
                .header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new PresignRequest(type, contentType, fileName))));
    }

    protected ResultActions register(Contract contract, ContractDocumentType type, String s3Key, String fileName)
            throws Exception {
        return mockMvc.perform(post(BASE + "/" + contract.getId() + "/documents")
                .header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new RegisterDocumentRequest(type, s3Key, fileName, 1_200_000L))));
    }

    /** presign → (S3 PUT은 모의) → 등록. 시안 B4의 [파일 선택] 한 번에 해당한다. */
    protected ResultActions upload(Contract contract, ContractDocumentType type, String fileName) throws Exception {
        String presigned = presign(contract, type, "application/pdf", fileName)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return register(contract, type, readString(presigned, "$.s3Key"), fileName);
    }

    protected ResultActions deleteDocument(Contract contract, ContractDocumentType type) throws Exception {
        return mockMvc.perform(delete(BASE + "/" + contract.getId() + "/documents/" + type).header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    protected ResultActions downloadDocument(Contract contract, ContractDocumentType type) throws Exception {
        return mockMvc.perform(get(BASE + "/" + contract.getId() + "/documents/" + type).header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    protected ResultActions draft(Contract contract) throws Exception {
        return mockMvc.perform(get(BASE + "/" + contract.getId() + "/document-draft").header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    /**
     * 운영자가 옮겨 적는 시각을 초 미만까지 그대로 보낸다. 앱 ObjectMapper는 LocalDateTime을 초 단위로 잘라 쓰므로,
     * 「방금」 값을 보내면 같은 초 안의 검토 요청·발송 시각보다 앞선 값으로 바뀌어 순서 검증에 걸린다.
     */
    protected String exactJson(Object value) {
        try {
            return EXACT_JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper EXACT_JSON = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // ------------------------------------------------------------------ 반대편 서피스

    protected ResultActions sellerDetail(Contract contract) throws Exception {
        return mockMvc.perform(get(SELLER_CONTRACTS + "/" + contract.getId()).header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    protected ResultActions creatorDetail(Contract contract) throws Exception {
        return mockMvc.perform(get(CREATOR_CONTRACTS + "/" + contract.getId()).header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    // ------------------------------------------------------------------ 응답 읽기

    protected String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    protected String readString(String json, String path) {
        return JsonPath.parse(json).read(path, String.class);
    }

    /**
     * 응답 시각 읽기. JacksonConfig가 LocalDateTime을 {@code yyyy-MM-dd'T'HH:mm:ss'Z'}로 내린다 —
     * 끝의 Z는 문자일 뿐 서버 로컬 시각이고, 초 미만은 잘린다.
     */
    protected LocalDateTime time(String json, String path) {
        String value = readString(json, path);
        return LocalDateTime.parse(value.endsWith("Z") ? value.substring(0, value.length() - 1) : value);
    }

    protected long readLong(String json, String path) {
        return JsonPath.parse(json).read(path, Number.class).longValue();
    }

    protected long versionOf(Contract contract) {
        return reload(contract).getVersion();
    }
}
