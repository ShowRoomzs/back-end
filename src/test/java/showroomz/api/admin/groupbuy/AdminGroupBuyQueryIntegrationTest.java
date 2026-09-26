package showroomz.api.admin.groupbuy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.service.GroupBuyActor;
import showroomz.domain.groupbuy.service.GroupBuyHistoryRecorder;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("[통합] 어드민 공구 조회 — 조치 큐 · 탭 · 요약 · 상세")
class AdminGroupBuyQueryIntegrationTest extends AdminGroupBuyTestSupport {

    @Autowired GroupBuyHistoryRecorder historyRecorder;

    /**
     * 조치 큐 4종 각 1건 + 큐에 들지 않는 3종(숨김 · 소명 대기 · 이행 미합의 없음의 종료) + 준비중 대기 1건.
     * 큐는 배타적이라 {@code actionRequiredCount == 조치 필요 탭 행 수}다(32 설계 2-3).
     */
    @Test
    @DisplayName("조치 큐 4종이 배타적으로 세지고, 큐 합 = 조치 필요 탭 행 수 = GNB 배지다")
    void queuesAreExclusiveAndSumToActionTab() throws Exception {
        GroupBuy openReview = seedPreparing();
        seedPendingReview(openReview);
        GroupBuy suspendRequest = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedSellerRequest(suspendRequest, ChangeRequestType.SUSPEND);
        GroupBuy earlyClose = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedSellerRequest(earlyClose, ChangeRequestType.EARLY_CLOSE);
        GroupBuy appealOverdue = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedNotice(appealOverdue.getId(), LocalDateTime.now().minusHours(1));

        // 큐에 들지 않는 것 — 다음 차례가 운영자가 아니다.
        GroupBuy hidden = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(hidden.getId(), GroupBuyPostReviewStatus.APPROVED, true);
        GroupBuy appealWaiting = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedNotice(appealWaiting.getId(), LocalDateTime.now().plusDays(2));
        seedIn(GroupBuyStatus.ENDED);
        seedPreparing();

        adminSummary().andExpect(status().isOk())
                .andExpect(jsonPath("$.queues.OPEN_REVIEW").value(1))
                .andExpect(jsonPath("$.queues.SUSPEND_REQUEST").value(1))
                .andExpect(jsonPath("$.queues.EARLY_CLOSE_REQUEST").value(1))
                .andExpect(jsonPath("$.queues.APPEAL_REVIEW").value(1))
                .andExpect(jsonPath("$.actionRequiredCount").value(4))
                .andExpect(jsonPath("$.tabCounts.ALL").value(8))
                .andExpect(jsonPath("$.tabCounts.ACTION_REQUIRED").value(4))
                .andExpect(jsonPath("$.tabCounts.PREPARING").value(2))
                .andExpect(jsonPath("$.tabCounts.IN_PROGRESS").value(5))
                .andExpect(jsonPath("$.tabCounts.ENDED").value(1))
                .andExpect(jsonPath("$.tabCounts.SETTLED").value(0))
                .andExpect(jsonPath("$.nearestDeadlines[*].queue").value(hasItem("OPEN_REVIEW")))
                .andExpect(jsonPath("$.nearestDeadlines[*].queue").value(hasItem("APPEAL_REVIEW")))
                .andExpect(jsonPath("$.settlementWatch.watchingCount").value(1))
                .andExpect(jsonPath("$.settlementWatch.overdueCount").value(0));

        JsonNode actionTab = json(adminList("tab=ACTION_REQUIRED"));
        assertThat(actionTab.at("/pageInfo/totalResults").asLong()).isEqualTo(4);
        List<Long> ids = new ArrayList<>();
        actionTab.get("content").forEach(row -> ids.add(row.get("groupBuyId").asLong()));
        assertThat(ids).containsExactlyInAnyOrder(openReview.getId(), suspendRequest.getId(), earlyClose.getId(),
                appealOverdue.getId());
    }

