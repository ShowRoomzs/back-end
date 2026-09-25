package showroomz.api.admin.groupbuy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.entity.GroupBuyHistory;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.service.GroupBuyCommandService;
import showroomz.domain.groupbuy.type.AdminSuspensionKind;
import showroomz.domain.groupbuy.type.AdminSuspensionStatus;
import showroomz.domain.groupbuy.type.ChangeRequestStatus;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.ExtensionRequestStatus;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.SuspensionWithdrawReason;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.global.error.exception.BusinessException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("[통합] 어드민 공구 판정 — 오픈 승인 · 게시물 · 직권 중단 · 요청 판정 · 종결 공용화")
class AdminGroupBuyCommandIntegrationTest extends AdminGroupBuyTestSupport {

    @Autowired GroupBuyCommandService groupBuyCommandService;

    // ── 5-1 · 5-2 오픈 승인 · 반려 ────────────────────────────────────────

    @Test
    @DisplayName("오픈 승인 — 물량 확인이 끝났으면 이 요청이 준비완료를 만든다 · 이력에 운영자 실명 · 두 번째 승인은 409")
    void approveOpenPromotesToReady() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        confirmStockDirectly(groupBuy.getId());
        seedPendingReview(groupBuy);

        adminAction(groupBuy.getId(), "open-review/approve", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.postStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$.openAt").exists());

        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.READY);
        assertThat(productStatus(cream)).isEqualTo(ProductGroupBuyStatus.READY);
        GroupBuyHistory approved = history(groupBuy, GroupBuyEventType.OPEN_APPROVED);
        assertThat(approved.getActorType()).isEqualTo(GroupBuyActorType.ADMIN);
        assertThat(approved.getActorDisplayName()).isEqualTo(OPERATOR_NAME);
        assertThat(approved.getActorId()).isEqualTo(operator.getId());
        // 운영자가 마지막 게이트면 OPEN_APPROVED가 준비완료를 함께 말한다 — READY 이력을 따로 남기지 않는다.
        assertThat(events(groupBuy)).doesNotContain(GroupBuyEventType.READY);

        adminAction(groupBuy.getId(), "open-review/approve", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_OPEN_REVIEW_NOT_PENDING"));
    }

    @Test
    @DisplayName("오픈 승인 — 물량 확인 전이면 준비중에 남는다(승인 → 준비완료는 확정이 아니다)")
    void approveOpenWaitsForStock() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        seedPendingReview(groupBuy);

        adminAction(groupBuy.getId(), "open-review/approve", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.openAt").doesNotExist());
        assertThat(loadPost(groupBuy.getId()).isApproved()).isTrue();
    }

    @Test
    @DisplayName("오픈 반려 — 설명은 사유와 무관하게 필수 · 공구는 준비중에서 멈춘다 · 이력 detail은 사유 라벨만")
    void rejectOpen() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        seedPendingReview(groupBuy);

