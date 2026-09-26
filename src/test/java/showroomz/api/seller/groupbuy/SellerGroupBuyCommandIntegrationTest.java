package showroomz.api.seller.groupbuy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import showroomz.api.seller.groupbuy.service.GroupBuyAppealAttachmentStorage;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.ExtensionRequestStatus;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.product.type.ProductGroupBuyStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("[통합] 파트너센터 공구 실행 — 물량 확인 · 연장 · 조기 마감 · 중단 · 소명 · 종료 후")
class SellerGroupBuyCommandIntegrationTest extends GroupBuyTestSupport {

    /** S3는 실제로 부르지 않는다 — 키 발급 · presign · HeadObject 결과를 테스트가 정한다. */
    @MockitoBean
    private GroupBuyAppealAttachmentStorage appealStorage;

    // ── B1 [확보 완료] ────────────────────────────────────────────────────

    @Test
    @DisplayName("물량 확인 — 이력에 최소 물량 스냅샷 · 게시물 승인 전이면 준비중에 머물고 두 번은 409")
    void confirmStockWithoutApprovalStaysPreparing() throws Exception {
        GroupBuy groupBuy = seedPreparing();

        action(groupBuy.getId(), "stock-confirmation", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("PREPARING"))
                .andExpect(jsonPath("$.readiness.gates[0].state").value("DONE"))
                .andExpect(jsonPath("$.readiness.gates[0].doneAt").exists())
                .andExpect(jsonPath("$.permissions.canConfirmStock").value(false))
                .andExpect(jsonPath("$.history[1].eventType").value("STOCK_CONFIRMED"))
                .andExpect(jsonPath("$.history[1].actorType").value("SELLER"))
                .andExpect(jsonPath("$.history[1].actorDisplayName").value("글로우랩"))
                // 제25조 제재 판정 때 「무엇을 확인했는지」가 이력만으로 읽혀야 한다.
                .andExpect(jsonPath("$.history[1].detail").value("글로우 크림 50ml 300개 · 글로우 세럼 30ml 200개"));

        assertThat(reload(groupBuy.getId()).getStockConfirmedBy()).isEqualTo(brand.seller().getId());
        action(groupBuy.getId(), "stock-confirmation", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_STOCK_ALREADY_CONFIRMED"));
    }

    @Test
    @DisplayName("운영자 승인이 먼저 끝났으면 브랜드 확인이 준비완료를 만든다 — 이력 행위자는 브랜드 · 상품은 준비완료")
    void confirmStockAsLastGatePromotesToReady() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);

        action(groupBuy.getId(), "stock-confirmation", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("READY"))
                .andExpect(jsonPath("$.groupBuy.readyAt").exists())
                .andExpect(jsonPath("$.readiness.gates[*].state").value(contains("DONE", "DONE", "DONE")))
                .andExpect(jsonPath("$.post.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.history[*].eventType").value(contains("CREATED", "STOCK_CONFIRMED", "READY")))
                .andExpect(jsonPath("$.history[2].actorType").value("SELLER"))
                .andExpect(jsonPath("$.permissions.canRequestSuspension").value(true));