    @Test
    @DisplayName("「조치 필요 우선」 정렬은 조치 행을 먼저 두고 행마다 actionRequired를 싣는다 — 목록에 permissions가 없다")
    void actionRequiredFirstSort() throws Exception {
        GroupBuy plain = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuy action = seedPreparing();
        seedPendingReview(action);
        seedIn(GroupBuyStatus.SUSPENDED);

        JsonNode page = json(adminList(null));
        JsonNode first = page.get("content").get(0);
        assertThat(first.get("groupBuyId").asLong()).isEqualTo(action.getId());
        assertThat(first.get("actionRequired").asBoolean()).isTrue();
        assertThat(first.get("postStatus").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(first.get("creatorName").asText()).isEqualTo("글로우_지민");
        assertThat(first.get("brandName").asText()).isEqualTo("글로우랩");
        assertThat(first.has("permissions")).isFalse();
        assertThat(first.has("remark")).isFalse();
        page.get("content").forEach(row -> {
            if (row.get("groupBuyId").asLong() == plain.getId()) {
                assertThat(row.get("actionRequired").asBoolean()).isFalse();
            }
        });
    }

    @Test
    @DisplayName("검색은 공구명 · 공구번호 · 브랜드명 · 쇼룸명 4축 — 양측을 다 찾는다")
    void keywordSearchesBothSides() throws Exception {
        GroupBuy groupBuy = seedPreparing();

        adminList("keyword=지민").andExpect(jsonPath("$.pageInfo.totalResults").value(1));
        adminList("keyword=글로우랩").andExpect(jsonPath("$.pageInfo.totalResults").value(1));
        adminList("keyword=" + groupBuy.getGroupBuyNumber()).andExpect(jsonPath("$.pageInfo.totalResults").value(1));
        adminList("keyword=없는공구").andExpect(jsonPath("$.pageInfo.totalResults").value(0));
    }

    @Test
    @DisplayName("B1 오픈 승인 대기 — 게이트 ③은 MY_TURN(경고 톤) · SLA 기한과 시작일을 한 블록에 · 승인·반려 버튼")
    void openReviewDetail() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        confirmStockDirectly(groupBuy.getId());
        GroupBuyPost post = seedPendingReview(groupBuy);
        LocalDate due = businessCalendar.addBusinessDays(post.getSubmittedAt().toLocalDate(), 3);

        adminDetail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("PREPARING"))
                .andExpect(jsonPath("$.readiness.gates[0].state").value("DONE"))
                .andExpect(jsonPath("$.readiness.gates[0].doneByName").value("글로우랩"))
                .andExpect(jsonPath("$.readiness.gates[1].state").value("DONE"))
                .andExpect(jsonPath("$.readiness.gates[2].state").value("MY_TURN"))
                .andExpect(jsonPath("$.readiness.gates[2].tone").value("WARNING"))
                .andExpect(jsonPath("$.openReview.slaBusinessDays").value(3))
                .andExpect(jsonPath("$.openReview.dueAt").value(startsWith(due + "T23:59:59")))
                .andExpect(jsonPath("$.openReview.startAt").exists())
                .andExpect(jsonPath("$.post.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.post.postNumber").value(startsWith("POST-GB-")))
                .andExpect(jsonPath("$.post.disclosureText").value(containsString("글로우랩")))
                .andExpect(jsonPath("$.post.latestRevisionNo").value(1))
                .andExpect(jsonPath("$.items[0].minQuantity").value(300))
                .andExpect(jsonPath("$.permissions.canApproveOpen").value(true))
                .andExpect(jsonPath("$.permissions.canRejectOpen").value(true))
                .andExpect(jsonPath("$.permissions.canHidePost").value(false))
                .andExpect(jsonPath("$.permissions.canNoticeSuspension").value(false))
                .andExpect(jsonPath("$.permissions.noticeUnavailableReason").value("STATUS"));
    }

    @Test
    @DisplayName("B3 판단 근거 — 판매 포트가 비면 요청 후 증가분 · CS 문의 합계가 null이다(0이 아니다) · 상대 요청 메모도 내린다")
    void decisionBasisIsNullNotZero() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPendingRequest(groupBuy.getId(), ChangeRequestType.SUSPEND, GroupBuyActorType.CREATOR, "PRODUCT_DEFECT");

        adminDetail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRequest.type").value("SUSPEND"))
                .andExpect(jsonPath("$.activeRequest.requesterType").value("CREATOR"))
                .andExpect(jsonPath("$.activeRequest.requesterName").value("글로우_지민"))
                .andExpect(jsonPath("$.activeRequest.elapsed").value("3h"))
                .andExpect(jsonPath("$.activeRequest.decisionBasis.ordersAtRequest").doesNotExist())
                .andExpect(jsonPath("$.activeRequest.decisionBasis.ordersSinceRequest").doesNotExist())
                .andExpect(jsonPath("$.activeRequest.decisionBasis.inquiries.total").doesNotExist())
                .andExpect(jsonPath("$.activeRequest.decisionBasis.inquiries.defectRelated").doesNotExist())
                .andExpect(jsonPath("$.sales").doesNotExist())
                .andExpect(jsonPath("$.permissions.canApproveRequest").value(true))
                .andExpect(jsonPath("$.permissions.noticeUnavailableReason").value("REQUEST_PENDING"));
    }

    @Test
    @DisplayName("B4 조기 마감 근거 — 준비 물량(최소 물량 합) · 품절 문의는 상품 문의 테이블만으로 셀 수 있어 값이 나온다")
    void earlyCloseBasis() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedSellerRequest(groupBuy, ChangeRequestType.EARLY_CLOSE);

        adminDetail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRequest.type").value("EARLY_CLOSE"))
                .andExpect(jsonPath("$.activeRequest.decisionBasis.preparedQuantity").value(500))
                .andExpect(jsonPath("$.activeRequest.decisionBasis.sellThroughRate").doesNotExist())
                .andExpect(jsonPath("$.activeRequest.decisionBasis.soldOutInquiriesSinceRequest").value(0))
                .andExpect(jsonPath("$.activeRequest.decisionBasis.endsImmediatelyIfApproved").value(true));
    }

    @Test
    @DisplayName("통지 창이 없으면 통지 버튼이 잠기고 사유가 NO_WINDOW_BEFORE_END다 — 긴급은 열려 있다")
    void noNoticeWindowBeforeEnd() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);   // 종료가 4일 뒤

        adminDetail(groupBuy.getId())
                .andExpect(jsonPath("$.permissions.canNoticeSuspension").value(false))
                .andExpect(jsonPath("$.permissions.noticeUnavailableReason").value("NO_WINDOW_BEFORE_END"))
                .andExpect(jsonPath("$.permissions.canEmergencySuspend").value(true));
        adminGet(groupBuy.getId(), "admin-suspension/notice-options")
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.unavailableReason").value("NO_WINDOW_BEFORE_END"));
    }

    @Test
    @DisplayName("M6 날짜 선택지 — 영업일만 · 소명 기한 하한 이하 날짜는 잠금 · 첫 선택 가능 날짜는 소명 기한 다음 영업일")
    void noticeOptions() throws Exception {
        GroupBuy groupBuy = seedInProgressLong();
        LocalDate today = LocalDate.now();
        LocalDate appealDay = businessCalendar.addBusinessDays(today, 3);

        JsonNode options = json(adminGet(groupBuy.getId(), "admin-suspension/notice-options"));
        assertThat(options.get("available").asBoolean()).isTrue();
        assertThat(options.at("/appealDeadline/default").asText()).startsWith(appealDay + "T23:59:59");
        LocalDate firstSelectable = null;
        for (JsonNode chip : options.get("executionDates")) {
            LocalDate date = LocalDate.parse(chip.get("date").asText());
            assertThat(date.getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
            assertThat(chip.get("selectable").asBoolean()).isEqualTo(date.isAfter(appealDay));
            if (firstSelectable == null && chip.get("selectable").asBoolean()) {
                firstSelectable = date;
            }
        }
        assertThat(firstSelectable).isEqualTo(businessCalendar.addBusinessDays(appealDay, 1));
    }

    @Test
    @DisplayName("이력 — 최신순 · 운영자 실명 · 게시물 수정은 리비전에서 합성한 POST_EDITED(synthetic)")
    void historyMergesSyntheticEdits() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuyPost post = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        seedApprovedWithEdits(post, 2);
        transactionTemplate.executeWithoutResult(tx -> historyRecorder.record(reload(groupBuy.getId()),
                GroupBuyEventType.OPEN_APPROVED, GroupBuyActor.admin(operator.getId(), OPERATOR_NAME), null, null,
                LocalDateTime.now().withNano(0)));

        adminDetail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.history[0].eventType").value("OPEN_APPROVED"))
                .andExpect(jsonPath("$.history[0].actorDisplayName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.history[?(@.eventType == 'POST_EDITED')].detail")
                        .value(hasItem("2회차 · 재승인 없음")))
                .andExpect(jsonPath("$.history[?(@.eventType == 'POST_EDITED')].synthetic").value(hasItem(true)))
                .andExpect(jsonPath("$.post.editCount").value(2))
                .andExpect(jsonPath("$.post.latestRevisionNo").value(3));

        // 파트너 · 스튜디오 직렬화는 운영자 이름을 내리지 않는다(32 설계 1-4 ①).
        detail(groupBuy.getId())
                .andExpect(jsonPath("$.history[?(@.actorType == 'ADMIN')].actorDisplayName")
                        .value(not(hasItem(OPERATOR_NAME))));
    }

    @Test
    @DisplayName("판본 목록 — 표지(승인된 판 · 최신)를 붙이고 원문을 그대로 내린다")
    void postRevisions() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuyPost post = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        int latest = seedApprovedWithEdits(post, 1);

        adminGet(groupBuy.getId(), "post/revisions").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(latest))
                .andExpect(jsonPath("$[0].kind").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].approved").value(true))
                .andExpect(jsonPath("$[1].kind").value("EDITED"))
                .andExpect(jsonPath("$[1].latest").value(true))
                .andExpect(jsonPath("$[1].content").value("판본 2 본문"));
    }

    @Test
    @DisplayName("종료 — 정산 차단 사유를 서버가 판정한다 · 판매 포트가 비면 CLOSURE_UNKNOWN이고 정산 확인이 닫힌다")
    void settlementBlockers() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.ENDED);

        adminDetail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.afterEnd.settlement.stage").value("WAITING"))
                .andExpect(jsonPath("$.afterEnd.settlement.stageSource").value("DERIVED"))
                .andExpect(jsonPath("$.afterEnd.settlement.blockers[0]").value("CLOSURE_UNKNOWN"))
                .andExpect(jsonPath("$.afterEnd.settlement.blockers[1]").value("FULFILLMENT_PENDING"))
                .andExpect(jsonPath("$.afterEnd.settlement.preview.rewardAmount").doesNotExist())
                .andExpect(jsonPath("$.afterEnd.settlement.watch.reached").value(false))
                .andExpect(jsonPath("$.afterEnd.fulfillment.targets.brandToCreator.duties[0]").value("SHOWROOM_POST"))
                .andExpect(jsonPath("$.afterEnd.fulfillment.targets.creatorToBrand.duties[0]").value("ORDER_DELIVERY"))
                .andExpect(jsonPath("$.afterEnd.fulfillment.autoConfirmOnTimeout").value(false))
                .andExpect(jsonPath("$.afterEnd.orderClosure").doesNotExist())
                .andExpect(jsonPath("$.sales").doesNotExist())
                .andExpect(jsonPath("$.permissions.canConfirmSettlement").value(false))
                .andExpect(jsonPath("$.permissions.canOpenIssue").value(true));
    }

    @Test
    @DisplayName("없는 공구는 404 · 운영자 토큰이 아니면 막힌다")
    void notFoundAndAuth() throws Exception {
        adminDetail(999_999L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_NOT_FOUND"));
        mockMvc.perform(get(ADMIN)
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isForbidden());
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
