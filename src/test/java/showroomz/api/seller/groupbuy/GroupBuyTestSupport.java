package showroomz.api.seller.groupbuy;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.repository.ContractHistoryRepository;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.contract.type.SecondaryUsePeriodType;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyAdminSuspensionRepository;
import showroomz.domain.groupbuy.repository.GroupBuyChangeRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyExtensionRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyHistoryRepository;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.service.GroupBuyFactory;
import showroomz.domain.groupbuy.service.GroupBuyLifecycleService;
import showroomz.domain.groupbuy.type.AdminSuspensionKind;
import showroomz.domain.groupbuy.type.AdminSuspensionStatus;
import showroomz.domain.groupbuy.type.ChangeRequestStatus;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.SuspensionReasonClause;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.type.PostType;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.support.BrandFixture;
import showroomz.support.IntegrationTestSupport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * §29·§30 파트너센터 공구 관리 테스트의 공통 배선.
 *
 * <p>브랜드 1곳 · 연결됨 인플루언서 1명(PAIR 스레드 포함) · 진열 상품 2건을 깐다. 공구는 <b>체결 계약을 적재한 뒤
 * 실제 생성 경로({@link GroupBuyFactory})를 태워</b> 만든다 — 번호·이력·상품 동기화가 운영과 같은 코드로 생긴다.
 *
 * <p>생성 이후의 상태(진행중·종료 등)는 다른 서피스(스튜디오·어드민)나 스케줄러가 만드는 값이라 SQL로 직접 옮긴다.
 * 파트너 화면의 분기가 남의 API 순서에 묶이지 않게 하기 위해서다.
 */
public abstract class GroupBuyTestSupport extends IntegrationTestSupport {

    protected static final String GROUP_BUYS = "/v1/seller/group-buys";

    @Autowired protected UserRepository userRepository;
    @Autowired protected CreatorRepository creatorRepository;
    @Autowired protected ConnectionRepository connectionRepository;
    @Autowired protected MessageThreadRepository messageThreadRepository;
    @Autowired protected CategoryRepository categoryRepository;
    @Autowired protected ProductRepository productRepository;
    @Autowired protected ContractRepository contractRepository;
    @Autowired protected ContractHistoryRepository contractHistoryRepository;
    @Autowired protected GroupBuyRepository groupBuyRepository;
    @Autowired protected GroupBuyHistoryRepository groupBuyHistoryRepository;
    @Autowired protected GroupBuyExtensionRequestRepository extensionRequestRepository;
    @Autowired protected GroupBuyChangeRequestRepository changeRequestRepository;
    @Autowired protected GroupBuyAdminSuspensionRepository adminSuspensionRepository;
    @Autowired protected GroupBuyPostRepository groupBuyPostRepository;
    @Autowired protected PostRepository postRepository;
    @Autowired protected GroupBuyFactory groupBuyFactory;
    @Autowired protected GroupBuyLifecycleService lifecycleService;
    @Autowired protected JdbcTemplate jdbc;

    protected BrandFixture.Brand brand;
    protected String brandToken;
    protected Creator creator;
    protected Connection connection;
    protected Long pairThreadId;
    protected Product cream;
    protected Product serum;

    private int contractSeq;

    @BeforeEach
    void setUpGroupBuyFixtures() {
        brand = fixture.createBrand("brand@showroomz.test", "글로우랩");
        brandToken = sellerToken(brand.seller());
        creator = createCreator("글로우_지민", "jimin");
        connection = connect(brand, creator);
        pairThreadId = messageThreadRepository.save(MessageThread.openFor(connection)).getId();
        cream = createProduct(brand, "글로우 크림 50ml", 34_000);
        serum = createProduct(brand, "글로우 세럼 30ml", 30_000);
    }

    // ------------------------------------------------------------------ 요청

