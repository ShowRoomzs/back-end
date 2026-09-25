package showroomz.api.seller.groupbuy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyFulfillmentCheck;
import showroomz.domain.groupbuy.repository.GroupBuyFulfillmentCheckRepository;
import showroomz.domain.groupbuy.repository.GroupBuyIssueRepository;
import showroomz.domain.groupbuy.service.GroupBuyCommandService;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.service.port.GroupBuyThreadGateway;
import showroomz.domain.groupbuy.type.FulfillmentResult;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyIssueStatus;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 브랜드 공구관리의 화면 데이터와 실행 결과를 HTTP → 서비스 → DB까지 검증한다.
 * 판매·3자 스레드는 아직 운영 구현이 없어 포트의 반환값만 대역으로 준다.
 * 현재 503을 성공으로 간주하지 않고, 포트 연결 후에도 유지돼야 할 공구 측 계약을 검증한다.
 */
@DisplayName("[기능] 브랜드 공구관리 — 판매 수치 · 종료 후 이슈 · 이행 확인")
class SellerGroupBuyFunctionalIntegrationTest extends GroupBuyTestSupport {

    @MockitoBean
    private GroupBuySalesReader salesReader;

    @MockitoBean
    private GroupBuyThreadGateway threadGateway;

    @Autowired
    private GroupBuyIssueRepository issueRepository;

    @Autowired
    private GroupBuyFulfillmentCheckRepository fulfillmentCheckRepository;

    @Autowired
    private GroupBuyCommandService groupBuyCommandService;

    @Test
    @DisplayName("A1/A3: 탭·검색·페이지를 함께 적용해도 행 중복이 없고 전체 카운트는 검색 결과와 구분된다")
    void filteredPaginationKeepsStableOrderAndSummaryCounts() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(10).withNano(0);
        GroupBuy first = seed(brand, creator, "여름 크림 1", start, start.plusDays(7));
        GroupBuy second = seed(brand, creator, "여름 크림 2", start.plusDays(1), start.plusDays(8));
        seed(brand, creator, "겨울 크림", start.plusDays(2), start.plusDays(9));
        seedIn(GroupBuyStatus.IN_PROGRESS);