        adminAction(groupBuy.getId(), "open-review/reject", Map.of("reasonCode", "AD_EFFECT_ASSERTION", "detail", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_REJECT_DETAIL_REQUIRED"));

        adminAction(groupBuy.getId(), "open-review/reject", Map.of("reasonCode", "AD_EFFECT_ASSERTION",
                "detail", "“건조함이 사라집니다” 문장이 효과를 단정합니다."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.postStatus").value("REJECTED"));

        GroupBuyPost post = loadPost(groupBuy.getId());
        assertThat(post.getReviewStatus()).isEqualTo(GroupBuyPostReviewStatus.REJECTED);
        assertThat(post.getRejectReasonCode()).isEqualTo("AD_EFFECT_ASSERTION");
        assertThat(history(groupBuy, GroupBuyEventType.OPEN_REJECTED).getDetail())
                .isEqualTo("표시광고법 위반 문구 — 효과 단정");

        adminDetail(groupBuy.getId())
                .andExpect(jsonPath("$.post.rejection.label").value("표시광고법 위반 문구 — 효과 단정"))
                .andExpect(jsonPath("$.post.rejection.axis").value("AD_LAW"))
                .andExpect(jsonPath("$.post.rejection.rejectedByName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.readiness.gates[1].state").value("REJECTED"));
    }

    // ── 5-3 · 5-4 숨김 · 해제 ─────────────────────────────────────────────

    @Test
    @DisplayName("숨김 — 공구는 멈추지 않는다 · 잠금 안의 최신 판본을 박는다 · 본 판본과 다르면 revisionAdvanced")
    void hidePost() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuyPost post = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        int latest = seedApprovedWithEdits(post, 2);

        adminAction(groupBuy.getId(), "post/hide", Map.of("reasonCode", "AD_MEDICAL_CLAIM",
                "detail", "“탈모 개선” 표현", "observedRevisionNo", latest - 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.postStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.revisionAdvanced").value(true));

        GroupBuyPost hidden = loadPost(groupBuy.getId());
        assertThat(hidden.isHidden()).isTrue();
        assertThat(hidden.getHiddenRevisionNo()).isEqualTo(latest);
        assertThat(hidden.getPost().getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.IN_PROGRESS);

        adminAction(groupBuy.getId(), "post/hide", Map.of("reasonCode", "ETC", "detail", "x"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_POST_ALREADY_HIDDEN"));
        adminDetail(groupBuy.getId())
                .andExpect(jsonPath("$.post.hidden.revisionNo").value(latest))
                .andExpect(jsonPath("$.post.hidden.hiddenByName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.post.hidden.ordersSinceHidden").doesNotExist())
                .andExpect(jsonPath("$.permissions.canUnhidePost").value(true));
    }

    @Test
    @DisplayName("숨김 해제 — 읽은 뒤 수정이 생기면 409 · 게시물은 숨김 그대로 · 맞는 판본이면 다시 노출된다")
    void unhideChecksRevision() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuyPost post = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, true);
        int latest = seedApprovedWithEdits(post, 1);

        adminAction(groupBuy.getId(), "post/unhide", Map.of("expectedRevisionNo", latest - 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_POST_CHANGED_SINCE_VIEW"));
        assertThat(loadPost(groupBuy.getId()).isHidden()).isTrue();

        adminAction(groupBuy.getId(), "post/unhide", Map.of("expectedRevisionNo", latest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postStatus").value("EXPOSED"));
        GroupBuyPost unhidden = loadPost(groupBuy.getId());
        assertThat(unhidden.isHidden()).isFalse();
        assertThat(unhidden.getUnhiddenRevisionNo()).isEqualTo(latest);
        assertThat(unhidden.getPost().getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    // ── 6 직권 중단 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("사전 통지 — 검토 중 요청이 있으면 409 REQUEST_DECIDE_FIRST · 집행이 소명 기한보다 앞이면 400")
    void noticeGuards() throws Exception {
        GroupBuy groupBuy = seedInProgressLong();
        LocalDateTime appeal = businessCalendar.addBusinessDays(LocalDate.now(), 3).atTime(23, 59, 59);
        GroupBuyChangeRequest pending = seedSellerRequest(groupBuy, ChangeRequestType.EARLY_CLOSE);

        adminAction(groupBuy.getId(), "admin-suspension/notice", notice("ART17_1_LAW", appeal.plusDays(1).withHour(10), appeal))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_REQUEST_DECIDE_FIRST"));

        adminAction(groupBuy.getId(), "change-requests/" + pending.getId() + "/reject",
                Map.of("decisionReason", "목표 달성 전입니다.")).andExpect(status().isOk());

        // 시안 M6의 「소명 기한 08.14 23:59 + 집행 08.14 10:00」 — 소명을 읽기 전에 집행할 수 있게 된다.
        adminAction(groupBuy.getId(), "admin-suspension/notice", notice("ART17_1_LAW", appeal.withHour(10), appeal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_SUSPENSION_SCHEDULE_INVALID"));
        // 소명 기한이 3영업일보다 짧다.
        adminAction(groupBuy.getId(), "admin-suspension/notice",
                notice("ART17_1_LAW", appeal.plusDays(5), appeal.minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_SUSPENSION_SCHEDULE_INVALID"));
        // 집행이 종료 이후다.
        adminAction(groupBuy.getId(), "admin-suspension/notice",
                notice("ART17_1_LAW", reload(groupBuy.getId()).getEndAt().plusHours(1), appeal))
                .andExpect(status().isBadRequest());
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("사전 통지 — 중단 예정이 되지만 판매·게시물은 그대로 · 판본·판매 스냅샷 · 3호는 clauseCaution")
    void noticeSchedulesSuspension() throws Exception {
        GroupBuy groupBuy = seedInProgressLong();
        GroupBuyPost post = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        int latest = seedApprovedWithEdits(post, 1);
        LocalDateTime appeal = businessCalendar.addBusinessDays(LocalDate.now(), 3).atTime(23, 59, 59);
        LocalDateTime execute = businessCalendar.addBusinessDays(appeal.toLocalDate(), 1).atTime(10, 0);

        adminAction(groupBuy.getId(), "admin-suspension/notice", notice("ART17_3_BREACH", execute, appeal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENSION_SCHEDULED"))
                .andExpect(jsonPath("$.clauseCaution").value("C2_POST_ALTERATION"));

        GroupBuyAdminSuspension notice = adminSuspensionRepository
                .findFirstByGroupBuyIdAndStatus(groupBuy.getId(), AdminSuspensionStatus.NOTICED).orElseThrow();
        assertThat(notice.getNoticeRevisionNo()).isEqualTo(latest);
        assertThat(notice.getSalesOrderCountAtNotice()).isNull();
        assertThat(notice.getNoticedBy()).isEqualTo(operator.getId());
        assertThat(history(groupBuy, GroupBuyEventType.SUSPENSION_NOTICED).getDetail())
                .startsWith("제17조① 3호");
        // 예고만으로 판매가 멈추지 않는다 — 상품은 진행중 매핑 그대로다.
        assertThat(productStatus(cream)).isNotEqualTo(ProductGroupBuyStatus.NOT_CONNECTED);

        adminDetail(groupBuy.getId())
                .andExpect(jsonPath("$.adminSuspension.clauseLabel").value("제17조① 3호 중대 의무 불이행"))
                .andExpect(jsonPath("$.adminSuspension.noticedByName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.adminSuspension.salesSinceNotice").doesNotExist())
                .andExpect(jsonPath("$.permissions.canExecuteSuspension").value(false))
                .andExpect(jsonPath("$.permissions.canWithdrawSuspension").value(true));
        adminAction(groupBuy.getId(), "admin-suspension/notice", notice("ART17_1_LAW", execute, appeal))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_SUSPENSION_ALREADY_NOTICED"));
    }

    @Test
    @DisplayName("집행 — 소명 기한은 지났지만 집행 예정 전이면 409 · 사유 없으면 400")
    void executeNotDue() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedNotice(groupBuy.getId(), LocalDateTime.now().minusHours(1));   // 집행 예정 = 기한 + 1일

        adminAction(groupBuy.getId(), "admin-suspension/execute", Map.of("executionNote", "집행합니다."))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_SUSPENSION_EXECUTION_NOT_DUE"));

        GroupBuy due = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedNotice(due.getId(), LocalDateTime.now().minusDays(2));
        adminAction(due.getId(), "admin-suspension/execute", Map.of("executionNote", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_DECISION_REASON_REQUIRED"));
    }

    @Test
    @DisplayName("철회 — 진행중으로 돌아간다 · 종료 예정 불변 · 사유 코드 저장 · 소명 기한 전에도 받는다")
    void withdrawNotice() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        LocalDateTime endAt = groupBuy.getEndAt();
        GroupBuyAdminSuspension notice = seedNotice(groupBuy.getId(), LocalDateTime.now().plusDays(2));

        adminAction(groupBuy.getId(), "admin-suspension/withdraw",
                Map.of("reasonCode", "NOT_BRAND_FAULT", "detail", "귀책이 인플루언서에게 있습니다."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        GroupBuyAdminSuspension withdrawn = adminSuspensionRepository.findById(notice.getId()).orElseThrow();
        assertThat(withdrawn.getStatus()).isEqualTo(AdminSuspensionStatus.WITHDRAWN);
        assertThat(withdrawn.getWithdrawReasonCode()).isEqualTo(SuspensionWithdrawReason.NOT_BRAND_FAULT);
        assertThat(withdrawn.getWithdrawDetail()).isEqualTo("귀책이 인플루언서에게 있습니다.");
        assertThat(reload(groupBuy.getId()).getEndAt()).isEqualTo(endAt);

        adminAction(groupBuy.getId(), "admin-suspension/withdraw", Map.of("reasonCode", "ETC", "detail", "x"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_SUSPENSION_NOT_NOTICED"));
    }

    @Test
    @DisplayName("긴급 — 준비완료는 409 · 통지 중이면 그 통지는 SUPERSEDED · 이력에 「긴급」")
    void emergencySupersedesNotice() throws Exception {
        GroupBuy ready = seedIn(GroupBuyStatus.READY);
        adminAction(ready.getId(), "admin-suspension/emergency", Map.of("emergencyReason", "CONSUMER_HARM", "body", "x"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_STATUS_CONFLICT"));
        adminAction(ready.getId(), "admin-suspension/emergency", Map.of("emergencyReason", "ETC", "body", "x"))
                .andExpect(status().isBadRequest());

        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuyAdminSuspension notice = seedNotice(groupBuy.getId(), LocalDateTime.now().plusDays(2));

        adminAction(groupBuy.getId(), "admin-suspension/emergency",
                Map.of("emergencyReason", "AUTHORITY_ORDER", "body", "식약처 판매 중지 명령"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        assertThat(adminSuspensionRepository.findById(notice.getId()).orElseThrow().getStatus())
                .isEqualTo(AdminSuspensionStatus.SUPERSEDED);
        GroupBuy suspended = reload(groupBuy.getId());
        GroupBuyAdminSuspension emergency = adminSuspensionRepository.findById(suspended.getClosingAdminSuspensionId())
                .orElseThrow();
        assertThat(emergency.getKind()).isEqualTo(AdminSuspensionKind.EMERGENCY);
        assertThat(emergency.getStatus()).isEqualTo(AdminSuspensionStatus.EXECUTED);
        assertThat(history(groupBuy, GroupBuyEventType.SUSPENDED_EMERGENCY).getDetail())
                .isEqualTo("긴급 · 행정·사법기관의 명령");

        adminDetail(groupBuy.getId())
                .andExpect(jsonPath("$.closure.source").value("ADMIN_EMERGENCY"))
                .andExpect(jsonPath("$.closure.adminBasis.basisLabel").value("행정·사법기관의 명령"))
                .andExpect(jsonPath("$.closure.decidedByName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.closure.acceptedOrderCount").doesNotExist())
                .andExpect(jsonPath("$.permissions.canOpenIssue").value(true));
    }

    // ── 7 요청 판정 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("요청 반려 — 요청만 기각되고 공구는 일정대로 · 사유 필수 · 다른 요청 id면 409")
    void rejectRequest() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuyChangeRequest request = seedSellerRequest(groupBuy, ChangeRequestType.SUSPEND);

        adminAction(groupBuy.getId(), "change-requests/" + request.getId() + "/reject", Map.of("decisionReason", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_DECISION_REASON_REQUIRED"));
        adminAction(groupBuy.getId(), "change-requests/999999/reject", Map.of("decisionReason", "x"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_CHANGE_REQUEST_NOT_PENDING"));

        adminAction(groupBuy.getId(), "change-requests/" + request.getId() + "/reject",
                Map.of("decisionReason", "개별 교환으로 대응해 주세요."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        GroupBuyChangeRequest rejected = changeRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(ChangeRequestStatus.REJECTED);
        assertThat(rejected.getDecidedBy()).isEqualTo(operator.getId());
        assertThat(history(groupBuy, GroupBuyEventType.SUSPENSION_REJECTED).getDetail())
                .isEqualTo("개별 교환으로 대응해 주세요.");
        adminAction(groupBuy.getId(), "change-requests/" + request.getId() + "/approve", Map.of("decisionReason", "x"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_CHANGE_REQUEST_NOT_PENDING"));
    }

    @Test
    @DisplayName("조기 마감 승인 — 종료(EARLY_CLOSED) · 승인 시각이 종료 시각 · 이행 확인 기한이 생긴다 · closure에 요청자·승인자")
    void approveEarlyClose() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuyChangeRequest request = seedSellerRequest(groupBuy, ChangeRequestType.EARLY_CLOSE);

        adminAction(groupBuy.getId(), "change-requests/" + request.getId() + "/approve",
                Map.of("decisionReason", "재고 소진이 확인되어 조기 마감합니다."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));

        GroupBuy ended = reload(groupBuy.getId());
        assertThat(ended.getCloseType()).isEqualTo(GroupBuyCloseType.EARLY_CLOSED);
        assertThat(ended.getClosingChangeRequestId()).isEqualTo(request.getId());
        assertThat(ended.getEndedAt()).isBefore(ended.getEndAt());
        assertThat(ended.getFulfillmentDueAt()).isEqualTo(ended.getEndedAt().plusDays(3));
        adminDetail(groupBuy.getId())
                .andExpect(jsonPath("$.closure.closeType").value("EARLY_CLOSED"))
                .andExpect(jsonPath("$.closure.source").value("REQUEST"))
                .andExpect(jsonPath("$.closure.requester.name").value("글로우랩"))
                .andExpect(jsonPath("$.closure.decidedByName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.closure.decisionReason").value("재고 소진이 확인되어 조기 마감합니다."));
    }

    // ── 8-1 종결 공용화 — 경로 × 부수 효과 매트릭스 ─────────────────────────────

    /**
     * 종결 경로 5개 각각에서 같은 부수 효과가 일어나는지 고정한다(32 설계 8-1 테스트 1순위) —
     * 상품 groupBuyStatus 재계산 · 게시물 비노출 · 대기 연장 EXPIRED · 검토 요청 LAPSED · 조치 큐 0.
     */
    @Test
    @DisplayName("종결 5경로 × 부수 효과 5종 — 상품 재동기화 · 게시물 내림 · 연장 만료 · 요청 만료 · 조치 큐 0")
    void terminatorMatrix() throws Exception {
        assertTerminates("기간 종료", g -> transactionTemplate.executeWithoutResult(
                tx -> lifecycleService.end(g.getId(), LocalDateTime.now().plusDays(30))), true);
        assertTerminates("조기 마감 승인", g -> approve(g, seedSellerRequest(g, ChangeRequestType.EARLY_CLOSE)), false);
        assertTerminates("중단 요청 승인", g -> approve(g, seedSellerRequest(g, ChangeRequestType.SUSPEND)), false);
        assertTerminates("직권 중단 집행", g -> {
            seedNotice(g.getId(), LocalDateTime.now().minusDays(2));
            perform(() -> adminAction(g.getId(), "admin-suspension/execute", Map.of("executionNote", "집행합니다."))
                    .andExpect(status().isOk()));
        }, false);
        assertTerminates("긴급 직권 중단", g -> perform(() -> adminAction(g.getId(), "admin-suspension/emergency",
                Map.of("emergencyReason", "DAMAGE_SURGE", "body", "피해 급증")).andExpect(status().isOk())), true);

        adminSummary().andExpect(jsonPath("$.actionRequiredCount").value(0));
    }

    /**
     * @param withStrayRequest 종결 경로 자신이 요청을 쓰지 않는 경로면 검토 중 요청을 하나 더 깔아 LAPSED를 확인한다
     */
    private void assertTerminates(String path, Consumer<GroupBuy> terminate, boolean withStrayRequest) {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuyPost post = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        transactionTemplate.executeWithoutResult(tx -> {
            GroupBuy locked = groupBuyRepository.findById(groupBuy.getId()).orElseThrow();
            showroomz.domain.groupbuy.service.GroupBuyPostExposure.sync(locked,
                    groupBuyPostRepository.findById(post.getPostId()).orElseThrow(), LocalDateTime.now());
        });
        GroupBuyExtensionRequest extension = seedExtension(groupBuy.getId(), 3);
        GroupBuyChangeRequest stray = withStrayRequest
                ? seedPendingRequest(groupBuy.getId(), ChangeRequestType.SUSPEND, GroupBuyActorType.CREATOR, "ETC")
                : null;
        assertThat(loadPost(groupBuy.getId()).getPost().getStatus()).as(path).isEqualTo(PostStatus.PUBLISHED);

        terminate.accept(groupBuy);

        GroupBuy terminated = reload(groupBuy.getId());
        assertThat(terminated.getStatus().isTerminal()).as(path + " · 종결").isTrue();
        assertThat(productStatus(cream)).as(path + " · 상품 재동기화").isEqualTo(ProductGroupBuyStatus.NOT_CONNECTED);
        assertThat(loadPost(groupBuy.getId()).getPost().getStatus()).as(path + " · 게시물 내림").isEqualTo(PostStatus.DRAFT);
        assertThat(extensionRequestRepository.findById(extension.getId()).orElseThrow().getStatus())
                .as(path + " · 대기 연장 만료").isEqualTo(ExtensionRequestStatus.EXPIRED);
        if (stray != null) {
            assertThat(changeRequestRepository.findById(stray.getId()).orElseThrow().getStatus())
                    .as(path + " · 검토 요청 만료").isEqualTo(ChangeRequestStatus.LAPSED);
        }
        assertThat(changeRequestRepository.findByGroupBuyIdOrderByRequestedAtDescIdDesc(groupBuy.getId()))
                .as(path + " · PENDING 없음").noneMatch(GroupBuyChangeRequest::isPending);
        assertThat(adminSuspensionRepository.findFirstByGroupBuyIdAndStatus(groupBuy.getId(), AdminSuspensionStatus.NOTICED))
                .as(path + " · 진행 중 통지 없음").isEmpty();
    }

    private void approve(GroupBuy groupBuy, GroupBuyChangeRequest request) {
        perform(() -> adminAction(groupBuy.getId(), "change-requests/" + request.getId() + "/approve",
                Map.of("decisionReason", "승인합니다.")).andExpect(status().isOk()));
    }

    // ── 8 이슈 · 정산 · 합의 통보 ──────────────────────────────────────────────

    @Test
    @DisplayName("이슈 — 진행중이면 409 · 종료면 스레드 포트 선행 전이라 503 · 정산 확인은 착수 게이트라 409")
    void issueAndSettlementGates() throws Exception {
        GroupBuy inProgress = seedIn(GroupBuyStatus.IN_PROGRESS);
        adminAction(inProgress.getId(), "issues", Map.of("issueType", "ETC", "content", "이견"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_ACTION_NOT_ALLOWED"));

        GroupBuy ended = seedIn(GroupBuyStatus.ENDED);
        adminAction(ended.getId(), "issues", Map.of("issueType", "SETTLEMENT_AMOUNT", "content", "정산 금액 이견"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_THREAD_UNAVAILABLE"));
        adminAction(ended.getId(), "settlement/confirm", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_SETTLEMENT_NOT_READY"));
    }

    @Test
    @DisplayName("합의 전 보류 해제는 409 · 합의 후 해제 — 합의와 해제는 다른 사건이다")
    void releaseHoldRequiresAgreement() {
        GroupBuy ended = seedIn(GroupBuyStatus.ENDED);
        LocalDateTime now = LocalDateTime.now().withNano(0);

        assertThatThrownBy(() -> groupBuyCommandService.releaseFulfillmentHold(ended.getId(), operator.getId(),
                OPERATOR_NAME, now))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("합의");

        groupBuyCommandService.recordFulfillmentAgreement(ended.getId(), now, "교환 재발송 11건을 08.25까지 완료");
        GroupBuy agreed = reload(ended.getId());
        assertThat(agreed.getFulfillmentAgreedAt()).isEqualTo(now);
        assertThat(agreed.getFulfillmentResolvedAt()).isNull();

        groupBuyCommandService.releaseFulfillmentHold(ended.getId(), operator.getId(), OPERATOR_NAME, now.plusMinutes(5));
        assertThat(reload(ended.getId()).getFulfillmentResolvedAt()).isEqualTo(now.plusMinutes(5));
        assertThat(events(ended)).contains(GroupBuyEventType.FULFILLMENT_AGREED, GroupBuyEventType.FULFILLMENT_RESOLVED);
    }

    // ── 도우미 ───────────────────────────────────────────────────────────────

    private static Map<String, Object> notice(String clause, LocalDateTime execute, LocalDateTime appeal) {
        return Map.of("clause", clause, "executeScheduledAt", execute.toString(),
                "appealDeadlineAt", appeal.toString(), "noticeBody", "게시물 본문에 효능 단정 표현이 있습니다.");
    }

    private GroupBuyHistory history(GroupBuy groupBuy, GroupBuyEventType eventType) {
        return groupBuyHistoryRepository.findByGroupBuyIdOrderByOccurredAtAscIdAsc(groupBuy.getId()).stream()
                .filter(entry -> entry.getEventType() == eventType)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("이력 없음: " + eventType));
    }

    private List<GroupBuyEventType> events(GroupBuy groupBuy) {
        return groupBuyHistoryRepository.findByGroupBuyIdOrderByOccurredAtAscIdAsc(groupBuy.getId()).stream()
                .map(GroupBuyHistory::getEventType).toList();
    }

    private static void perform(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