    protected ResultActions list(String query) throws Exception {
        return mockMvc.perform(get(GROUP_BUYS + (query == null ? "" : "?" + query))
                .header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    protected ResultActions summary() throws Exception {
        return mockMvc.perform(get(GROUP_BUYS + "/summary").header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    protected ResultActions detail(long groupBuyId) throws Exception {
        return mockMvc.perform(get(GROUP_BUYS + "/" + groupBuyId).header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    protected ResultActions action(long groupBuyId, String path, Object body) throws Exception {
        var request = post(GROUP_BUYS + "/" + groupBuyId + "/" + path).header(HttpHeaders.AUTHORIZATION, brandToken);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : toJson(body));
        }
        return mockMvc.perform(request);
    }

    // ------------------------------------------------------------------ 적재

    /** 체결 계약 1건 + 실제 생성 경로로 만든 공구(PREPARING). 기간은 시작 D+10 · 8일(시안 8일 공구). */
    protected GroupBuy seedPreparing() {
        LocalDateTime startAt = LocalDateTime.now().plusDays(10).withHour(10).withMinute(0).withSecond(0).withNano(0);
        return seed(brand, creator, "글로우 크림 앵콜 공구", startAt, startAt.plusDays(7).withHour(23).withMinute(55));
    }

    /**
     * 원하는 상태의 공구. 기간은 상태에 맞춘다 — 진행중이면 이미 시작했고 아직 안 끝났으며,
     * 종결 3종이면 이미 끝났다.
     */
    protected GroupBuy seedIn(GroupBuyStatus status) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime startAt;
        LocalDateTime endAt;
        if (status == GroupBuyStatus.PREPARING || status == GroupBuyStatus.READY) {
            startAt = now.plusDays(10);
            endAt = startAt.plusDays(7);
        } else if (status.isSelling()) {
            startAt = now.minusDays(3);
            endAt = now.plusDays(4);
        } else {
            startAt = now.minusDays(10);
            endAt = now.minusDays(3);
        }
        GroupBuy groupBuy = seed(brand, creator, "글로우 크림 앵콜 공구", startAt, endAt);
        moveTo(groupBuy.getId(), status);
        return reload(groupBuy.getId());
    }

    protected GroupBuy seed(BrandFixture.Brand owner, Creator counterparty, String title,
                            LocalDateTime startAt, LocalDateTime endAt) {
        Product first = owner == brand ? cream : createProduct(owner, "상품 " + title, 30_000);
        Product second = owner == brand ? serum : null;
        return transactionTemplate.execute(tx -> {
            Contract contract = Contract.createDraft(owner.market(), counterparty, null);
            contract.updateTerms(title, startAt, endAt,
                    300_000, FixedFeeTrigger.POST_REGISTERED, LocalDateTime.now().withNano(0),
                    1, 1, 3, endAt.toLocalDate().plusDays(3),
                    true, SecondaryUsePeriodType.FIXED, 12, false, null);
            List<ContractItem> items = new ArrayList<>();
            items.add(item(first, 27_200, "12.0", 300));
            if (second != null) {
                items.add(item(second, 24_000, "10.0", 200));
            }
            contract.replaceItems(items);
            LocalDateTime now = LocalDateTime.now().withNano(0);
            contract.applyReviewRequested(nextContractNumber(), null, null, now.minusDays(5));
            contract.approveReview(now.minusDays(4), now.plusDays(3), now.minusDays(4));
            contract.updateSignatures(now.minusDays(2), now.minusDays(2), now.minusDays(2));
            contract.conclude(now.minusDays(1));
            Contract saved = contractRepository.saveAndFlush(contract);
            return groupBuyFactory.createFromConcludedContract(saved, now.minusDays(1));
        });
    }

    /** 다른 서피스·스케줄러가 만드는 상태를 SQL로 옮긴다. */
    protected void moveTo(Long groupBuyId, GroupBuyStatus status) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        GroupBuyCloseType closeType = switch (status) {
            case ENDED, SETTLED -> GroupBuyCloseType.COMPLETED;
            case SUSPENDED -> GroupBuyCloseType.SUSPENDED;
            default -> null;
        };
        jdbc.update("UPDATE group_buy SET status = ?, "
                        + "ready_at = CASE WHEN ? THEN ? ELSE ready_at END, "
                        + "opened_at = CASE WHEN ? THEN start_at ELSE opened_at END, "
                        + "ended_at = CASE WHEN ? THEN end_at ELSE ended_at END, "
                        + "close_type = ?, "
                        + "fulfillment_due_at = CASE WHEN ? THEN DATEADD('DAY', 3, end_at) ELSE fulfillment_due_at END "
                        + "WHERE group_buy_id = ?",
                status.name(),
                status != GroupBuyStatus.PREPARING, now.minusDays(1),
                status.isSelling() || status.isTerminal(),
                status.isTerminal(),
                closeType == null ? null : closeType.name(),
                status == GroupBuyStatus.ENDED || status == GroupBuyStatus.SETTLED,
                groupBuyId);
    }

    protected void confirmStockDirectly(Long groupBuyId) {
        jdbc.update("UPDATE group_buy SET stock_confirmed_at = ?, stock_confirmed_by = ? WHERE group_buy_id = ?",
                LocalDateTime.now().withNano(0).minusDays(1), brand.seller().getId(), groupBuyId);
    }

    /** 인플루언서 게시물 — 스튜디오·어드민이 만드는 값이다. {@code hidden}이면 운영자 숨김 상태다. */
    protected GroupBuyPost seedPost(Long groupBuyId, GroupBuyPostReviewStatus reviewStatus, boolean hidden) {
        return transactionTemplate.execute(tx -> {
            GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId).orElseThrow();
            Post post = Post.draft(groupBuy.getCreator(), "여름 한정 앵콜 공구 — 크림·세럼 세트", null);
            ReflectionTestUtils.setField(post, "postType", PostType.GROUP_BUY);
            postRepository.save(post);
            LocalDateTime now = LocalDateTime.now().withNano(0);
            boolean reviewed = reviewStatus == GroupBuyPostReviewStatus.APPROVED
                    || reviewStatus == GroupBuyPostReviewStatus.REJECTED;
            return groupBuyPostRepository.save(GroupBuyPost.builder()
                    .post(post)
                    .groupBuy(groupBuy)
                    .title("글로우 크림 앵콜 공구 오픈")
                    .reviewStatus(reviewStatus)
                    .submittedAt(reviewStatus == GroupBuyPostReviewStatus.DRAFT ? null : now.minusHours(5))
                    .reviewedAt(reviewed ? now.minusHours(2) : null)
                    .rejectReasonCode(reviewStatus == GroupBuyPostReviewStatus.REJECTED ? "PRICE_MISMATCH" : null)
                    .rejectReasonDetail(reviewStatus == GroupBuyPostReviewStatus.REJECTED ? "공구가 표기가 계약과 다릅니다." : null)
                    .hiddenAt(hidden ? now.minusHours(1) : null)
                    .hiddenReasonCode(hidden ? "AD_DISCLOSURE" : null)
                    .hiddenReasonDetail(hidden ? "대가관계 표기 누락" : null)
                    .build());
        });
    }

    protected GroupBuyChangeRequest seedPendingRequest(Long groupBuyId, ChangeRequestType type,
                                                       GroupBuyActorType requesterType, String reasonCode) {
        return transactionTemplate.execute(tx -> {
            GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId).orElseThrow();
            return changeRequestRepository.save(GroupBuyChangeRequest.builder()
                    .groupBuy(groupBuy)
                    .requestType(type)
                    .requesterType(requesterType)
                    .requesterId(requesterType == GroupBuyActorType.CREATOR ? creator.getId() : brand.seller().getId())
                    .reasonCode(reasonCode)
                    .statusAtRequest(groupBuy.getStatus())
                    .status(ChangeRequestStatus.PENDING)
                    .requestedAt(LocalDateTime.now().withNano(0).minusHours(3))
                    .build());
        });
    }

