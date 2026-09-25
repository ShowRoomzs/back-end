package showroomz.api.creator.groupbuy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.entity.GroupBuyPostRevision;
import showroomz.domain.groupbuy.type.ExtensionRequestStatus;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyPostRevisionKind;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.PostType;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("[통합] 쇼룸 스튜디오 공구 실행 — 게시물 쓰기 · 연장 응답 · 중단 요청 · 이행 확인")
class CreatorGroupBuyCommandIntegrationTest extends CreatorGroupBuyTestSupport {

    private static final Map<String, String> FULL_POST = Map.of(
            "title", "여름 수분 세럼, 제가 쓰던 그 조합",
            "content", "건조한 여름에도 속까지 촉촉하게 — 크림과 세럼을 같이 쓰면 좋아요.");

    // ── 2-3 임시저장 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("최초 임시저장이 게시물을 만든다 — 뿌리는 공구 타입·비노출·공구의 인플루언서 · 리비전·이력 없음")
    void firstDraftCreatesGroupBuyPost() throws Exception {
        GroupBuy groupBuy = seedPreparing();

        saveDraft(groupBuy.getId(), Map.of("title", "여름 세럼")).andExpect(status().isOk())
                .andExpect(jsonPath("$.post.status").value("WRITING"))
                .andExpect(jsonPath("$.post.title").value("여름 세럼"))
                .andExpect(jsonPath("$.post.content").doesNotExist())
                .andExpect(jsonPath("$.readiness.gates[1].state").value("MY_TURN"))
                .andExpect(jsonPath("$.permissions.canWritePost").value(true))
                .andExpect(jsonPath("$.history[*].eventType").value(contains("CREATED")));

        GroupBuyPost saved = loadPost(groupBuy.getId());
        Post root = inTransaction(() -> {
            Post post = postRepository.findById(saved.getPostId()).orElseThrow();
            post.getCreator().getId();
            return post;
        });
        assertThat(root.getPostType()).isEqualTo(PostType.GROUP_BUY);
        assertThat(root.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(root.getAspectRatio()).isNull();
        assertThat(root.getCreator().getId()).isEqualTo(creator.getId());
        assertThat(revisionRepository.count()).isZero();

        // 이후 호출은 덮어쓴다.
        saveDraft(groupBuy.getId(), Map.of("title", "여름 세럼 2", "content", "본문")).andExpect(status().isOk())
                .andExpect(jsonPath("$.post.title").value("여름 세럼 2"))
                .andExpect(jsonPath("$.post.content").value("본문"));
        assertThat(groupBuyPostRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 임시저장 · 상한 초과는 400")
    void draftValidation() throws Exception {
        GroupBuy groupBuy = seedPreparing();

        saveDraft(groupBuy.getId(), Map.of("title", "  ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_POST_EMPTY_DRAFT"));
        saveDraft(groupBuy.getId(), Map.of("title", "가".repeat(41))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_POST_TOO_LONG"));
        saveDraft(groupBuy.getId(), Map.of("content", "가".repeat(2001))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_POST_TOO_LONG"));
        assertThat(groupBuyPostRepository.count()).isZero();
    }

    @Test
    @DisplayName("반려 게시물을 임시저장해도 반려 그대로 — 반려 사유가 고치는 동안 남는다")
    void draftKeepsRejection() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.REJECTED, false);

        saveDraft(groupBuy.getId(), Map.of("title", "고치는 중")).andExpect(status().isOk())
                .andExpect(jsonPath("$.post.status").value("REJECTED"))
                .andExpect(jsonPath("$.post.rejection.code").value("PRICE_MISMATCH"))
                .andExpect(jsonPath("$.readiness.gates[1].state").value("MY_TURN"));
    }

    // ── 2-4 등록하고 검토 요청 ─────────────────────────────────────────────

    @Test
    @DisplayName("임시저장 없이 바로 제출 — 승인대기 · 게이트 ② 완료 · 리비전 1 · 이력 · 이후 쓰기는 전부 409")
    void submitWithoutDraft() throws Exception {
        GroupBuy groupBuy = seedPreparing();

        submitPost(groupBuy.getId(), FULL_POST).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("PREPARING"))
                .andExpect(jsonPath("$.post.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.post.submittedAt").exists())
                .andExpect(jsonPath("$.post.expectedReviewDate").exists())
                .andExpect(jsonPath("$.readiness.gates[*].state").value(contains("WAITING", "DONE", "IN_REVIEW")))
                .andExpect(jsonPath("$.readiness.registrationOverdue").value(false))
                .andExpect(jsonPath("$.permissions.canWritePost").value(false))
                .andExpect(jsonPath("$.history[-1:].eventType").value(contains("POST_SUBMITTED")))
                .andExpect(jsonPath("$.history[-1:].actorType").value(contains("CREATOR")))
                .andExpect(jsonPath("$.history[-1:].actorDisplayName").value(contains("글로우_지민")));

        List<GroupBuyPostRevision> revisions = revisions(groupBuy.getId());
        assertThat(revisions).extracting(GroupBuyPostRevision::getKind).containsExactly(GroupBuyPostRevisionKind.SUBMITTED);
        assertThat(revisions.get(0).getTitle()).isEqualTo(FULL_POST.get("title"));
        assertThat(revisions.get(0).getCreatedBy()).isEqualTo(creator.getId());
        studioSummary().andExpect(jsonPath("$.actionRequiredCount").value(0));

        // 심사 중인 글은 바뀌면 안 된다 — 임시저장·재제출·수정 모두 막힌다(B2).
        saveDraft(groupBuy.getId(), Map.of("title", "몰래 고치기")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_POST_UNDER_REVIEW"));
        submitPost(groupBuy.getId(), FULL_POST).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_POST_UNDER_REVIEW"));
        editPost(groupBuy.getId(), FULL_POST).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_POST_UNDER_REVIEW"));
        assertThat(loadPost(groupBuy.getId()).getTitle()).isEqualTo(FULL_POST.get("title"));
    }

    @Test
    @DisplayName("제출은 제목·본문 모두 필수")
    void submitRequiresBoth() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        submitPost(groupBuy.getId(), Map.of("title", "제목만")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_POST_REQUIRED_FIELD"));
    }

    @Test
    @DisplayName("반려 후 재등록 — 이력 detail 「재등록」 · 반려 필드는 재심사를 위해 보존")
    void resubmitAfterRejection() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.REJECTED, false);

        submitPost(groupBuy.getId(), FULL_POST).andExpect(status().isOk())
                .andExpect(jsonPath("$.post.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.post.rejection").doesNotExist())
                .andExpect(jsonPath("$.history[-1:].detail").value(contains("재등록")));

        GroupBuyPost saved = loadPost(groupBuy.getId());
        assertThat(saved.getReviewStatus()).isEqualTo(GroupBuyPostReviewStatus.PENDING);
        assertThat(saved.getRejectReasonCode()).isEqualTo("PRICE_MISMATCH");
    }

    @Test
    @DisplayName("준비중이 아니면 작성·제출은 409")
    void writeOnlyWhilePreparing() throws Exception {
        GroupBuy ready = seedIn(GroupBuyStatus.READY);
        saveDraft(ready.getId(), FULL_POST).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_POST_NOT_WRITABLE"));
        submitPost(ready.getId(), FULL_POST).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_POST_NOT_WRITABLE"));
    }

    // ── 2-5 승인 후 수정 ─────────────────────────────────────────────────

    @Test
    @DisplayName("숨김 중 승인 게시물 수정 — 즉시 반영 · 재승인 없음 · 숨김은 풀리지 않는다 · 리비전 EDITED · 이력 없음")
    void editHiddenApprovedPost() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, true);
        int historyBefore = groupBuyHistoryRepository.findByGroupBuyIdOrderByOccurredAtAscIdAsc(groupBuy.getId()).size();

