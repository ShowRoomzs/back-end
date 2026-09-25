package showroomz.api.seller.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.groupbuy.dto.GroupBuyAppealAttachmentPresignRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyAppealAttachmentPresignResponse;
import showroomz.api.seller.groupbuy.dto.GroupBuyAppealSubmitRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyDetailResponse;
import showroomz.api.seller.groupbuy.dto.GroupBuyEarlyCloseRequestRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyExtensionRequestRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyFulfillmentCheckRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyIssueOpenRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyIssueOpenResponse;
import showroomz.api.seller.groupbuy.dto.GroupBuySuspensionRequestRequest;
import showroomz.api.seller.groupbuy.service.GroupBuyAccessGuard.SellerScope;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyAppealAttachment;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.entity.GroupBuyFulfillmentCheck;
import showroomz.domain.groupbuy.entity.GroupBuyIssue;
import showroomz.domain.groupbuy.repository.GroupBuyAdminSuspensionRepository;
import showroomz.domain.groupbuy.repository.GroupBuyAppealAttachmentRepository;
import showroomz.domain.groupbuy.repository.GroupBuyChangeRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyExtensionRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyFulfillmentCheckRepository;
import showroomz.domain.groupbuy.repository.GroupBuyIssueRepository;
import showroomz.domain.groupbuy.service.GroupBuyFacts;
import showroomz.domain.groupbuy.service.GroupBuyFactsLoader;
import showroomz.domain.groupbuy.service.GroupBuyHistoryRecorder;
import showroomz.domain.groupbuy.service.GroupBuyNotifier;
import showroomz.domain.groupbuy.service.GroupBuyReadiness;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader.GroupBuySales;
import showroomz.domain.groupbuy.service.port.GroupBuyThreadGateway;
import showroomz.domain.groupbuy.type.ChangeRequestStatus;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.FulfillmentResult;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyAttachmentStatus;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.global.config.properties.GroupBuyProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 파트너센터 공구 실행(설계서 4-6).
 *
 * <p>모든 실행은 공구 행을 {@code PESSIMISTIC_WRITE}로 잠근 뒤 {@link GroupBuyPermissionPolicy}의 <b>버튼 판정과
 * 같은 메서드</b>로 다시 판정한다. 판정이 거짓이면 409다. 그 뒤에 더 구체적인 사유(이미 사용·기한·검토 중)를
 * 먼저 골라 내려 FE가 문구를 맞출 수 있게 한다.
 *
 * <p>만들지 않는 것 — 공구 생성·수정·삭제 · 기간 단축 · 게시물 쓰기 · 숨김 · <b>요청 취소</b>(B4a·B4g
 * 「취소할 수 없습니다」) · 물량 확인 해제(§33-1 #10) · 고정 지급비 기록(계약 관리 소관).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SellerGroupBuyCommandService {

    private final GroupBuyAccessGuard accessGuard;
    private final GroupBuyFactsLoader factsLoader;
    private final GroupBuyPermissionPolicy permissionPolicy;
    private final GroupBuyDetailAssembler detailAssembler;
    private final GroupBuyReadiness readiness;
    private final GroupBuyHistoryRecorder historyRecorder;
    private final GroupBuyNotifier notifier;
    private final GroupBuyExtensionRequestRepository extensionRequestRepository;
    private final GroupBuyChangeRequestRepository changeRequestRepository;
    private final GroupBuyAdminSuspensionRepository adminSuspensionRepository;
    private final GroupBuyAppealAttachmentRepository appealAttachmentRepository;
    private final GroupBuyFulfillmentCheckRepository fulfillmentCheckRepository;
    private final GroupBuyIssueRepository issueRepository;
    private final GroupBuySalesReader salesReader;
    private final GroupBuyThreadGateway threadGateway;
    private final GroupBuyAppealAttachmentStorage appealStorage;
    private final GroupBuyProperties properties;

    // ── B1 [확보 완료] ────────────────────────────────────────────────────────

    /**
     * 최소 물량 확보 확인 — 게이트 ①. <b>서버는 재고를 판정하지 않는다</b> — 브랜드의 자기 확인이다.
     * 이력의 detail에 최소 물량 스냅샷을 남긴다 — 제25조 제재 판정 때 「무엇을 확인했는지」가 이력만으로 읽혀야 한다.
     * 운영자 오픈 승인이 이미 끝났으면 이 요청이 READY를 만든다.
     */
    public GroupBuyDetailResponse confirmStock(String sellerEmail, Long groupBuyId) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        GroupBuy groupBuy = accessGuard.loadOwnedForUpdate(groupBuyId, scope.market());
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        if (groupBuy.isStockConfirmed()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_STOCK_ALREADY_CONFIRMED);
        }
        if (!permissionPolicy.canConfirmStock(facts)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_STATUS_CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        groupBuy.applyStockConfirmed(scope.sellerId(), now);
        historyRecorder.recordBySeller(groupBuy, GroupBuyEventType.STOCK_CONFIRMED, minQuantitySnapshot(groupBuy), now);
        notifier.notifyAdmin(groupBuy, "STOCK_CONFIRMED");
        readiness.promoteIfSatisfied(groupBuy, GroupBuyActorType.SELLER, scope.market().getId(),
                scope.market().getMarketName(), true, now);
        return detailAssembler.assemble(groupBuy);
    }

    /** 「크림 300개 · 세럼 200개」. */
    private static String minQuantitySnapshot(GroupBuy groupBuy) {
        return groupBuy.getContract().getItems().stream()
                .map(item -> "%s %,d개".formatted(item.getProductName(), nullToZero(item)))
                .collect(Collectors.joining(" · "));
    }

    private static int nullToZero(ContractItem item) {
        return item.getMinQuantity() == null ? 0 : item.getMinQuantity();
    }

    // ── C1 기간 연장 요청 ─────────────────────────────────────────────────────

    /**
     * 공구당 1회 — 수락되든 거절되든 기회가 소진된다(§29-6). 요청만으로 {@code end_at}은 바뀌지 않는다.
     * 총 30일 기준은 시작 일자부터 새 종료 일자까지 양끝 포함이다 — 계약 H4와 같은 계산.
     */
    public GroupBuyDetailResponse requestExtension(String sellerEmail, Long groupBuyId,
                                                   GroupBuyExtensionRequestRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        GroupBuy groupBuy = accessGuard.loadOwnedForUpdate(groupBuyId, scope.market());
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        LocalDateTime now = LocalDateTime.now();

        requireStatus(groupBuy, GroupBuyStatus.IN_PROGRESS);
        requireRequestable(facts);
        if (facts.extension() != null) {
            throw new BusinessException(ErrorCode.GROUP_BUY_EXTENSION_ALREADY_USED);
        }
        if (!permissionPolicy.isBeforeExtensionCutoff(groupBuy, now)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_EXTENSION_WINDOW_CLOSED);
        }
        int days = request.extensionDays();
        if (groupBuy.totalDays() + days > properties.getExtension().getMaxTotalDays()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_EXTENSION_EXCEEDS_LIMIT);
        }
        if (!permissionPolicy.canRequestExtension(facts, now)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ACTION_NOT_ALLOWED);
        }

        String reason = trimToNull(request.reason());
        try {
            extensionRequestRepository.saveAndFlush(
                    GroupBuyExtensionRequest.request(groupBuy, scope.sellerId(), days, reason, now));
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(group_buy_id) — 선검사를 통과한 동시 요청은 DB가 떨어뜨린다(설계서 1-5).
            throw new BusinessException(ErrorCode.GROUP_BUY_EXTENSION_ALREADY_USED);
        }
        historyRecorder.recordBySeller(groupBuy, GroupBuyEventType.EXTENSION_REQUESTED,
                reason == null ? days + "일" : days + "일 · " + reason, now);
        notifier.notifyCreator(groupBuy, "EXTENSION_REQUESTED");
        return detailAssembler.assemble(groupBuy);
    }

    // ── C3 조기 마감 요청 · C2/C4 공구 중단 요청 ─────────────────────────────────

    /** 공구 상태는 그대로 IN_PROGRESS다 — 승인(어드민)만 상태를 바꾼다. 반려 후 재요청은 막지 않는다(B4h). */
    public GroupBuyDetailResponse requestEarlyClose(String sellerEmail, Long groupBuyId,
                                                    GroupBuyEarlyCloseRequestRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        GroupBuy groupBuy = accessGuard.loadOwnedForUpdate(groupBuyId, scope.market());
        GroupBuyFacts facts = factsLoader.load(groupBuy);

        requireStatus(groupBuy, GroupBuyStatus.IN_PROGRESS);
        requireRequestable(facts);
        if (!permissionPolicy.canRequestEarlyClose(facts)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ACTION_NOT_ALLOWED);
        }
        String memo = trimToNull(request.memo());
        if (request.reasonCode().requiresMemo() && memo == null) {
            throw new BusinessException(ErrorCode.GROUP_BUY_REASON_MEMO_REQUIRED);
        }

        LocalDateTime now = LocalDateTime.now();
        createChangeRequest(groupBuy, scope, ChangeRequestType.EARLY_CLOSE, request.reasonCode().name(), memo, now);
        historyRecorder.recordBySeller(groupBuy, GroupBuyEventType.EARLY_CLOSE_REQUESTED,
                withMemo(request.reasonCode().getLabel(), memo), now);
        notifier.notifyAdmin(groupBuy, "EARLY_CLOSE_REQUESTED");
        return detailAssembler.assemble(groupBuy);
    }

    /**
     * 준비완료에서도 받는다(C4). 요청만으로 판매는 멈추지 않는다(제16조②) — 상태 불변.
     * 「배송을 멈추지 마세요」는 FE 문구지만, 요청 ≠ 집행이라는 사실은 서버가 상태로 보장한다.
     */
    public GroupBuyDetailResponse requestSuspension(String sellerEmail, Long groupBuyId,
                                                    GroupBuySuspensionRequestRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        GroupBuy groupBuy = accessGuard.loadOwnedForUpdate(groupBuyId, scope.market());
        GroupBuyFacts facts = factsLoader.load(groupBuy);

        if (groupBuy.getStatus() != GroupBuyStatus.READY && groupBuy.getStatus() != GroupBuyStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ACTION_NOT_ALLOWED);
        }
        requireRequestable(facts);
        if (!permissionPolicy.canRequestSuspension(facts)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ACTION_NOT_ALLOWED);
        }
        String memo = trimToNull(request.memo());
        if (request.reasonCode().requiresMemo() && memo == null) {
            throw new BusinessException(ErrorCode.GROUP_BUY_REASON_MEMO_REQUIRED);
        }

        LocalDateTime now = LocalDateTime.now();
        createChangeRequest(groupBuy, scope, ChangeRequestType.SUSPEND, request.reasonCode().name(), memo, now);
        historyRecorder.recordBySeller(groupBuy, GroupBuyEventType.SUSPENSION_REQUESTED,
                withMemo(request.reasonCode().getLabel(), memo), now);
        notifier.notifyAdmin(groupBuy, "SUSPENSION_REQUESTED");
        return detailAssembler.assemble(groupBuy);
    }

    private void createChangeRequest(GroupBuy groupBuy, SellerScope scope, ChangeRequestType type,
                                     String reasonCode, String memo, LocalDateTime now) {
        // 어드민 B3 「요청 후 증가분」의 기준점. 판매 포트가 비어 있으면 null — 0이 아니다(설계서 0-6).
        GroupBuySales sales = salesReader.readSales(groupBuy.getId()).orElse(null);
        GroupBuyChangeRequest changeRequest = GroupBuyChangeRequest.builder()
                .groupBuy(groupBuy)
                .requestType(type)
                .requesterType(GroupBuyActorType.SELLER)
                .requesterId(scope.sellerId())
                .reasonCode(reasonCode)
                .memo(memo)
                .statusAtRequest(groupBuy.getStatus())
                .salesOrderCountAtRequest(sales == null ? null : sales.orderCount())
                .salesAmountAtRequest(sales == null ? null : sales.amount())
                .status(ChangeRequestStatus.PENDING)
                .requestedAt(now)
                .build();
        try {
            changeRequestRepository.saveAndFlush(changeRequest);
        } catch (DataIntegrityViolationException e) {
            // pending_group_buy_id UNIQUE — 브랜드·인플루언서 요청이 동시에 들어오면 하나는 DB에서 떨어진다.
            throw new BusinessException(ErrorCode.GROUP_BUY_REQUEST_ALREADY_PENDING);
        }
    }

    // ── C9 직권 중단 소명 ─────────────────────────────────────────────────────

    /** 소명 증빙 업로드 URL — PENDING 행을 먼저 만들고, 제출 시 HeadObject로 확정한다. */
    public GroupBuyAppealAttachmentPresignResponse presignAppealAttachment(String sellerEmail, Long groupBuyId,
                                                                           GroupBuyAppealAttachmentPresignRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        GroupBuy groupBuy = accessGuard.loadOwnedForUpdate(groupBuyId, scope.market());
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        LocalDateTime now = LocalDateTime.now();
        GroupBuyAdminSuspension notice = requireAppealOpen(facts, now);

        if (!GroupBuyAppealAttachmentStorage.isAllowedContentType(request.contentType())
                || request.sizeBytes() > properties.getAppeal().getMaxAttachmentBytes()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_APPEAL_ATTACHMENT_INVALID);
        }
        long attached = appealAttachmentRepository.countByAdminSuspensionIdAndStatusNot(
                notice.getId(), GroupBuyAttachmentStatus.REJECTED);
        if (attached >= properties.getAppeal().getMaxAttachments()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_APPEAL_ATTACHMENT_INVALID,
                    "증빙은 최대 " + properties.getAppeal().getMaxAttachments() + "개까지 첨부할 수 있습니다.");
        }

        String key = appealStorage.newKey(notice.getId(), request.contentType());
        GroupBuyAppealAttachment attachment = appealAttachmentRepository.save(GroupBuyAppealAttachment.pending(
                notice, scope.sellerId(), key, request.fileName(), request.contentType(), request.sizeBytes(), now));
        return new GroupBuyAppealAttachmentPresignResponse(
                attachment.getId(),
                appealStorage.presignUpload(key, request.contentType()),
                request.contentType(),
                now.plus(GroupBuyAppealAttachmentStorage.PRESIGN_EXPIRY));
    }

    /**
     * 소명 제출 — <b>제출 후 수정 API가 없다</b>(C9). 소명은 운영자가 판단 근거로 읽은 글이라 바뀌면 판정의 근거가
     * 흔들린다. 상태는 그대로 SUSPENSION_SCHEDULED다.
     */
    public GroupBuyDetailResponse submitAppeal(String sellerEmail, Long groupBuyId, GroupBuyAppealSubmitRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        GroupBuy groupBuy = accessGuard.loadOwnedForUpdate(groupBuyId, scope.market());
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        LocalDateTime now = LocalDateTime.now();
        GroupBuyAdminSuspension notice = requireAppealOpen(facts, now);

        List<Long> attachmentIds = request.attachmentIds() == null ? List.of()
                : List.copyOf(new LinkedHashSet<>(request.attachmentIds().stream().filter(Objects::nonNull).toList()));
        if (attachmentIds.size() > properties.getAppeal().getMaxAttachments()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_APPEAL_ATTACHMENT_INVALID,
                    "증빙은 최대 " + properties.getAppeal().getMaxAttachments() + "개까지 첨부할 수 있습니다.");
        }
        for (Long attachmentId : attachmentIds) {
            confirmUploaded(notice, attachmentId, now);
        }

        String content = request.content().trim();
        if (adminSuspensionRepository.submitAppeal(notice.getId(), content, scope.sellerId(), now) != 1) {
            throw new BusinessException(ErrorCode.GROUP_BUY_APPEAL_ALREADY_SUBMITTED);
        }
        notice.applyAppealSubmitted(content, scope.sellerId(), now);
        historyRecorder.recordBySeller(groupBuy, GroupBuyEventType.APPEAL_SUBMITTED,
                attachmentIds.isEmpty() ? null : "증빙 " + attachmentIds.size() + "건", now);
        notifier.notifyAdmin(groupBuy, "APPEAL_SUBMITTED");
        return detailAssembler.assemble(groupBuy);
    }

    /** presign 요청값은 클라이언트가 말한 것일 뿐이다 — 실제로 올라간 객체의 크기·타입을 다시 본다. */
    private void confirmUploaded(GroupBuyAdminSuspension notice, Long attachmentId, LocalDateTime now) {
        GroupBuyAppealAttachment attachment = appealAttachmentRepository.findById(attachmentId)
                .filter(a -> a.getAdminSuspension().getId().equals(notice.getId()))
                .filter(a -> !a.isRejected())
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_APPEAL_ATTACHMENT_INVALID));
        if (attachment.getStatus() == GroupBuyAttachmentStatus.UPLOADED) {
            return;
        }
        GroupBuyAppealAttachmentStorage.UploadedObject uploaded = appealStorage.head(attachment.getS3Key())
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_APPEAL_ATTACHMENT_INVALID));
        boolean sizeOk = uploaded.contentLength() != null
                && uploaded.contentLength() <= properties.getAppeal().getMaxAttachmentBytes();
        boolean typeOk = attachment.getContentType().equalsIgnoreCase(uploaded.contentType());
        if (!sizeOk || !typeOk) {
            throw new BusinessException(ErrorCode.GROUP_BUY_APPEAL_ATTACHMENT_INVALID);
        }
        attachment.markUploaded(uploaded.contentLength(), now);
    }

    /** 소명 창이 닫혀 있으면 사유를 가려 409 — 통지 없음 · 이미 제출 · 기한 경과. */
    private GroupBuyAdminSuspension requireAppealOpen(GroupBuyFacts facts, LocalDateTime now) {
        GroupBuyAdminSuspension notice = facts.activeNotice()
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_APPEAL_NOT_OPEN));
        if (notice.isAppealSubmitted()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_APPEAL_ALREADY_SUBMITTED);
        }
        if (notice.getAppealDeadlineAt() != null && now.isAfter(notice.getAppealDeadlineAt())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_APPEAL_DEADLINE_PASSED);
        }
        if (!permissionPolicy.canSubmitAppeal(facts, now)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_APPEAL_NOT_OPEN);
        }
        return notice;
    }

    // ── C5 이슈 스레드 · C6/C7 이행 확인 ──────────────────────────────────────

    /**
     * 이슈 스레드 — 공구 상태 불변, 정산 보류도 하지 않는다(B5e). 스레드 개설과 이슈 INSERT가 같은 트랜잭션이다 —
     * 스레드만 생기고 이슈 행이 없으면 「중복 개설 방지」가 깨진다.
     */
    public GroupBuyIssueOpenResponse openIssue(String sellerEmail, Long groupBuyId, GroupBuyIssueOpenRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        GroupBuy groupBuy = accessGuard.loadOwnedForUpdate(groupBuyId, scope.market());
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        if (facts.openIssue() != null) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ISSUE_ALREADY_OPEN);
        }
        if (!permissionPolicy.canOpenIssue(facts)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ACTION_NOT_ALLOWED);
        }

        LocalDateTime now = LocalDateTime.now();
        String content = request.content().trim();
        Long threadId = threadGateway.openIssueThread(groupBuy, FulfillmentSide.SELLER, request.issueType(), content);
        GroupBuyIssue issue;
        try {
            issue = issueRepository.saveAndFlush(GroupBuyIssue.open(groupBuy, GroupBuyActorType.SELLER,
                    scope.sellerId(), request.issueType(), content, threadId, now));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ISSUE_ALREADY_OPEN);
        }
        historyRecorder.recordBySeller(groupBuy, GroupBuyEventType.ISSUE_OPENED, request.issueType().getLabel(), now);
        notifier.notifyCreator(groupBuy, "ISSUE_OPENED");
        notifier.notifyAdmin(groupBuy, "ISSUE_OPENED");
        return new GroupBuyIssueOpenResponse(issue.getId(), threadId);
    }

    /**
     * 계약 이행 확인 — 측별 1회 · 불가역(제20조②). 브랜드 요청은 항상 SELLER 측으로 저장되고, 뜻은
     * 「인플루언서의 콘텐츠 의무를 확인했다」이다. 미이행이면 같은 트랜잭션에서 3자 스레드를 연다.
     * 기한이 지나도 받는다 — 자동 이행 스위치가 꺼져 있는 동안 기한은 표시값이다.
     */
    public GroupBuyDetailResponse checkFulfillment(String sellerEmail, Long groupBuyId,
                                                   GroupBuyFulfillmentCheckRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        GroupBuy groupBuy = accessGuard.loadOwnedForUpdate(groupBuyId, scope.market());
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        if (facts.fulfillmentCheck(FulfillmentSide.SELLER).isPresent()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_FULFILLMENT_ALREADY_CHECKED);
        }
        if (!permissionPolicy.canCheckFulfillment(facts)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ACTION_NOT_ALLOWED);
        }
        boolean unfulfilled = request.result() == FulfillmentResult.UNFULFILLED;
        String reason = trimToNull(request.reason());
        if (unfulfilled && reason == null) {
            throw new BusinessException(ErrorCode.GROUP_BUY_FULFILLMENT_REASON_REQUIRED);
        }

        LocalDateTime now = LocalDateTime.now();
        Long threadId = unfulfilled
                ? threadGateway.openFulfillmentDisputeThread(groupBuy, FulfillmentSide.SELLER, reason)
                : null;
        try {
            fulfillmentCheckRepository.saveAndFlush(GroupBuyFulfillmentCheck.manual(groupBuy, FulfillmentSide.SELLER,
                    request.result(), unfulfilled ? reason : null, scope.sellerId(), threadId, now));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.GROUP_BUY_FULFILLMENT_ALREADY_CHECKED);
        }
        historyRecorder.recordBySeller(groupBuy,
                unfulfilled ? GroupBuyEventType.FULFILLMENT_DISPUTED : GroupBuyEventType.FULFILLMENT_CONFIRMED,
                unfulfilled ? reason : null, now);
        notifier.notifyCreator(groupBuy, unfulfilled ? "FULFILLMENT_DISPUTED" : "FULFILLMENT_CONFIRMED");
        if (unfulfilled) {
            notifier.notifyAdmin(groupBuy, "FULFILLMENT_DISPUTED");
        }
        return detailAssembler.assemble(groupBuy);
    }

    // ── 공통 ─────────────────────────────────────────────────────────────────

    private static void requireStatus(GroupBuy groupBuy, GroupBuyStatus expected) {
        if (groupBuy.getStatus() != expected) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ACTION_NOT_ALLOWED);
        }
    }

    /** 요청 차단 조건(설계서 4-5) — 검토 중 요청이면 그 사유를, 나머지는 일반 409를 내린다. */
    private void requireRequestable(GroupBuyFacts facts) {
        if (facts.pendingChangeRequest().isPresent()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_REQUEST_ALREADY_PENDING);
        }
        if (permissionPolicy.isRequestBlocked(facts)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ACTION_NOT_ALLOWED);
        }
    }

    private static String withMemo(String label, String memo) {
        return memo == null ? label : label + " · " + memo;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