        assertThat(productStatus(cream)).isEqualTo(ProductGroupBuyStatus.READY);
    }

    @Test
    @DisplayName("준비중이 아니면 물량 확인은 409")
    void confirmStockOnlyWhilePreparing() throws Exception {
        GroupBuy selling = seedIn(GroupBuyStatus.IN_PROGRESS);
        action(selling.getId(), "stock-confirmation", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_STATUS_CONFLICT"));
    }

    // ── C1 기간 연장 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("연장 요청 — 종료일은 수락 전까지 그대로 · 공구당 1회 · 두 번째는 409")
    void extensionIsOncePerGroupBuy() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        LocalDateTime endAt = groupBuy.getEndAt();

        action(groupBuy.getId(), "extension-request", Map.of("extensionDays", 7, "reason", "수요 증가"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extension.status").value("PENDING"))
                .andExpect(jsonPath("$.extension.days").value(7))
                .andExpect(jsonPath("$.permissions.canRequestExtension").value(false))
                // 대기 중인 연장은 차단 조건이 아니다 — B4c 「중단 요청만」, 원칙상 조기 마감도 열린다(설계서 4-5).
                .andExpect(jsonPath("$.permissions.canRequestSuspension").value(true))
                .andExpect(jsonPath("$.permissions.canRequestEarlyClose").value(true))
                .andExpect(jsonPath("$.history[-1:].detail").value(contains("7일 · 수요 증가")));

        GroupBuyExtensionRequest saved = extensionRequestRepository.findByGroupBuyId(groupBuy.getId()).orElseThrow();
        assertThat(saved.getBeforeEndAt()).isEqualTo(endAt);
        assertThat(saved.getAfterEndAt()).isEqualTo(endAt.plusDays(7));
        assertThat(reload(groupBuy.getId()).getEndAt()).isEqualTo(endAt);

        action(groupBuy.getId(), "extension-request", Map.of("extensionDays", 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_EXTENSION_ALREADY_USED"));

        list(null).andExpect(jsonPath("$.content[0].remark.code").value("EXTENSION_PENDING"));
    }

    @Test
    @DisplayName("연장 후 총 기간 30일 초과는 400 · 종료 12시간 전부터는 409")
    void extensionLimits() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS); // 8일 공구 — 최대 22일
        action(groupBuy.getId(), "extension-request", Map.of("extensionDays", 23)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_EXTENSION_EXCEEDS_LIMIT"));
        action(groupBuy.getId(), "extension-request", Map.of("extensionDays", 0)).andExpect(status().isBadRequest());

        GroupBuy closing = seedIn(GroupBuyStatus.IN_PROGRESS);
        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?",
                LocalDateTime.now().plusHours(11).withNano(0), closing.getId());
        detail(closing.getId()).andExpect(jsonPath("$.permissions.canRequestExtension").value(false));
        action(closing.getId(), "extension-request", Map.of("extensionDays", 3)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_EXTENSION_WINDOW_CLOSED"));
        assertThat(extensionRequestRepository.count()).isZero();
    }

    // ── C2 · C3 · C4 중단 · 조기 마감 ─────────────────────────────────────

    @Test
    @DisplayName("조기 마감 요청 — 상태는 진행중 그대로 · 검토 중이면 모든 추가 요청이 막힌다 · 취소 경로는 없다")
    void earlyCloseRequestBlocksFurtherRequests() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);

        action(groupBuy.getId(), "early-close-request", Map.of("reasonCode", "STOCK_OUT", "memo", "잔여 40개 미만"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.activeRequest.type").value("EARLY_CLOSE"))
                .andExpect(jsonPath("$.activeRequest.requesterType").value("SELLER"))
                .andExpect(jsonPath("$.activeRequest.requesterName").value("글로우랩"))
                .andExpect(jsonPath("$.activeRequest.reasonLabel").value("재고 소진"))
                .andExpect(jsonPath("$.activeRequest.statusAtRequest").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.permissions.canRequestExtension").value(false))
                .andExpect(jsonPath("$.permissions.canRequestEarlyClose").value(false))
                .andExpect(jsonPath("$.permissions.canRequestSuspension").value(false));

        // 판매 모듈이 없으니 요청 시점 판매 스냅샷은 0이 아니라 null이다(설계서 0-6).
        var request = changeRequestRepository.findByGroupBuyIdOrderByRequestedAtDescIdDesc(groupBuy.getId()).getFirst();
        assertThat(request.getSalesOrderCountAtRequest()).isNull();
        assertThat(request.getSalesAmountAtRequest()).isNull();

        action(groupBuy.getId(), "suspension-request", Map.of("reasonCode", "QUALITY_ISSUE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_REQUEST_ALREADY_PENDING"));
        action(groupBuy.getId(), "extension-request", Map.of("extensionDays", 3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_REQUEST_ALREADY_PENDING"));
    }

    @Test
    @DisplayName("인플루언서의 중단 요청이 검토 중이어도 브랜드 요청은 막힌다 — 요청자 무관")
    void creatorsPendingRequestBlocksBrand() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPendingRequest(groupBuy.getId(), ChangeRequestType.SUSPEND, GroupBuyActorType.CREATOR, "CREATOR_REASON");

        detail(groupBuy.getId())
                .andExpect(jsonPath("$.activeRequest.requesterType").value("CREATOR"))
                .andExpect(jsonPath("$.activeRequest.requesterName").value("글로우_지민"))
                // 인플루언서 사유 코드는 미정이다 — 그럴듯한 라벨을 지어내지 않는다.
                .andExpect(jsonPath("$.activeRequest.reasonLabel").doesNotExist());
        action(groupBuy.getId(), "early-close-request", Map.of("reasonCode", "STOCK_OUT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_REQUEST_ALREADY_PENDING"));
    }

    @Test
    @DisplayName("중단 요청 — 준비완료에서도 받는다(C4) · 준비중은 409 · 기타 사유는 메모 필수")
    void suspensionRequestFromReady() throws Exception {
        GroupBuy ready = seedIn(GroupBuyStatus.READY);
        action(ready.getId(), "suspension-request", Map.of("reasonCode", "ETC")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_REASON_MEMO_REQUIRED"));
        action(ready.getId(), "suspension-request", Map.of("reasonCode", "ETC", "memo", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_REASON_MEMO_REQUIRED"));

        action(ready.getId(), "suspension-request", Map.of("reasonCode", "PRICE_TERMS_ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("READY"))
                .andExpect(jsonPath("$.activeRequest.type").value("SUSPEND"))
                .andExpect(jsonPath("$.activeRequest.statusAtRequest").value("READY"))
                .andExpect(jsonPath("$.history[-1:].eventType").value(contains("SUSPENSION_REQUESTED")));

        GroupBuy preparing = seedPreparing();
        action(preparing.getId(), "suspension-request", Map.of("reasonCode", "QUALITY_ISSUE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_ACTION_NOT_ALLOWED"));
        // 재고 소진은 중단 사유가 아니다 — enum에 없다.
        action(ready.getId(), "suspension-request", Map.of("reasonCode", "STOCK_OUT")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("게시물 숨김 중에는 연장·조기 마감·중단 요청이 모두 막힌다(B4j)")
    void hiddenPostBlocksRequests() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, true);

        detail(groupBuy.getId())
                .andExpect(jsonPath("$.post.status").value("HIDDEN"))
                .andExpect(jsonPath("$.post.hiddenReason.code").value("AD_DISCLOSURE"))
                .andExpect(jsonPath("$.post.hiddenDays").value(0));
        action(groupBuy.getId(), "extension-request", Map.of("extensionDays", 3))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_BUY_ACTION_NOT_ALLOWED"));
        action(groupBuy.getId(), "early-close-request", Map.of("reasonCode", "STOCK_OUT"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_BUY_ACTION_NOT_ALLOWED"));
        action(groupBuy.getId(), "suspension-request", Map.of("reasonCode", "QUALITY_ISSUE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_BUY_ACTION_NOT_ALLOWED"));
    }

    // ── B4i · C9 직권 중단 소명 ───────────────────────────────────────────

    @Test
    @DisplayName("소명 — 제출하면 상태는 중단 예정 그대로 · 수정·재제출 불가 · 중단 예정 중엔 다른 요청 불가")
    void appealOnce() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedNotice(groupBuy.getId(), LocalDateTime.now().plusDays(2).withNano(0));

        detail(groupBuy.getId())
                .andExpect(jsonPath("$.adminSuspension.kind").value("NOTICE"))
                .andExpect(jsonPath("$.adminSuspension.reasonClause").value("ART17_1_LAW"))
                .andExpect(jsonPath("$.adminSuspension.noticeBody").value("게시물 본문에 의약품 오인 표현이 포함되어 있습니다."))
                .andExpect(jsonPath("$.adminSuspension.appeal").doesNotExist());
        action(groupBuy.getId(), "suspension-request", Map.of("reasonCode", "QUALITY_ISSUE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_BUY_ACTION_NOT_ALLOWED"));

        action(groupBuy.getId(), "appeal", Map.of("content", "해당 표현은 시험성적서에 근거합니다."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("SUSPENSION_SCHEDULED"))
                .andExpect(jsonPath("$.adminSuspension.appeal.content").value("해당 표현은 시험성적서에 근거합니다."))
                .andExpect(jsonPath("$.adminSuspension.appeal.submittedAt").exists())
                .andExpect(jsonPath("$.permissions.canSubmitAppeal").value(false))
                .andExpect(jsonPath("$.history[-1:].eventType").value(contains("APPEAL_SUBMITTED")));

        action(groupBuy.getId(), "appeal", Map.of("content", "추가 소명")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_APPEAL_ALREADY_SUBMITTED"));
        summary().andExpect(jsonPath("$.actionRequiredCount").value(0));
    }

    @Test
    @DisplayName("소명 기한이 지나면 409 · 통지가 없으면 409 · 증빙은 PNG·JPG·PDF 10MB 이하만")
    void appealWindow() throws Exception {
        GroupBuy overdue = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedNotice(overdue.getId(), LocalDateTime.now().minusHours(1).withNano(0));
        action(overdue.getId(), "appeal", Map.of("content", "늦은 소명")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_APPEAL_DEADLINE_PASSED"));

        GroupBuy selling = seedIn(GroupBuyStatus.IN_PROGRESS);
        action(selling.getId(), "appeal", Map.of("content", "소명")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_APPEAL_NOT_OPEN"));

        GroupBuy noticed = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedNotice(noticed.getId(), LocalDateTime.now().plusDays(2).withNano(0));
        action(noticed.getId(), "appeal/attachments",
                Map.of("fileName", "영상.mp4", "contentType", "video/mp4", "sizeBytes", 1000))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_APPEAL_ATTACHMENT_INVALID"));
        action(noticed.getId(), "appeal/attachments",
                Map.of("fileName", "시험성적서.pdf", "contentType", "application/pdf", "sizeBytes", 11L * 1024 * 1024))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("소명 증빙 — presign 발급 후 제출 시 실제 업로드를 다시 본다 · 올라가지 않은 파일은 400이고 소명도 남지 않는다")
    void appealWithAttachment() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedNotice(groupBuy.getId(), LocalDateTime.now().plusDays(2).withNano(0));
        given(appealStorage.newKey(any(), eq("application/pdf"))).willReturn("uploads/group-buy/appeal/1/a.pdf", "uploads/group-buy/appeal/1/b.pdf");
        given(appealStorage.presignUpload(any(), any())).willReturn("https://upload.test/presigned");

        long uploaded = readId(action(groupBuy.getId(), "appeal/attachments",
                Map.of("fileName", "시험성적서.pdf", "contentType", "application/pdf", "sizeBytes", 812_345))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadUrl").value(startsWith("https://")))
                .andExpect(jsonPath("$.expiresAt").exists()));
        long missing = readId(action(groupBuy.getId(), "appeal/attachments",
                Map.of("fileName", "사진.pdf", "contentType", "application/pdf", "sizeBytes", 1_000))
                .andExpect(status().isCreated()));
        given(appealStorage.head("uploads/group-buy/appeal/1/a.pdf"))
                .willReturn(Optional.of(new GroupBuyAppealAttachmentStorage.UploadedObject(812_000L, "application/pdf")));
        given(appealStorage.head("uploads/group-buy/appeal/1/b.pdf")).willReturn(Optional.empty());

        action(groupBuy.getId(), "appeal", Map.of("content", "근거 자료 첨부", "attachmentIds", List.of(uploaded, missing)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_APPEAL_ATTACHMENT_INVALID"));
        detail(groupBuy.getId()).andExpect(jsonPath("$.permissions.canSubmitAppeal").value(true));

        action(groupBuy.getId(), "appeal", Map.of("content", "근거 자료 첨부", "attachmentIds", List.of(uploaded)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminSuspension.appeal.attachments[*].originalName").value(contains("시험성적서.pdf")))
                // 실제로 올라간 크기로 확정한다 — presign 요청값은 클라이언트가 말한 것일 뿐이다.
                .andExpect(jsonPath("$.adminSuspension.appeal.attachments[0].sizeBytes").value(812_000));
    }

    private long readId(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return com.jayway.jsonpath.JsonPath.parse(result.andReturn().getResponse().getContentAsString())
                .read("$.attachmentId", Number.class).longValue();
    }

    // ── B5 · C5~C7 종료 후 ────────────────────────────────────────────────

    @Test
    @DisplayName("이행 확인 — 이행은 1회 · 불가역 · 미이행은 사유 필수 · 3자 스레드 전에는 503")
    void fulfillmentCheck() throws Exception {
        GroupBuy ended = seedIn(GroupBuyStatus.ENDED);
        action(ended.getId(), "fulfillment-check", Map.of("result", "UNFULFILLED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_FULFILLMENT_REASON_REQUIRED"));
        // 미이행은 3자 스레드가 첫 글을 받아야 성립한다 — 스레드 모델 변경 전에는 열지 않는다(설계서 5-3).
        action(ended.getId(), "fulfillment-check", Map.of("result", "UNFULFILLED", "reason", "스토리 1건 누락"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_THREAD_UNAVAILABLE"));

        action(ended.getId(), "fulfillment-check", Map.of("result", "FULFILLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afterEnd.fulfillment.mine.result").value("FULFILLED"))
                .andExpect(jsonPath("$.afterEnd.fulfillment.mine.auto").value(false))
                .andExpect(jsonPath("$.afterEnd.fulfillment.theirs").doesNotExist())
                .andExpect(jsonPath("$.afterEnd.fulfillment.onHold").value(false))
                .andExpect(jsonPath("$.permissions.canCheckFulfillment").value(false))
                .andExpect(jsonPath("$.history[-1:].eventType").value(contains("FULFILLMENT_CONFIRMED")));

        action(ended.getId(), "fulfillment-check", Map.of("result", "FULFILLED")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_FULFILLMENT_ALREADY_CHECKED"));

        GroupBuy selling = seedIn(GroupBuyStatus.IN_PROGRESS);
        action(selling.getId(), "fulfillment-check", Map.of("result", "FULFILLED")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_ACTION_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("이슈 스레드 — 진행중엔 409 · 종료 후에는 3자 스레드 전까지 503이고 이슈 행도 남지 않는다")
    void issueThread() throws Exception {
        GroupBuy selling = seedIn(GroupBuyStatus.IN_PROGRESS);
        action(selling.getId(), "issues", Map.of("issueType", "CONTENT_FULFILLMENT", "content", "스토리 누락"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_ACTION_NOT_ALLOWED"));

        GroupBuy ended = seedIn(GroupBuyStatus.ENDED);
        action(ended.getId(), "issues", Map.of("issueType", "CONTENT_FULFILLMENT", "content", "스토리 누락"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_THREAD_UNAVAILABLE"));
        detail(ended.getId()).andExpect(jsonPath("$.afterEnd.openIssue").doesNotExist())
                .andExpect(jsonPath("$.permissions.canOpenIssue").value(true));
        assertThat(groupBuyHistoryRepository.findByGroupBuyIdOrderByOccurredAtAscIdAsc(ended.getId()))
                .noneMatch(entry -> entry.getEventType() == GroupBuyEventType.ISSUE_OPENED);
    }

    @Test
    @DisplayName("연장 요청은 기록만 남긴다 — 수락 전에는 상태도 종료일도 그대로다")
    void extensionDoesNotChangeStatus() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        action(groupBuy.getId(), "extension-request", Map.of("extensionDays", 2)).andExpect(status().isOk());
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.IN_PROGRESS);
        assertThat(extensionRequestRepository.findByGroupBuyId(groupBuy.getId()).orElseThrow().getStatus())
                .isEqualTo(ExtensionRequestStatus.PENDING);
    }
}