        editPost(groupBuy.getId(), FULL_POST).andExpect(status().isOk())
                .andExpect(jsonPath("$.post.status").value("HIDDEN"))
                .andExpect(jsonPath("$.post.hidden.code").value("AD_DISCLOSURE"))
                .andExpect(jsonPath("$.post.hidden.hiddenDays").isNumber())
                .andExpect(jsonPath("$.post.title").value(FULL_POST.get("title")))
                .andExpect(jsonPath("$.post.content").value(FULL_POST.get("content")))
                .andExpect(jsonPath("$.post.lastEditedAt").exists())
                .andExpect(jsonPath("$.permissions.canEditPost").value(true));

        GroupBuyPost saved = loadPost(groupBuy.getId());
        assertThat(saved.getReviewStatus()).isEqualTo(GroupBuyPostReviewStatus.APPROVED);
        assertThat(saved.isHidden()).isTrue();
        assertThat(revisions(groupBuy.getId())).extracting(GroupBuyPostRevision::getKind)
                .containsExactly(GroupBuyPostRevisionKind.EDITED);
        assertThat(groupBuyHistoryRepository.findByGroupBuyIdOrderByOccurredAtAscIdAsc(groupBuy.getId()))
                .hasSize(historyBefore);
    }

    @Test
    @DisplayName("수정 잠금 — 중단 예정 · 승인 전 준비중은 NOT_EDITABLE · 노출 중 제목을 비울 수 없다")
    void editLocks() throws Exception {
        GroupBuy scheduled = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(scheduled.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        seedNotice(scheduled.getId(), LocalDateTime.now().plusDays(3).withNano(0));
        editPost(scheduled.getId(), FULL_POST).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_POST_NOT_EDITABLE"));

        GroupBuy preparing = seedPreparing();
        seedPost(preparing.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        editPost(preparing.getId(), FULL_POST).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_POST_NOT_EDITABLE"));

        GroupBuy selling = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(selling.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        editPost(selling.getId(), Map.of("title", "", "content", "본문만")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_POST_REQUIRED_FIELD"));
    }

    // ── 5-1 · 5-2 연장 응답 ──────────────────────────────────────────────

    @Test
    @DisplayName("연장 수락 — 종료일이 바뀌고 요청은 ACCEPTED · 이력 · 두 번째는 409")
    void acceptExtension() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuyExtensionRequest extension = seedExtension(groupBuy.getId(), 7);

        studioDetail(groupBuy.getId())
                .andExpect(jsonPath("$.extension.status").value("PENDING"))
                .andExpect(jsonPath("$.extension.respondDeadlineAt").exists())
                .andExpect(jsonPath("$.permissions.canRespondExtension").value(true));

        studioAction(groupBuy.getId(), "extension/acceptance", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.extension.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.extension.responseActorType").value("CREATOR"))
                .andExpect(jsonPath("$.extension.respondDeadlineAt").doesNotExist())
                .andExpect(jsonPath("$.permissions.canRespondExtension").value(false))
                .andExpect(jsonPath("$.history[-1:].eventType").value(contains("EXTENSION_ACCEPTED")))
                .andExpect(jsonPath("$.history[-1:].detail").value(contains(startsWithText("종료일 "))));

        assertThat(reload(groupBuy.getId()).getEndAt()).isEqualTo(extension.getAfterEndAt());
        assertThat(extensionRequestRepository.findByGroupBuyId(groupBuy.getId()).orElseThrow().getStatus())
                .isEqualTo(ExtensionRequestStatus.ACCEPTED);

        studioAction(groupBuy.getId(), "extension/acceptance", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_EXTENSION_NOT_PENDING"));
    }

    @Test
    @DisplayName("종료 시각이 지나면 수락·거절 모두 409 RESPONSE_CLOSED — 종료일은 그대로")
    void extensionResponseClosesAtEnd() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedExtension(groupBuy.getId(), 7);
        LocalDateTime passed = LocalDateTime.now().minusMinutes(1).withNano(0);
        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?", Timestamp.valueOf(passed), groupBuy.getId());

        studioAction(groupBuy.getId(), "extension/acceptance", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_EXTENSION_RESPONSE_CLOSED"));
        studioAction(groupBuy.getId(), "extension/rejection", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_EXTENSION_RESPONSE_CLOSED"));
        assertThat(reload(groupBuy.getId()).getEndAt()).isEqualTo(passed);
    }

    @Test
    @DisplayName("연장 거절 — 기타면 메모 필수 · 메모는 브랜드에게 보인다 · 종료일 불변")
    void rejectExtension() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedExtension(groupBuy.getId(), 7);

        studioAction(groupBuy.getId(), "extension/rejection", Map.of("reasonCode", "ETC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_REASON_MEMO_REQUIRED"));

        studioAction(groupBuy.getId(), "extension/rejection",
                Map.of("reasonCode", "NEXT_SCHEDULE_BOOKED", "memo", "9월 첫 주에 다른 공구가 잡혀 있어요."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extension.status").value("REJECTED"))
                .andExpect(jsonPath("$.extension.rejectReasonLabel").value("다음 일정이 잡혀 있음"))
                .andExpect(jsonPath("$.history[-1:].detail").value(contains("다음 일정이 잡혀 있음")));

        assertThat(reload(groupBuy.getId()).getEndAt()).isEqualTo(groupBuy.getEndAt());
        detail(groupBuy.getId()).andExpect(jsonPath("$.extension.rejectMemo").value("9월 첫 주에 다른 공구가 잡혀 있어요."));
    }

    @Test
    @DisplayName("거절된 연장은 종료 전이가 만료로 덮어쓰지 않는다")
    void endDoesNotOverwriteRejection() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedExtension(groupBuy.getId(), 7);
        studioAction(groupBuy.getId(), "extension/rejection", Map.of()).andExpect(status().isOk());

        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), groupBuy.getId());
        assertThat(lifecycleService.end(groupBuy.getId(), LocalDateTime.now())).isTrue();

        assertThat(extensionRequestRepository.findByGroupBuyId(groupBuy.getId()).orElseThrow().getStatus())
                .isEqualTo(ExtensionRequestStatus.REJECTED);
    }

    @Test
    @DisplayName("중단 예정에서는 연장에 응답할 수 없다")
    void noExtensionResponseWhileSuspensionScheduled() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedExtension(groupBuy.getId(), 7);
        seedNotice(groupBuy.getId(), LocalDateTime.now().plusDays(3).withNano(0));

        studioAction(groupBuy.getId(), "extension/acceptance", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_ACTION_NOT_ALLOWED"));
    }

    // ── 5-3 중단 요청 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("중단 요청 — 상태 불변 · 내 요청은 메모까지 보이고 · 브랜드에게는 사유 라벨만")
    void requestSuspension() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedExtension(groupBuy.getId(), 7); // 연장 대기 중에도 요청할 수 있다(B6).

        studioAction(groupBuy.getId(), "suspension-request",
                Map.of("reasonCode", "DELIVERY_FAILURE", "memo", "18건이 아직 배송 시작되지 않았습니다."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.activeRequest.mine").value(true))
                .andExpect(jsonPath("$.activeRequest.requesterType").value("CREATOR"))
                .andExpect(jsonPath("$.activeRequest.reasonLabel").value("배송 지연 · 미발송이 계속됨"))
                .andExpect(jsonPath("$.activeRequest.memo").value("18건이 아직 배송 시작되지 않았습니다."))
                .andExpect(jsonPath("$.permissions.canRequestSuspension").value(false));

        GroupBuyChangeRequest saved = changeRequestRepository.findByGroupBuyIdOrderByRequestedAtDescIdDesc(groupBuy.getId()).get(0);
        assertThat(saved.getRequesterType()).isEqualTo(GroupBuyActorType.CREATOR);
        assertThat(saved.getRequesterId()).isEqualTo(creator.getId());
        assertThat(saved.getStatusAtRequest()).isEqualTo(GroupBuyStatus.IN_PROGRESS);

        detail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRequest.reasonLabel").value("배송 지연 · 미발송이 계속됨"))
                // 운영자에게 쓴 메모는 브랜드에게 내리지 않는다.
                .andExpect(jsonPath("$.activeRequest.memo").doesNotExist())
                .andExpect(jsonPath("$.history[-1:].detail").value(contains("배송 지연 · 미발송이 계속됨")))
                .andExpect(jsonPath("$.history[-1:].detail").value(not(contains(containsString("18건")))));

        studioAction(groupBuy.getId(), "suspension-request",
                Map.of("reasonCode", "ETC", "memo", "다시 요청")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_REQUEST_ALREADY_PENDING"));
    }

    @Test
    @DisplayName("중단 요청 — 메모 필수 · 진행중만(준비완료 불가) · 숨김 중 불가")
    void suspensionRequestGuards() throws Exception {
        GroupBuy selling = seedIn(GroupBuyStatus.IN_PROGRESS);
        studioAction(selling.getId(), "suspension-request", Map.of("reasonCode", "PERSONAL_REASON"))
                .andExpect(status().isBadRequest());

        GroupBuy ready = seedIn(GroupBuyStatus.READY);
        studioAction(ready.getId(), "suspension-request", Map.of("reasonCode", "PERSONAL_REASON", "memo", "사정"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_ACTION_NOT_ALLOWED"));

        GroupBuy hidden = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(hidden.getId(), GroupBuyPostReviewStatus.APPROVED, true);
        studioAction(hidden.getId(), "suspension-request", Map.of("reasonCode", "PRODUCT_DEFECT", "memo", "하자"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_ACTION_NOT_ALLOWED"));
    }

    // ── 5-4 이행 확인 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("이행 확인 — CREATOR 측으로 저장 · 불가역 · 브랜드 화면에서는 theirs")
    void checkFulfillment() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.ENDED);

        studioAction(groupBuy.getId(), "fulfillment-check", Map.of("result", "FULFILLED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.afterEnd.fulfillment.mine.result").value("FULFILLED"))
                .andExpect(jsonPath("$.afterEnd.fulfillment.theirs").doesNotExist())
                .andExpect(jsonPath("$.permissions.canCheckFulfillment").value(false))
                .andExpect(jsonPath("$.history[-1:].eventType").value(contains("FULFILLMENT_CONFIRMED")))
                .andExpect(jsonPath("$.history[-1:].actorType").value(contains("CREATOR")));

        assertThat(fulfillmentCheckRepository.existsByGroupBuyIdAndCheckerSide(groupBuy.getId(), FulfillmentSide.CREATOR))
                .isTrue();
        detail(groupBuy.getId()).andExpect(jsonPath("$.afterEnd.fulfillment.theirs.result").value("FULFILLED"));
        studioSummary().andExpect(jsonPath("$.actionRequiredCount").value(0));

        studioAction(groupBuy.getId(), "fulfillment-check", Map.of("result", "UNFULFILLED", "reason", "번복"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_FULFILLMENT_ALREADY_CHECKED"));
    }

    @Test
    @DisplayName("미이행 — 사유 필수 · 3자 스레드를 못 열면 확인도 남지 않는다(503)")
    void unfulfilledNeedsThread() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.ENDED);

        studioAction(groupBuy.getId(), "fulfillment-check", Map.of("result", "UNFULFILLED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_FULFILLMENT_REASON_REQUIRED"));
        Map<String, Object> body = new HashMap<>();
        body.put("result", "UNFULFILLED");
        body.put("reason", "18건이 아직 배송 시작되지 않았습니다.");
        studioAction(groupBuy.getId(), "fulfillment-check", body).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_THREAD_UNAVAILABLE"));

        assertThat(fulfillmentCheckRepository.findByGroupBuyId(groupBuy.getId())).isEmpty();
    }

    @Test
    @DisplayName("종료 전에는 이행 확인 불가")
    void fulfillmentOnlyAfterEnd() throws Exception {
        GroupBuy selling = seedIn(GroupBuyStatus.IN_PROGRESS);
        studioAction(selling.getId(), "fulfillment-check", Map.of("result", "FULFILLED"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_ACTION_NOT_ALLOWED"));
    }

    // ── 도우미 ───────────────────────────────────────────────────────────

    private GroupBuyPost loadPost(Long groupBuyId) {
        return groupBuyPostRepository.findByGroupBuyId(groupBuyId).orElseThrow();
    }

    private List<GroupBuyPostRevision> revisions(Long groupBuyId) {
        return revisionRepository.findByGroupBuyPostPostIdOrderByRevisionNoAsc(loadPost(groupBuyId).getPostId());
    }

    private static org.hamcrest.Matcher<String> startsWithText(String prefix) {
        return org.hamcrest.Matchers.startsWith(prefix);
    }
}