        list("tab=PREPARING&keyword=여름&sort=START_AT_ASC&size=1&page=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].groupBuyId").value(contains(first.getId().intValue())))
                .andExpect(jsonPath("$.pageInfo.totalResults").value(2));
        list("tab=PREPARING&keyword=여름&sort=START_AT_ASC&size=1&page=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].groupBuyId").value(contains(second.getId().intValue())))
                .andExpect(jsonPath("$.pageInfo.totalResults").value(2));
        list("tab=PREPARING&keyword=없는공구&size=1&page=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        summary().andExpect(jsonPath("$.tabCounts.ALL").value(4))
                .andExpect(jsonPath("$.tabCounts.PREPARING").value(3));
    }

    @Test
    @DisplayName("B4g→B5d: 브랜드 조기 마감 요청을 운영자가 승인하면 브랜드 상세에 원래 종료일과 실제 종결을 나란히 제공한다")
    void earlyCloseApprovalIsVisibleToSeller() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        LocalDateTime plannedEnd = groupBuy.getEndAt();
        String reason = "재고 소진 확인";

        action(groupBuy.getId(), "early-close-request", Map.of("reasonCode", "STOCK_OUT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("IN_PROGRESS"));
        GroupBuyChangeRequest request = changeRequestRepository
                .findByGroupBuyIdOrderByRequestedAtDescIdDesc(groupBuy.getId()).getFirst();
        adminAction(groupBuy.getId(), "change-requests/" + request.getId() + "/approve",
                Map.of("decisionReason", reason)).andExpect(status().isOk());

        detail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("ENDED"))
                .andExpect(jsonPath("$.groupBuy.closeType").value("EARLY_CLOSED"))
                .andExpect(jsonPath("$.timeline.originalEndAt").value(plannedEnd + "Z"))
                .andExpect(jsonPath("$.closure.source").value("REQUEST"))
                .andExpect(jsonPath("$.closure.requester.type").value("SELLER"))
                .andExpect(jsonPath("$.closure.decisionReason").value(reason))
                .andExpect(jsonPath("$.sales").doesNotExist())
                .andExpect(jsonPath("$.permissions.canCheckFulfillment").value(true));
        assertThat(reload(groupBuy.getId()).getEndedAt()).isBefore(plannedEnd);
    }

    @Test
    @DisplayName("B7/B7a: 내 요청 승인 중단은 이슈 개설 불가, 운영자 긴급 직권 중단은 요청자 없이 이의 제기 가능")
    void suspensionOriginControlsSellerIssuePermission() throws Exception {
        GroupBuy requested = seedIn(GroupBuyStatus.IN_PROGRESS);
        action(requested.getId(), "suspension-request", Map.of("reasonCode", "QUALITY_ISSUE"))
                .andExpect(status().isOk());
        GroupBuyChangeRequest request = changeRequestRepository
                .findByGroupBuyIdOrderByRequestedAtDescIdDesc(requested.getId()).getFirst();
        adminAction(requested.getId(), "change-requests/" + request.getId() + "/approve",
                Map.of("decisionReason", "품질 문제 확인")).andExpect(status().isOk());
        detail(requested.getId()).andExpect(jsonPath("$.groupBuy.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.closure.source").value("REQUEST"))
                .andExpect(jsonPath("$.closure.requester.type").value("SELLER"))
                .andExpect(jsonPath("$.permissions.canOpenIssue").value(false));

        GroupBuy emergency = seedIn(GroupBuyStatus.IN_PROGRESS);
        adminAction(emergency.getId(), "admin-suspension/emergency",
                Map.of("emergencyReason", "DAMAGE_SURGE", "body", "피해 급증"))
                .andExpect(status().isOk());
        detail(emergency.getId()).andExpect(jsonPath("$.groupBuy.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.closure.source").value("ADMIN_EMERGENCY"))
                .andExpect(jsonPath("$.closure.requester").doesNotExist())
                .andExpect(jsonPath("$.adminSuspension.noticeBody").value("피해 급증"))
                .andExpect(jsonPath("$.permissions.canOpenIssue").value(true));
    }

    @Test
    @DisplayName("C1/C3: 입력 길이·필수 기타 메모 실패는 400이고 요청·이력은 남지 않는다")
    void rejectedInputDoesNotConsumeRequestOpportunity() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        int historyBefore = groupBuyHistoryRepository
                .findByGroupBuyIdOrderByOccurredAtAscIdAsc(groupBuy.getId()).size();

        action(groupBuy.getId(), "extension-request",
                Map.of("extensionDays", 1, "reason", "가".repeat(301)))
                .andExpect(status().isBadRequest());
        action(groupBuy.getId(), "early-close-request",
                Map.of("reasonCode", "ETC", "memo", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_REASON_MEMO_REQUIRED"));

        assertThat(extensionRequestRepository.findByGroupBuyId(groupBuy.getId())).isEmpty();
        assertThat(changeRequestRepository.findByGroupBuyIdOrderByRequestedAtDescIdDesc(groupBuy.getId())).isEmpty();
        assertThat(groupBuyHistoryRepository.findByGroupBuyIdOrderByOccurredAtAscIdAsc(groupBuy.getId()))
                .hasSize(historyBefore);
        detail(groupBuy.getId()).andExpect(jsonPath("$.permissions.canRequestExtension").value(true))
                .andExpect(jsonPath("$.permissions.canRequestEarlyClose").value(true));
    }

    @Test
    @DisplayName("B4→B4c→B4d: 브랜드 요청을 인플루언서가 수락하면 브랜드 상세의 종료일만 바뀌고 재요청은 막힌다")
    void extensionAcceptedAcrossSellerAndCreatorApis() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        LocalDateTime originalEnd = groupBuy.getEndAt();

        action(groupBuy.getId(), "extension-request", Map.of("extensionDays", 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extension.status").value("PENDING"))
                .andExpect(jsonPath("$.timeline.endAt").value(originalEnd + "Z"));
        creatorAction(groupBuy.getId(), "extension/acceptance")
                .andExpect(status().isOk());

        detail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.extension.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.extension.responseActorType").value("CREATOR"))
                .andExpect(jsonPath("$.timeline.originalEndAt").value(originalEnd + "Z"))
                .andExpect(jsonPath("$.timeline.endAt").value(originalEnd.plusDays(7) + "Z"))
                .andExpect(jsonPath("$.permissions.canRequestExtension").value(false));
        assertThat(reload(groupBuy.getId()).getStartAt()).isEqualTo(groupBuy.getStartAt());
        assertThat(reload(groupBuy.getId()).getEndAt()).isEqualTo(originalEnd.plusDays(7));
    }

    @Test
    @DisplayName("B4→B4c→B4e: 인플루언서 거절 후에도 판매와 원래 종료일은 유지하고 재연장은 불가하다")
    void extensionRejectedAcrossSellerAndCreatorApis() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        LocalDateTime originalEnd = groupBuy.getEndAt();

        action(groupBuy.getId(), "extension-request", Map.of("extensionDays", 7))
                .andExpect(status().isOk());
        creatorAction(groupBuy.getId(), "extension/rejection")
                .andExpect(status().isOk());

        detail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.extension.status").value("REJECTED"))
                .andExpect(jsonPath("$.timeline.endAt").value(originalEnd + "Z"))
                .andExpect(jsonPath("$.permissions.canRequestExtension").value(false));
        action(groupBuy.getId(), "extension-request", Map.of("extensionDays", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_EXTENSION_ALREADY_USED"));
    }

    @Test
    @DisplayName("B4→B5→B6: 진행중 KPI는 계약 리워드로 계산하고 종료에서 숨기며 정산완료는 확정 basis로 표시한다")
    void salesVisibilityFollowsLifecycle() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        given(salesReader.readSales(groupBuy.getId())).willReturn(Optional.of(new GroupBuySalesReader.GroupBuySales(
                15, 392_000L, List.of(
                        new GroupBuySalesReader.ItemQuantity(cream.getProductId(), 10),
                        new GroupBuySalesReader.ItemQuantity(serum.getProductId(), 5)))));
        given(salesReader.readClosure(groupBuy.getId())).willReturn(Optional.of(closure(15, 10, 5)));

        detail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.basis").value("LIVE"))
                .andExpect(jsonPath("$.sales.orderCount").value(15))
                .andExpect(jsonPath("$.sales.amount").value(392_000))
                // 10 × (27,200 × 12%) + 5 × (24,000 × 10%)
                .andExpect(jsonPath("$.sales.rewardAmount").value(44_640))
                .andExpect(jsonPath("$.orderClosure.unclosedCount").value(5));

        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        detail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.sales").doesNotExist())
                .andExpect(jsonPath("$.orderClosure.unclosedCount").value(5))
                .andExpect(jsonPath("$.permissions.canCheckFulfillment").value(true));

        moveTo(groupBuy.getId(), GroupBuyStatus.SETTLED);
        detail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.basis").value("SETTLED"))
                .andExpect(jsonPath("$.sales.rewardAmount").value(44_640))
                .andExpect(jsonPath("$.permissions.canCheckFulfillment").value(false));
    }

    @Test
    @DisplayName("B5/C5→B5e: 이슈 개설은 3자 스레드와 한 건으로 기록되고 중복 버튼이 사라져도 정산 보류는 하지 않는다")
    void issueOpeningCreatesOneLinkedThreadWithoutSettlementHold() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.ENDED);
        given(threadGateway.openIssueThread(any(GroupBuy.class), eq(FulfillmentSide.SELLER),
                eq(GroupBuyIssueType.CONTENT_FULFILLMENT), eq("스토리 1건 누락"))).willReturn(901L);

        action(groupBuy.getId(), "issues", Map.of("issueType", "CONTENT_FULFILLMENT", "content", "스토리 1건 누락"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issueId").isNumber())
                .andExpect(jsonPath("$.threadId").value(901));

        var issue = issueRepository.findFirstByGroupBuyIdAndStatus(groupBuy.getId(), GroupBuyIssueStatus.OPEN)
                .orElseThrow();
        assertThat(issue.getThreadId()).isEqualTo(901L);
        assertThat(issue.getContent()).isEqualTo("스토리 1건 누락");
        detail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("ENDED"))
                .andExpect(jsonPath("$.afterEnd.openIssue.threadId").value(901))
                .andExpect(jsonPath("$.afterEnd.fulfillment.onHold").value(false))
                .andExpect(jsonPath("$.permissions.canOpenIssue").value(false));

        action(groupBuy.getId(), "issues", Map.of("issueType", "CONTENT_FULFILLMENT", "content", "중복"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_ISSUE_ALREADY_OPEN"));
        assertThat(issueRepository.count()).isEqualTo(1);
        assertThat(groupBuyHistoryRepository.findByGroupBuyIdOrderByOccurredAtAscIdAsc(groupBuy.getId()))
                .filteredOn(entry -> entry.getEventType() == GroupBuyEventType.ISSUE_OPENED)
                .hasSize(1);
        verify(threadGateway).openIssueThread(any(GroupBuy.class), eq(FulfillmentSide.SELLER),
                eq(GroupBuyIssueType.CONTENT_FULFILLMENT), eq("스토리 1건 누락"));
    }

    @Test
    @DisplayName("B5/C7→B5f: 미이행은 사유를 스레드 첫 글로 보내고 보류하며 재제출할 수 없다")
    void unfulfilledCheckCreatesDisputeAndBlocksSettlement() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.ENDED);
        given(threadGateway.openFulfillmentDisputeThread(any(GroupBuy.class), eq(FulfillmentSide.SELLER),
                eq("계약상 스토리 3건 중 2건 게시"))).willReturn(902L);
        given(salesReader.readClosure(groupBuy.getId())).willReturn(Optional.of(closure(12, 12, 0)));
        saveCreatorFulfilled(groupBuy);

        action(groupBuy.getId(), "fulfillment-check", Map.of(
                "result", "UNFULFILLED", "reason", "계약상 스토리 3건 중 2건 게시"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afterEnd.fulfillment.mine.result").value("UNFULFILLED"))
                .andExpect(jsonPath("$.afterEnd.fulfillment.mine.reason").value("계약상 스토리 3건 중 2건 게시"))
                .andExpect(jsonPath("$.afterEnd.fulfillment.threadId").value(902))
                .andExpect(jsonPath("$.afterEnd.fulfillment.onHold").value(true))
                .andExpect(jsonPath("$.permissions.canCheckFulfillment").value(false));

        assertThat(groupBuyCommandService.isSettlementReady(groupBuy.getId())).isFalse();
        assertThat(fulfillmentCheckRepository.findByGroupBuyId(groupBuy.getId())).hasSize(2);
        action(groupBuy.getId(), "fulfillment-check", Map.of("result", "FULFILLED"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_FULFILLMENT_ALREADY_CHECKED"));
        verify(threadGateway).openFulfillmentDisputeThread(any(GroupBuy.class), eq(FulfillmentSide.SELLER),
                eq("계약상 스토리 3건 중 2건 게시"));
    }

    @Test
    @DisplayName("B5→B5a: 주문이 모두 종결되고 양측 이행이 확인된 경우에만 정산 게이트가 열린다")
    void settlementGateRequiresKnownClosedOrdersAndBothChecks() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.ENDED);
        action(groupBuy.getId(), "fulfillment-check", Map.of("result", "FULFILLED"))
                .andExpect(status().isOk());
        saveCreatorFulfilled(groupBuy);

        // 판매 포트가 비어 있을 때 모르는 주문 수를 0으로 해석하면 안 된다.
        assertThat(groupBuyCommandService.isSettlementReady(groupBuy.getId())).isFalse();
        given(salesReader.readClosure(groupBuy.getId())).willReturn(Optional.of(closure(12, 11, 1)));
        assertThat(groupBuyCommandService.isSettlementReady(groupBuy.getId())).isFalse();
        given(salesReader.readClosure(groupBuy.getId())).willReturn(Optional.of(closure(12, 12, 0)));
        assertThat(groupBuyCommandService.isSettlementReady(groupBuy.getId())).isTrue();
    }

    private void saveCreatorFulfilled(GroupBuy groupBuy) {
        transactionTemplate.executeWithoutResult(tx -> fulfillmentCheckRepository.saveAndFlush(
                GroupBuyFulfillmentCheck.manual(groupBuyRepository.findById(groupBuy.getId()).orElseThrow(),
                        FulfillmentSide.CREATOR, FulfillmentResult.FULFILLED, null, creator.getId(), null,
                        LocalDateTime.now())));
    }

    private org.springframework.test.web.servlet.ResultActions creatorAction(long groupBuyId, String path)
            throws Exception {
        var user = creator.getUser();
        String token = bearerToken(user.getUsername(), RoleType.CREATOR, user.getId());
        return mockMvc.perform(post("/v1/creator/group-buys/" + groupBuyId + "/" + path)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType("application/json")
                .content("{}"));
    }

    private org.springframework.test.web.servlet.ResultActions adminAction(long groupBuyId, String path, Object body)
            throws Exception {
        var operator = fixture.createAdmin("functional-admin-" + groupBuyId + "@showroomz.test", "김운영");
        return mockMvc.perform(post("/v1/admin/group-buys/" + groupBuyId + "/" + path)
                .header(HttpHeaders.AUTHORIZATION, adminToken(operator))
                .contentType("application/json")
                .content(toJson(body)));
    }

    private static GroupBuySalesReader.GroupBuyOrderClosure closure(int total, int closed, int unclosed) {
        return new GroupBuySalesReader.GroupBuyOrderClosure(total, closed, unclosed, null, null, List.of());
    }
}
