package showroomz.api.admin.brand;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.repository.ChangeRequestRepository;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.history.entity.SellerApplicationHistory;
import showroomz.domain.history.repository.SellerApplicationHistoryRepository;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.member.brand.entity.BrandWithdrawalRequest;
import showroomz.domain.member.brand.repository.BrandAdminMemoRepository;
import showroomz.domain.member.brand.repository.BrandSanctionRepository;
import showroomz.domain.member.brand.repository.BrandWithdrawalRequestRepository;
import showroomz.domain.member.brand.service.BrandSanctionService;
import showroomz.domain.member.brand.service.BrandWithdrawalEligibility;
import showroomz.domain.member.brand.service.BrandWithdrawalService;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.seller.entity.SellerApplication;
import showroomz.domain.member.seller.repository.SellerApplicationRepository;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.message.type.ParticipantType;
import showroomz.domain.product.entity.Product;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementClawback;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.repository.SettlementClawbackRepository;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.ClawbackSide;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 어드민 브랜드 상세 시나리오(52 테스트 시나리오) 공용 배선. 정산 시드({@link SettlementTestSupport})가 진행 중 공구 1건 · 인플루언서 1명 ·
 * 소비자 1명 · 운영자 「김운영」을 깐 시드 브랜드 {@code brand}(글로우랩)를 준다 — 이 브랜드는 진행 중 공구가 있어 탈퇴 조건 ①에 걸린다.
 * 탈퇴 승인 · 반려 · 집계 격리가 필요한 케이스는 {@link #cleanBrand}로 아무것도 없는 브랜드를 따로 만든다.
 *
 * <p>다른 화면 · 배치가 만드는 상태(클레임 단계 · 정산 상태 · 시각)는 SQL 로 옮긴다 — 이 테스트들의 관심사는 그 상태를 브랜드 상세가 어떻게
 * 읽느냐이다.
 */
abstract class AdminBrandTestSupport extends SettlementTestSupport {

    protected static final String BRANDS = "/v1/admin/brands";
    protected static final String ACCOUNT_RAW = "110123456789";

    @Autowired protected BrandSanctionRepository sanctionRepository;
    @Autowired protected BrandWithdrawalRequestRepository withdrawalRepository;
    @Autowired protected BrandAdminMemoRepository memoRepository;
    @Autowired protected BrandWithdrawalService withdrawalService;
    @Autowired protected BrandSanctionService sanctionService;
    @Autowired protected BrandWithdrawalEligibility eligibility;
    @Autowired protected OperatorChannelService operatorChannels;
    @Autowired protected ChangeRequestRepository memberChangeRequests;
    @Autowired protected SellerApplicationRepository sellerApplications;
    @Autowired protected SellerApplicationHistoryRepository applicationHistories;
    @Autowired protected ProductInquiryRepository productInquiries;
    @Autowired protected SettlementTaxDocumentRepository taxDocuments;
    @Autowired protected SettlementClawbackRepository clawbacks;

    private int changeRequestSeq;
    private int clawbackSeq;

    // ------------------------------------------------------------------ 경로 · 요청

    protected String base(Long marketId) {
        return BRANDS + "/" + marketId;
    }

    protected String base() {
        return base(brand.marketId());
    }

    protected ResultActions brandDetail(Long marketId) throws Exception {
        return adminGet(base(marketId));
    }

    protected JsonNode brandDetailJson(Long marketId) throws Exception {
        return json(brandDetail(marketId));
    }

    protected ResultActions suspend(Long marketId, String reason, String detail) throws Exception {
        Map<String, Object> body = detail == null ? Map.of("reason", reason) : Map.of("reason", reason, "reasonDetail", detail);
        return adminPost(base(marketId) + "/suspend", body);
    }

    protected ResultActions release(Long marketId, Map<String, Object> body) throws Exception {
        return adminPost(base(marketId) + "/release", body);
    }

    protected ResultActions approve(Long marketId, Long requestId) throws Exception {
        return adminPost(base(marketId) + "/withdrawal-requests/" + requestId + "/approve", Map.of());
    }

    protected ResultActions reject(Long marketId, Long requestId, Map<String, Object> body) throws Exception {
        return adminPost(base(marketId) + "/withdrawal-requests/" + requestId + "/reject", body);
    }

    /** {@code token}이 null 이면 인증 헤더 없이 보낸다. */
    protected ResultActions getAs(String token, String url) throws Exception {
        var request = get(url);
        return mockMvc.perform(token == null ? request : request.header(HttpHeaders.AUTHORIZATION, token));
    }

    protected ResultActions postAs(String token, String url, Object body) throws Exception {
        var request = post(url);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, token);
        }
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        }
        return mockMvc.perform(request);
    }

    protected ResultActions patchAs(String token, String url, Object body) throws Exception {
        var request = patch(url).contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        return mockMvc.perform(token == null ? request : request.header(HttpHeaders.AUTHORIZATION, token));
    }

    protected ResultActions deleteAs(String token, String url) throws Exception {
        var request = delete(url);
        return mockMvc.perform(token == null ? request : request.header(HttpHeaders.AUTHORIZATION, token));
    }

    // ------------------------------------------------------------------ 브랜드 · 상태

    /** 공구 · 계약 · 주문 · 정산이 없는 입점 완료 브랜드 — 이름이 곧 이메일 앞부분이다. */
    protected BrandFixture.Brand cleanBrand(String name) {
        return fixture.createBrand(name + "@showroomz.test", name);
    }

    protected MarketStatus marketStatus(Long marketId) {
        return MarketStatus.valueOf(jdbc.queryForObject("SELECT status FROM market WHERE market_id = ?", String.class,
                marketId));
    }

    protected void setMarketStatus(Long marketId, MarketStatus status) {
        jdbc.update("UPDATE market SET status = ? WHERE market_id = ?", status.name(), marketId);
    }

    /** 기획 제외 경로({@code AdminMarketController})가 만든 정지를 흉내 낸다 — 상태만 있고 정지 행이 없다. */
    protected void suspendWithoutSanctionRow(Long marketId) {
        setMarketStatus(marketId, MarketStatus.SUSPENDED);
    }

    /** 훅을 거치지 않은 정지 행 — 확정 · 배치 경합의 틈새를 흉내 낸다. */
    protected void insertOpenSanction(Long marketId, String reason, boolean payoutHeld) {
        jdbc.update("INSERT INTO brand_sanction (market_id, reason, payout_held, suspended_at, suspended_by, "
                + "suspended_by_name) VALUES (?, ?, ?, ?, ?, '김운영')", marketId, reason, payoutHeld,
                LocalDateTime.now(), operator.getId());
    }

    /** 신청 화면의 관문을 지난 신청 행 — 조건 판정 없이 바로 만든다(승인 재검증 · 상태 경계용). */
    protected BrandWithdrawalRequest pendingRequestRow(Long marketId) {
        return withdrawalRepository.save(BrandWithdrawalRequest.open(marketId, null, null, "김담당", LocalDateTime.now()));
    }

    // ------------------------------------------------------------------ 운영팀 채널

    protected MessageThread operatorChannel(BrandFixture.Brand owner) {
        return inTransaction(() -> operatorChannels.requireMarketChannel(owner.market()));
    }

    /** 브랜드가 운영팀 채널에 쓴 글 — 운영팀 미확인으로 쌓인다. 스레드의 마지막 글 시각이 저장되도록 관리 상태의 스레드로 보낸다. */
    protected void brandSays(MessageThread channel, Seller sender, String content) {
        inTransaction(() -> messageThreadService.sendMessage(messageThreadRepository.findById(channel.getId()).orElseThrow(),
                ParticipantType.SELLER, sender.getId(), UUID.randomUUID().toString(), content, List.of()));
    }

    /** 목록 조회 — 검색어는 URL 문자열이 아니라 파라미터로 넣는다(한글 · 공백이 이중 인코딩되지 않게). */
    protected ResultActions adminList(String... params) throws Exception {
        var request = get(BRANDS).header(HttpHeaders.AUTHORIZATION, adminToken);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    protected List<Message> operatorChannelMessages(Long marketId) {
        Long threadId = jdbc.queryForObject("SELECT t.thread_id FROM message_thread t JOIN connection c "
                + "ON c.connection_id = t.connection_id WHERE c.type = 'OPERATOR_MARKET' AND c.market_id = ?",
                Long.class, marketId);
        return messagesOf(threadId);
    }

    protected List<MessageCardType> operatorCards(Long marketId) {
        return operatorChannelMessages(marketId).stream().map(Message::getCardType).filter(Objects::nonNull).toList();
    }

    // ------------------------------------------------------------------ 정산 · 지급

    /** {@code owner} 몫 정산 1건 — 시드 브랜드의 종료 공구로 만든 뒤 마켓만 옮긴다(집계는 정산의 market_id 로 센다). */
    protected Settlement settlementOf(BrandFixture.Brand owner, SettlementStatus status) {
        Settlement s = seedSettlement("정산 " + System.nanoTime(), 1_000_000, status);
        if (!owner.marketId().equals(brand.marketId())) {
            jdbc.update("UPDATE settlement SET market_id = ? WHERE settlement_id = ?", owner.marketId(), s.getId());
        }
        return settlement(s.getId());
    }

    protected void setPayout(Long settlementId, SettlementPayee payee, PayoutStatus status, long amount, String failCode,
                             LocalDate dueDate) {
        jdbc.update("UPDATE settlement_payout SET status = ?, amount = ?, fail_code = ?, due_date = ? "
                + "WHERE settlement_id = ? AND payee = ?", status.name(), amount, failCode, dueDate, settlementId,
                payee.name());
    }

    protected void brandTaxInvoice(Settlement s, TaxDocumentStatus status) {
        taxDocuments.save(SettlementTaxDocument.create(s.getId(), TaxDocumentType.BRAND_TAX_INVOICE, status, 100_000,
                10_000, "주식회사 글로우랩", "123-45-67890", "tax@글로우랩.com", LocalDate.now().plusDays(5),
                LocalDateTime.now()));
    }

    protected void clawback(Long marketId, Long originSettlementId, ClawbackSide side, String status) {
        SettlementClawback saved = clawbacks.save(SettlementClawback.builder()
                .clawbackNumber("CLW-%04d".formatted(++clawbackSeq)).seq(clawbackSeq).side(side)
                .originSettlementId(originSettlementId).reason("REFUND").refundAmount(10_000).amount(3_000)
                .marketId(marketId).creatorId(creator.getId()).createdAt(LocalDateTime.now()).build());
        if (!"PENDING".equals(status)) {
            jdbc.update("UPDATE settlement_clawback SET status = ? WHERE clawback_id = ?", status, saved.getId());
        }
    }

    // ------------------------------------------------------------------ 계약 · 상품 · 문의 · 연결

    /** 작성중 계약을 만들고 상태만 SQL 로 옮긴다 — 계약 생애는 계약 관리 테스트가 본다. */
    protected Contract contractIn(BrandFixture.Brand owner, Creator counterparty, ContractStatus status, boolean deleted) {
        Contract saved = inTransaction(() -> contractRepository.save(Contract.createDraft(owner.market(), counterparty, null)));
        jdbc.update("UPDATE contract SET status = ?, deleted_at = ? WHERE contract_id = ?", status.name(),
                deleted ? LocalDateTime.now() : null, saved.getId());
        return saved;
    }

    protected ProductInquiry inquiry(Product product) {
        return productInquiries.save(ProductInquiry.builder().user(consumer).product(product)
                .type(ProductInquiryType.OPTION).content("옵션 문의드립니다.").secret(false).build());
    }

    /** {@code owner}의 PAIR 연결 — 응답 시각을 고정한다. */
    protected void pairConnected(BrandFixture.Brand owner, Creator counterparty, LocalDateTime respondedAt) {
        Long id = connect(owner, counterparty).getId();
        jdbc.update("UPDATE connection SET responded_at = ? WHERE connection_id = ?", respondedAt, id);
    }

    protected void pairDisconnected(BrandFixture.Brand owner, Creator counterparty, LocalDateTime disconnectedAt) {
        Long id = connect(owner, counterparty).getId();
        jdbc.update("UPDATE connection SET status = 'DISCONNECTED', responded_at = ?, disconnected_at = ? "
                + "WHERE connection_id = ?", disconnectedAt.minusDays(30), disconnectedAt, id);
    }

    // ------------------------------------------------------------------ 변경 요청 · 입점 이력

    protected ChangeRequest changeRequest(BrandFixture.Brand owner, ChangeRequestType type, ChangeRequestStatus target) {
        ChangeRequest request = ChangeRequest.create("CHG-2026-%04d".formatted(++changeRequestSeq), owner.market(), type,
                "변경 사유", "김담당", "https://cdn.showroomz.test/evidence.jpg", "evidence.jpg", 1_000L);
        switch (target) {
            case APPROVED -> request.approve(operator.getId());
            case REJECTED -> request.reject(operator.getId(), "OTHER", "서류 확인 불가");
            case CANCELED -> request.cancel();
            default -> {
            }
        }
        return memberChangeRequests.save(request);
    }

    /** 입점 심사 승인 이력(재심사면 여러 번) — 처리자 · 시각을 고정한다. */
    protected void onboardingApproved(BrandFixture.Brand owner, Long processedBy, LocalDateTime... ats) {
        SellerApplication application = sellerApplications.save(
                SellerApplication.createFrom(owner.seller(), owner.market().getMarketName(), "02-1234-5678"));
        for (LocalDateTime at : ats) {
            SellerApplicationHistory history = applicationHistories.save(SellerApplicationHistory.builder()
                    .application(application)
                    .previousStatus(showroomz.api.seller.auth.type.SellerStatus.PENDING)
                    .newStatus(showroomz.api.seller.auth.type.SellerStatus.APPROVED)
                    .processedBy(processedBy).build());
            jdbc.update("UPDATE seller_application_history SET created_at = ? WHERE seller_application_history_id = ?",
                    at, history.getId());
        }
    }

    // ------------------------------------------------------------------ 응답 읽기

    /** 응답 시각 표기(LocalDateTime 에 'Z'를 붙인 초 단위)를 되읽는다. 값이 없으면 null. */
    protected static LocalDateTime at(JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode() ? null
                : LocalDateTime.parse(value.asText(), RESPONSE_DATE_TIME);
    }

    protected static final java.time.format.DateTimeFormatter RESPONSE_DATE_TIME =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    protected static List<String> textsAt(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.path(field).asText()));
        return values;
    }
}
