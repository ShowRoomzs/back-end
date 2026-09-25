package showroomz.api.admin.groupbuy;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import showroomz.api.seller.groupbuy.GroupBuyTestSupport;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.entity.GroupBuyPostRevision;
import showroomz.domain.groupbuy.repository.GroupBuyFulfillmentCheckRepository;
import showroomz.domain.groupbuy.repository.GroupBuyPostRevisionRepository;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyPostRevisionKind;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * §32 어드민 공구 관리 테스트의 공통 배선 — 파트너 픽스처(브랜드 1 · 연결된 인플루언서 1 · 상품 2)를 그대로 쓰고
 * 운영자 「김운영」의 토큰으로 {@code /v1/admin/group-buys}를 부른다.
 *
 * <p>브랜드·인플루언서가 만드는 사실(요청 · 게시물 · 리비전 · 연장)은 리포지토리·SQL로 적재한다 — 어드민 화면의 분기가
 * 남의 API 순서에 묶이지 않게 하기 위해서다.
 */
abstract class AdminGroupBuyTestSupport extends GroupBuyTestSupport {

    protected static final String ADMIN = "/v1/admin/group-buys";
    protected static final String OPERATOR_NAME = "김운영";

    @Autowired protected GroupBuyPostRevisionRepository revisionRepository;
    @Autowired protected GroupBuyFulfillmentCheckRepository fulfillmentCheckRepository;
    @Autowired protected BusinessCalendar businessCalendar;

    protected Seller operator;
    protected String operatorToken;

    @BeforeEach
    void setUpOperator() {
        operator = fixture.createAdmin("groupbuy-operator@showroomz.test", OPERATOR_NAME);
        operatorToken = adminToken(operator);
    }

    // ------------------------------------------------------------------ 요청

    protected ResultActions adminList(String query) throws Exception {
        return mockMvc.perform(get(ADMIN + (query == null ? "" : "?" + query))
                .header(HttpHeaders.AUTHORIZATION, operatorToken));
    }

    protected ResultActions adminSummary() throws Exception {
        return mockMvc.perform(get(ADMIN + "/summary").header(HttpHeaders.AUTHORIZATION, operatorToken));
    }

    protected ResultActions adminDetail(long groupBuyId) throws Exception {
        return mockMvc.perform(get(ADMIN + "/" + groupBuyId).header(HttpHeaders.AUTHORIZATION, operatorToken));
    }

    protected ResultActions adminGet(long groupBuyId, String path) throws Exception {
        return mockMvc.perform(get(ADMIN + "/" + groupBuyId + "/" + path).header(HttpHeaders.AUTHORIZATION, operatorToken));
    }

    protected ResultActions adminAction(long groupBuyId, String path, Object body) throws Exception {
        MockHttpServletRequestBuilder request = post(ADMIN + "/" + groupBuyId + "/" + path)
                .header(HttpHeaders.AUTHORIZATION, operatorToken);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON)
                    .content(body instanceof String s ? s : toJson(body));
        }
        return mockMvc.perform(request);
    }

    // ------------------------------------------------------------------ 적재

    /** 진행중 공구 — 종료가 3주 뒤라 직권 중단 통지 창(3영업일 + 소명 기한)이 반드시 있다. */
    protected GroupBuy seedInProgressLong() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        GroupBuy groupBuy = seed(brand, creator, "여름 수분 세럼 공구", now.minusDays(2), now.plusDays(21));
        moveTo(groupBuy.getId(), GroupBuyStatus.IN_PROGRESS);
        return reload(groupBuy.getId());
    }

    /** 오픈 승인 대기(B1) — 준비중 ∧ 게시물 PENDING ∧ 제출 판본 1. */
    protected GroupBuyPost seedPendingReview(GroupBuy groupBuy) {
        GroupBuyPost post = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.PENDING,
                false);
        seedRevision(post, 1, GroupBuyPostRevisionKind.SUBMITTED, post.getSubmittedAt());
        return post;
    }

    /** 승인된 게시물 + 판본(제출 1 · 수정 n). 마지막 판본 번호를 돌려준다. */
    protected int seedApprovedWithEdits(GroupBuyPost post, int edits) {
        LocalDateTime at = LocalDateTime.now().withNano(0).minusDays(3);
        seedRevision(post, 1, GroupBuyPostRevisionKind.SUBMITTED, at);
        for (int i = 1; i <= edits; i++) {
            seedRevision(post, 1 + i, GroupBuyPostRevisionKind.EDITED, at.plusHours(i));
        }
        return 1 + edits;
    }

    protected GroupBuyPostRevision seedRevision(GroupBuyPost post, int revisionNo, GroupBuyPostRevisionKind kind,
                                                LocalDateTime at) {
        return transactionTemplate.execute(tx -> revisionRepository.save(GroupBuyPostRevision.builder()
                .groupBuyPost(groupBuyPostRepository.findById(post.getPostId()).orElseThrow())
                .revisionNo(revisionNo)
                .kind(kind)
                .title("판본 " + revisionNo)
                .content("판본 " + revisionNo + " 본문")
                .createdAt(at)
                .createdBy(creator.getId())
                .build()));
    }

    protected GroupBuyChangeRequest seedSellerRequest(GroupBuy groupBuy, ChangeRequestType type) {
        return seedPendingRequest(groupBuy.getId(), type, GroupBuyActorType.SELLER,
                type == ChangeRequestType.SUSPEND ? "QUALITY_ISSUE" : "STOCK_OUT");
    }

    protected GroupBuyExtensionRequest seedExtension(Long groupBuyId, int days) {
        return transactionTemplate.execute(tx -> extensionRequestRepository.save(GroupBuyExtensionRequest.request(
                groupBuyRepository.findById(groupBuyId).orElseThrow(), brand.seller().getId(), days, "수요 증가",
                LocalDateTime.now().withNano(0).minusHours(2))));
    }

    protected GroupBuyPost loadPost(Long groupBuyId) {
        return inTransaction(() -> {
            GroupBuyPost post = groupBuyPostRepository.findByGroupBuyId(groupBuyId).orElseThrow();
            post.getPost().getStatus();
            return post;
        });
    }
}
