package showroomz.api.admin.groupbuy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.type.AdminSuspensionStatus;
import showroomz.domain.groupbuy.type.ChangeRequestStatus;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyPostRevisionKind;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.product.type.ProductGroupBuyStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 운영자가 상세에서 판정한 뒤 목록·상대 화면에 반영되는 흐름을 검증한다. */
@DisplayName("[통합] 어드민 공구 관리 판정 전후 시나리오")
class AdminGroupBuyScenarioIntegrationTest extends AdminGroupBuyTestSupport {

    @Test
    @DisplayName("오픈 심사 큐는 승인 후 사라지고 승인 결과가 준비완료 탭과 쇼룸·브랜드 상세에 반영된다")
    void openApprovalMovesQueueAndBothParties() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        confirmStockDirectly(groupBuy.getId());
        seedPendingReview(groupBuy);

        adminSummary().andExpect(jsonPath("$.queues.OPEN_REVIEW").value(1));
        adminList("tab=ACTION_REQUIRED").andExpect(jsonPath("$.pageInfo.totalResults").value(1));

        adminAction(groupBuy.getId(), "open-review/approve", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));

        adminSummary().andExpect(jsonPath("$.queues.OPEN_REVIEW").value(0))
                .andExpect(jsonPath("$.tabCounts.READY").value(1))
                .andExpect(jsonPath("$.actionRequiredCount").value(0));
        adminList("tab=ACTION_REQUIRED").andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        adminList("tab=READY").andExpect(jsonPath("$.content[0].groupBuyId").value(groupBuy.getId()));
        adminDetail(groupBuy.getId()).andExpect(jsonPath("$.readiness.gates[2].state").value("DONE"))
                .andExpect(jsonPath("$.permissions.canApproveOpen").value(false));
        detail(groupBuy.getId()).andExpect(jsonPath("$.groupBuy.status").value("READY"));
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.READY);
    }

    @Test
    @DisplayName("인플루언서 중단 요청 승인 후 요청 큐가 비고 공구·게시물·상품이 종료되며 판정 사유가 남는다")
    void creatorSuspensionApprovalClosesEverything() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        var post = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        transactionTemplate.executeWithoutResult(tx -> showroomz.domain.groupbuy.service.GroupBuyPostExposure.sync(
                groupBuyRepository.findById(groupBuy.getId()).orElseThrow(),
                groupBuyPostRepository.findById(post.getPostId()).orElseThrow(), LocalDateTime.now()));
        GroupBuyChangeRequest request = seedPendingRequest(groupBuy.getId(), ChangeRequestType.SUSPEND,
                GroupBuyActorType.CREATOR, "PRODUCT_DEFECT");
        assertThat(loadPost(groupBuy.getId()).getPost().getStatus()).isEqualTo(PostStatus.PUBLISHED);
        adminSummary().andExpect(jsonPath("$.queues.SUSPEND_REQUEST").value(1));

        adminAction(groupBuy.getId(), "change-requests/" + request.getId() + "/approve",
                Map.of("decisionReason", "판매 상품 하자가 확인되었습니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));

        GroupBuy closed = reload(groupBuy.getId());
        assertThat(closed.getCloseType()).isEqualTo(GroupBuyCloseType.SUSPENDED);
        assertThat(closed.getClosingChangeRequestId()).isEqualTo(request.getId());
        assertThat(changeRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(ChangeRequestStatus.APPROVED);
        assertThat(loadPost(groupBuy.getId()).getPost().getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(productStatus(cream)).isEqualTo(ProductGroupBuyStatus.NOT_CONNECTED);
        adminSummary().andExpect(jsonPath("$.queues.SUSPEND_REQUEST").value(0))
                .andExpect(jsonPath("$.tabCounts.SUSPENDED").value(1));
        adminDetail(groupBuy.getId()).andExpect(jsonPath("$.closure.source").value("REQUEST"))
                .andExpect(jsonPath("$.closure.requester.name").value("글로우_지민"))
                .andExpect(jsonPath("$.closure.decisionReason").value("판매 상품 하자가 확인되었습니다."))
                .andExpect(jsonPath("$.permissions.canApproveRequest").value(false));
        detail(groupBuy.getId()).andExpect(jsonPath("$.groupBuy.status").value("SUSPENDED"));
        adminAction(groupBuy.getId(), "change-requests/" + request.getId() + "/reject",
                Map.of("decisionReason", "뒤늦은 반려"))
                .andExpect(status().isConflict());
        assertThat(groupBuyHistoryRepository.findByGroupBuyIdOrderByOccurredAtAscIdAsc(groupBuy.getId()))
                .filteredOn(history -> history.getEventType() == GroupBuyEventType.SUSPENDED)
                .hasSize(1);
    }

    @Test
    @DisplayName("소명 기한과 집행 시각이 지난 직권 중단은 예정 큐에서 사라지고 집행 근거와 종결 효과를 남긴다")
    void dueNoticeExecutionClosesAndClearsQueue() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuyAdminSuspension notice = seedNotice(groupBuy.getId(), LocalDateTime.now().minusDays(2));
        adminSummary().andExpect(jsonPath("$.queues.APPEAL_REVIEW").value(1));
        adminDetail(groupBuy.getId()).andExpect(jsonPath("$.permissions.canExecuteSuspension").value(true));

        adminAction(groupBuy.getId(), "admin-suspension/execute", Map.of("executionNote", "소명을 검토했으나 위반이 확인되었습니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));

        assertThat(adminSuspensionRepository.findById(notice.getId()).orElseThrow().getStatus())
                .isEqualTo(AdminSuspensionStatus.EXECUTED);
        assertThat(reload(groupBuy.getId()).getClosingAdminSuspensionId()).isEqualTo(notice.getId());
        assertThat(productStatus(cream)).isEqualTo(ProductGroupBuyStatus.NOT_CONNECTED);
        adminSummary().andExpect(jsonPath("$.queues.APPEAL_REVIEW").value(0))
                .andExpect(jsonPath("$.tabCounts.SUSPENDED").value(1));
        adminDetail(groupBuy.getId()).andExpect(jsonPath("$.closure.source").value("ADMIN_NOTICE"))
                .andExpect(jsonPath("$.closure.adminBasis.basisLabel").exists())
                .andExpect(jsonPath("$.permissions.canExecuteSuspension").value(false));
        adminAction(groupBuy.getId(), "admin-suspension/execute", Map.of("executionNote", "중복 집행"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("상세에서 목록 복귀 조건과 이전·다음 이동은 필터에 맞는 공구만 가리킨다")
    void detailNavigationKeepsListFilter() throws Exception {
        GroupBuy first = seedPreparing();
        GroupBuy ready = seedIn(GroupBuyStatus.READY);
        GroupBuy second = seedPreparing();

        JsonNode firstDetail = objectMapper.readTree(mockMvc.perform(get(ADMIN + "/" + first.getId())
                        .header(HttpHeaders.AUTHORIZATION, operatorToken).param("tab", "PREPARING"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode secondDetail = objectMapper.readTree(mockMvc.perform(get(ADMIN + "/" + second.getId())
                        .header(HttpHeaders.AUTHORIZATION, operatorToken).param("tab", "PREPARING"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(firstDetail.at("/navigation/prevGroupBuyId").asLong(0)
                + firstDetail.at("/navigation/nextGroupBuyId").asLong(0)).isEqualTo(second.getId());
        assertThat(secondDetail.at("/navigation/prevGroupBuyId").asLong(0)
                + secondDetail.at("/navigation/nextGroupBuyId").asLong(0)).isEqualTo(first.getId());
        mockMvc.perform(get(ADMIN + "/" + ready.getId())
                        .header(HttpHeaders.AUTHORIZATION, operatorToken).param("tab", "PREPARING"))
                .andExpect(jsonPath("$.navigation.prevGroupBuyId").doesNotExist())
                .andExpect(jsonPath("$.navigation.nextGroupBuyId").doesNotExist());
        adminList("tab=PREPARING").andExpect(jsonPath("$.pageInfo.totalResults").value(2));
        adminList("tab=READY").andExpect(jsonPath("$.content[0].groupBuyId").value(ready.getId()));
    }

    @Test
    @DisplayName("브랜드와 쇼룸 계정은 어드민 오픈 승인 API를 호출할 수 없고 심사 대기는 그대로 남는다")
    void partnerRolesCannotDecideAdminReview() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        confirmStockDirectly(groupBuy.getId());
        seedPendingReview(groupBuy);
        String creatorToken = bearerToken(creator.getUser().getUsername(), RoleType.CREATOR,
                creator.getUser().getId());

        mockMvc.perform(post(ADMIN + "/" + groupBuy.getId() + "/open-review/approve")
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN + "/" + groupBuy.getId() + "/open-review/approve")
                        .header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isForbidden());

        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.PREPARING);
        adminSummary().andExpect(jsonPath("$.queues.OPEN_REVIEW").value(1));
    }

    @Test
    @DisplayName("종료 후 30일이 지난 정산 대기는 요약과 상세에서 지연으로 표시하되 상태를 자동 변경하지 않는다")
    void settlementWatchFlagsOverdueWithoutChangingStatus() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.ENDED);
        jdbc.update("UPDATE group_buy SET ended_at = ? WHERE group_buy_id = ?",
                LocalDateTime.now().minusDays(31), groupBuy.getId());

        adminSummary().andExpect(jsonPath("$.settlementWatch.watchingCount").value(1))
                .andExpect(jsonPath("$.settlementWatch.overdueCount").value(1));
        adminDetail(groupBuy.getId()).andExpect(jsonPath("$.afterEnd.settlement.watch.reached").value(true))
                .andExpect(jsonPath("$.afterEnd.settlement.stage").value("WAITING"));
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.ENDED);
    }

    @Test
    @DisplayName("심사 SLA가 지나도 자동 승인되지 않고 운영자 큐·기한 초과 표시·승인 권한이 유지된다")
    void overdueOpenReviewStaysForOperator() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        confirmStockDirectly(groupBuy.getId());
        seedPendingReview(groupBuy);
        jdbc.update("UPDATE group_buy_post SET submitted_at = ? WHERE group_buy_id = ?",
                LocalDateTime.now().minusDays(10), groupBuy.getId());

        adminDetail(groupBuy.getId())
                .andExpect(jsonPath("$.openReview.overdue").value(true))
                .andExpect(jsonPath("$.permissions.canApproveOpen").value(true));
        adminSummary().andExpect(jsonPath("$.queues.OPEN_REVIEW").value(1))
                .andExpect(jsonPath("$.nearestDeadlines[0].daysLeft").value(lessThan(0)));
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.PREPARING);

        adminAction(groupBuy.getId(), "open-review/approve", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        adminSummary().andExpect(jsonPath("$.queues.OPEN_REVIEW").value(0));
    }

    @Test
    @DisplayName("브랜드 소명 제출이 운영자 검토 큐를 만들고 철회가 소명 권한과 큐를 닫는다")
    void sellerAppealBecomesAdminReviewThenWithdraws() throws Exception {
        GroupBuy groupBuy = seedInProgressLong();
        LocalDateTime appealDeadline = businessCalendar.addBusinessDays(LocalDate.now(), 3)
                .atTime(23, 59, 59);
        LocalDateTime executeAt = businessCalendar.addBusinessDays(appealDeadline.toLocalDate(), 1)
                .atTime(10, 0);
        adminAction(groupBuy.getId(), "admin-suspension/notice",
                Map.of("clause", "ART17_1_LAW", "executeScheduledAt", executeAt.toString(),
                        "appealDeadlineAt", appealDeadline.toString(), "noticeBody", "표시 문구의 근거를 제출해 주세요."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENSION_SCHEDULED"));
        adminSummary().andExpect(jsonPath("$.queues.APPEAL_REVIEW").value(0));

        action(groupBuy.getId(), "appeal", Map.of("content", "시험성적서에 근거한 표현이며 문구를 정정했습니다."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminSuspension.appeal.content")
                        .value("시험성적서에 근거한 표현이며 문구를 정정했습니다."));
        adminSummary().andExpect(jsonPath("$.queues.APPEAL_REVIEW").value(1));
        adminDetail(groupBuy.getId())
                .andExpect(jsonPath("$.adminSuspension.appeal.content")
                        .value("시험성적서에 근거한 표현이며 문구를 정정했습니다."))
                .andExpect(jsonPath("$.permissions.canExecuteSuspension").value(false))
                .andExpect(jsonPath("$.permissions.canWithdrawSuspension").value(true));

        adminAction(groupBuy.getId(), "admin-suspension/withdraw",
                Map.of("reasonCode", "RECTIFIED", "detail", "수정된 본문을 확인했습니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        adminSummary().andExpect(jsonPath("$.queues.APPEAL_REVIEW").value(0));
        detail(groupBuy.getId()).andExpect(jsonPath("$.permissions.canSubmitAppeal").value(false));
        assertThat(reload(groupBuy.getId()).getEndAt()).isEqualTo(groupBuy.getEndAt());
    }

    @Test
    @DisplayName("종료일 당일 유효한 직접 입력 집행 시각이 있으면 통지 창도 열려야 한다")
    void noticeWindowIncludesValidExecutionBeforeEndOnSameDate() throws Exception {
        GroupBuy groupBuy = seedInProgressLong();
        LocalDate today = LocalDate.now();
        LocalDateTime appealDeadline = businessCalendar.addBusinessDays(today, 3).atTime(23, 59, 59);
        LocalDate endDay = businessCalendar.addBusinessDays(today, 4);
        LocalDateTime executeAt = endDay.atTime(10, 0);
        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?",
                endDay.atTime(23, 59), groupBuy.getId());

        adminGet(groupBuy.getId(), "admin-suspension/notice-options")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
        adminDetail(groupBuy.getId())
                .andExpect(jsonPath("$.permissions.canNoticeSuspension").value(true));
        adminAction(groupBuy.getId(), "admin-suspension/notice",
                Map.of("clause", "ART17_1_LAW", "executeScheduledAt", executeAt.toString(),
                        "appealDeadlineAt", appealDeadline.toString(), "noticeBody", "법령 위반 문구의 시정을 요청합니다."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENSION_SCHEDULED"));
    }

    @Test
    @DisplayName("숨김·통지·해제의 근거 판본은 최신 수정 이후에도 각자 유지되어 본문 대조에 쓰인다")
    void revisionMarkersKeepDecisionEvidenceAfterLaterEdit() throws Exception {
        GroupBuy groupBuy = seedInProgressLong();
        var post = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        int hiddenBasis = seedApprovedWithEdits(post, 1);
        adminAction(groupBuy.getId(), "post/hide",
                Map.of("reasonCode", "AD_MEDICAL_CLAIM", "detail", "의료적 효능 표현", "observedRevisionNo", hiddenBasis))
                .andExpect(status().isOk());

        LocalDateTime appealDeadline = businessCalendar.addBusinessDays(LocalDate.now(), 3)
                .atTime(23, 59, 59);
        LocalDateTime executeAt = businessCalendar.addBusinessDays(appealDeadline.toLocalDate(), 1)
                .atTime(10, 0);
        adminAction(groupBuy.getId(), "admin-suspension/notice",
                Map.of("clause", "ART17_1_LAW", "executeScheduledAt", executeAt.toString(),
                        "appealDeadlineAt", appealDeadline.toString(), "noticeBody", "의료적 효능 표현을 시정해 주세요."))
                .andExpect(status().isOk());
        seedRevision(post, hiddenBasis + 1, GroupBuyPostRevisionKind.EDITED, LocalDateTime.now());

        adminGet(groupBuy.getId(), "post/revisions").andExpect(status().isOk())
                .andExpect(jsonPath("$[1].hiddenBasis").value(true))
                .andExpect(jsonPath("$[1].noticeBasis").value(true))
                .andExpect(jsonPath("$[1].latest").value(false))
                .andExpect(jsonPath("$[2].latest").value(true))
                .andExpect(jsonPath("$[2].hiddenBasis").value(false));

        adminAction(groupBuy.getId(), "post/unhide", Map.of("expectedRevisionNo", hiddenBasis + 1))
                .andExpect(status().isOk());
        adminGet(groupBuy.getId(), "post/revisions")
                .andExpect(jsonPath("$[1].hiddenBasis").value(true))
                .andExpect(jsonPath("$[1].noticeBasis").value(true))
                .andExpect(jsonPath("$[2].unhiddenBasis").value(true));
    }
}