    /** 운영자 직권 중단 사전 통지(M6) — 공구는 중단 예정이 된다. */
    protected GroupBuyAdminSuspension seedNotice(Long groupBuyId, LocalDateTime appealDeadlineAt) {
        moveTo(groupBuyId, GroupBuyStatus.SUSPENSION_SCHEDULED);
        return transactionTemplate.execute(tx -> adminSuspensionRepository.save(GroupBuyAdminSuspension.builder()
                .groupBuy(groupBuyRepository.findById(groupBuyId).orElseThrow())
                .kind(AdminSuspensionKind.NOTICE)
                .reasonClause(SuspensionReasonClause.ART17_1_LAW)
                .noticeBody("게시물 본문에 의약품 오인 표현이 포함되어 있습니다.")
                .noticedAt(LocalDateTime.now().withNano(0).minusDays(1))
                .noticedBy(1L)
                .appealDeadlineAt(appealDeadlineAt)
                .executeScheduledAt(appealDeadlineAt.plusDays(1))
                .status(AdminSuspensionStatus.NOTICED)
                .build()));
    }

    protected GroupBuy reload(Long groupBuyId) {
        return groupBuyRepository.findById(groupBuyId).orElseThrow();
    }

    protected ProductGroupBuyStatus productStatus(Product product) {
        return productRepository.findById(product.getProductId()).orElseThrow().getGroupBuyStatus();
    }

    // ------------------------------------------------------------------ 픽스처

    protected ContractItem item(Product product, int groupBuyPrice, String rewardRate, int minQuantity) {
        return ContractItem.builder()
                .product(product)
                .productName(product.getName())
                .regularPrice(product.getRegularPrice())
                .groupBuyPrice(groupBuyPrice)
                .rewardRate(new BigDecimal(rewardRate))
                .minQuantity(minQuantity)
                .sortOrder(0)
                .build();
    }

    protected Creator createCreator(String showroomName, String accountId) {
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
                .businessType(CreatorBusinessType.INDIVIDUAL)
                .build());
    }

    protected Connection connect(BrandFixture.Brand owner, Creator counterparty) {
        Connection pair = Connection.requestPair(owner.market(), counterparty);
        pair.markConnected();
        return connectionRepository.save(pair);
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

    private String nextContractNumber() {
        return "CTR-%s-%03d".formatted(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), 900 + ++contractSeq);
    }
}
