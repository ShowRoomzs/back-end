package showroomz.api.creator.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.creator.groupbuy.dto.CreatorExtensionRejectRequest;
import showroomz.api.creator.groupbuy.dto.CreatorFulfillmentCheckRequest;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuyDetailResponse;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuyPostRequest;
import showroomz.api.creator.groupbuy.dto.CreatorSuspensionRequestRequest;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.entity.GroupBuyPostRevision;
import showroomz.domain.groupbuy.repository.GroupBuyChangeRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyExtensionRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.repository.GroupBuyPostRevisionRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.service.GroupBuyFacts;
import showroomz.domain.groupbuy.service.GroupBuyFactsLoader;
import showroomz.domain.groupbuy.service.GroupBuyFulfillmentService;
import showroomz.domain.groupbuy.service.GroupBuyHistoryRecorder;
import showroomz.domain.groupbuy.service.GroupBuyNotifier;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader.GroupBuySales;
import showroomz.domain.groupbuy.type.ChangeRequestStatus;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.ExtensionRejectReason;
import showroomz.domain.groupbuy.type.ExtensionRequestStatus;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyPostRevisionKind;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.policy.GroupBuyPostPolicy;
import showroomz.domain.post.policy.PostPolicies;
import showroomz.domain.post.repository.PostRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 스튜디오 공구 실행(31 설계 2 · 5절).
 *
 * <p>모든 실행은 공구 행을 {@code PESSIMISTIC_WRITE}로 잠근 뒤 {@link CreatorGroupBuyPermissionPolicy}의 <b>버튼 판정과
 * 같은 메서드</b>로 다시 판정한다. 게시물 쓰기는 그다음 {@code group_buy_post} 행까지 잠근다 — 잠금 순서는
 * 공구 → 게시물이고, 어드민의 승인·반려·숨김·해제도 같은 순서를 따라야 교착이 나지 않는다(31 설계 2-7).
 *
 * <p>만들지 않는 것 — 공구 생성·수정·삭제 · 연장·조기 마감 요청(브랜드만 발의) · 게시물 제출 취소·삭제 · 사진 업로드 ·
 * 직권 중단 소명(인플루언서 약관에 소명 조항이 없다) · 요청 취소 · 이슈 스레드 개설 · 고정 지급비 수령 확인.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CreatorGroupBuyCommandService {

    private static final DateTimeFormatter END_AT_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final CreatorGroupBuyReader reader;
    private final CreatorGroupBuyPermissionPolicy permissionPolicy;
    private final CreatorGroupBuyDetailAssembler detailAssembler;
    private final GroupBuyFactsLoader factsLoader;
    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPostRepository groupBuyPostRepository;
    private final GroupBuyPostRevisionRepository revisionRepository;
    private final GroupBuyExtensionRequestRepository extensionRequestRepository;
    private final GroupBuyChangeRequestRepository changeRequestRepository;
    private final PostRepository postRepository;
    private final PostPolicies postPolicies;
    private final GroupBuyHistoryRecorder historyRecorder;
    private final GroupBuyNotifier notifier;
    private final GroupBuySalesReader salesReader;
    private final GroupBuyFulfillmentService fulfillmentService;

    // ── C3 · C4 게시물 임시저장 · 등록하고 검토 요청 ────────────────────────────────

    /**
     * 임시저장(31 설계 2-3). 최초 호출이 게시물을 만들고, 이후 호출은 덮어쓴다. <b>심사 상태는 바뀌지 않는다</b> —
     * 반려 게시물을 임시저장해도 반려 그대로다. 이력·알림·리비전 없음.
     */
    public CreatorGroupBuyDetailResponse saveDraft(String creatorEmail, Long groupBuyId, CreatorGroupBuyPostRequest request) {
        Locked locked = lockForPostWrite(creatorEmail, groupBuyId);
        requireWritable(locked);

        String title = normalizeTitle(request.title());
        String content = normalizeContent(request.content());
        GroupBuyPostPolicy.validateDraft(title, content);

        if (locked.post() == null) {
            createPost(locked.groupBuy(), title, content);
        } else {
            locked.post().rewrite(title, content);
        }
        return detailAssembler.assemble(locked.groupBuy(), null);
    }

    /**
     * 등록하고 검토 요청(31 설계 2-4) — <b>저장과 제출을 한 요청으로 받는다.</b> 두 API로 나누면 사이에 실패가 끼어
     * 「저장은 됐는데 제출은 안 된」 상태가 생기고 사용자는 제출했다고 믿는다.
     *
     * <p>공구 상태는 바뀌지 않는다 — 제출은 게이트 ②를 채울 뿐 ③(운영자 승인)이 남아 있다. 시작 시각이 지난 PREPARING에서도
     * 받는다(30 설계 7-2 #2). 반려 필드는 지우지 않는다 — 재심사하는 운영자가 이전 지적을 봐야 한다.
     */
    public CreatorGroupBuyDetailResponse submitPost(String creatorEmail, Long groupBuyId,
                                                    CreatorGroupBuyPostRequest request) {
        Locked locked = lockForPostWrite(creatorEmail, groupBuyId);
        requireWritable(locked);

        String title = normalizeTitle(request.title());
        String content = normalizeContent(request.content());
        GroupBuyPostPolicy.validateRequired(title, content);

        LocalDateTime now = LocalDateTime.now();
        GroupBuyPost post = locked.post();
        boolean resubmission = post != null && post.getReviewStatus() == GroupBuyPostReviewStatus.REJECTED;
        if (post == null) {
            post = createPost(locked.groupBuy(), title, content);
        } else {
            post.rewrite(title, content);
        }
        postPolicies.of(post.getPost()).validateForPublish(post.getPost());
        post.submit(now);

        appendRevision(post, GroupBuyPostRevisionKind.SUBMITTED, locked.creatorId(), now);
        historyRecorder.recordByCreator(locked.groupBuy(), GroupBuyEventType.POST_SUBMITTED,
                resubmission ? "재등록" : null, null, now);
        notifier.notifyAdmin(locked.groupBuy(), "POST_SUBMITTED");
        return detailAssembler.assemble(locked.groupBuy(), null);
    }

    // ── B4 · B5 · B5a · B10 승인 후 게시물 수정 ────────────────────────────────────

    /**
     * 승인 후 수정(31 설계 2-5) — <b>즉시 반영 · 재승인 없음</b>(인플 제14조⑤). 수정 기록은 이력이 아니라 리비전이 맡는다.
     *
     * <p>숨김 중 수정은 운영자에게 통지한다 — 해제 경로가 「고치면 운영자가 읽고 해제」인데 고쳤다는 사실을 모르면 숨김이
     * 방치로 끝난다. <b>해제하지는 않는다</b> — 자동 복귀하면 같은 문구로 되돌려도 다시 열린다(§29-8).
     */
    public CreatorGroupBuyDetailResponse editPost(String creatorEmail, Long groupBuyId,
                                                  CreatorGroupBuyPostRequest request) {
        Locked locked = lockForPostWrite(creatorEmail, groupBuyId);
        if (!permissionPolicy.canEditPost(locked.facts())) {
            GroupBuyPostPolicy.requireEditable(locked.post());
            throw new BusinessException(ErrorCode.GROUP_BUY_POST_NOT_EDITABLE);
        }

        String title = normalizeTitle(request.title());
        String content = normalizeContent(request.content());
        GroupBuyPostPolicy.validateRequired(title, content);

        LocalDateTime now = LocalDateTime.now();
        GroupBuyPost post = locked.post();
        post.rewrite(title, content);
        postPolicies.of(post.getPost()).validateEditable(post.getPost());
        post.markEdited(now);
        appendRevision(post, GroupBuyPostRevisionKind.EDITED, locked.creatorId(), now);
        if (post.isHidden()) {
            notifier.notifyAdmin(locked.groupBuy(), "POST_EDITED_WHILE_HIDDEN");
        }
        return detailAssembler.assemble(locked.groupBuy(), null);
    }

    private Locked lockForPostWrite(String creatorEmail, Long groupBuyId) {
        Creator creator = reader.resolveCreator(creatorEmail);
        GroupBuy groupBuy = reader.requireMineForUpdate(creator.getId(), groupBuyId);
        // 잠금 순서: 공구 → 게시물. 사실 스냅샷은 잠근 게시물과 같은 인스턴스를 본다(영속성 컨텍스트).
        GroupBuyPost post = groupBuyPostRepository.findByGroupBuyIdForUpdate(groupBuyId).orElse(null);
        return new Locked(creator.getId(), groupBuy, post, factsLoader.load(groupBuy));
    }

    /** 작성·제출 불가 사유를 가려 409 — 승인대기면 「검토 중」, 그 밖은 「지금은 작성할 수 없음」. */
    private void requireWritable(Locked locked) {
        if (permissionPolicy.canWritePost(locked.facts())) {
            return;
        }
        if (locked.post() != null && locked.post().isPendingReview()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_POST_UNDER_REVIEW);
        }
        throw new BusinessException(ErrorCode.GROUP_BUY_POST_NOT_WRITABLE);
    }

    /**
     * 게시물 최초 생성 — {@code post.creator_id}는 요청자가 아니라 <b>공구의 인플루언서</b>다. 공구 행을 잠갔으므로 동시 최초
     * 저장은 직렬화되지만, 최종 방어선은 {@code group_buy_post.group_buy_id} UNIQUE다 → 409(FE는 다시 조회해 수정 모드로 연다).
     */
    private GroupBuyPost createPost(GroupBuy groupBuy, String title, String content) {
        Post root = postRepository.save(Post.groupBuyDraft(groupBuy.getCreator(), content));
        try {
            return groupBuyPostRepository.saveAndFlush(GroupBuyPost.draft(root, groupBuy, title));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.GROUP_BUY_STATUS_CONFLICT);
        }
    }

    /** 리비전 번호는 게시물 행을 잠근 뒤 매긴다 — 동시 쓰기가 같은 번호를 잡지 않는다. */
    private void appendRevision(GroupBuyPost post, GroupBuyPostRevisionKind kind, Long creatorId, LocalDateTime now) {
        int next = revisionRepository.findLastRevisionNo(post.getPostId()) + 1;
        revisionRepository.save(GroupBuyPostRevision.snapshot(post, next, kind, creatorId, now));
    }

    // ── C1 · C2 연장 응답 ─────────────────────────────────────────────────────

    /**
     * 연장 수락(31 설계 5-1) — 불가역. 요청 행과 공구 종료일을 <b>둘 다 조건부 UPDATE</b>로 바꾸고, 종료일 쪽이 0행이면
     * 예외로 트랜잭션 전체를 되돌린다 — 요청은 ACCEPTED인데 종료일이 안 바뀐 상태가 가장 나쁘다.
     *
     * <p>리워드율·고정 지급비는 건드리지 않는다(계약 값) · 30일 상한은 요청 시점에 검사했다 · 상태가 바뀌지 않으므로 상품
     * 재동기화·게시물 투영도 없다.
     */
    public CreatorGroupBuyDetailResponse acceptExtension(String creatorEmail, Long groupBuyId) {
        Creator creator = reader.resolveCreator(creatorEmail);
        GroupBuy groupBuy = reader.requireMineForUpdate(creator.getId(), groupBuyId);
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        LocalDateTime now = LocalDateTime.now();
        GroupBuyExtensionRequest extension = requireRespondable(facts, now);

        if (extensionRequestRepository.respond(extension.getId(), ExtensionRequestStatus.ACCEPTED,
                GroupBuyActorType.CREATOR, null, null, now) != 1) {
            throw new BusinessException(ErrorCode.GROUP_BUY_EXTENSION_NOT_PENDING);
        }
        if (groupBuyRepository.extendEndAt(groupBuyId, creator.getId(), extension.getBeforeEndAt(),
                extension.getAfterEndAt(), now) != 1) {
            throw new BusinessException(groupBuy.getEndAt().isAfter(now)
                    ? ErrorCode.GROUP_BUY_STATUS_CONFLICT
                    : ErrorCode.GROUP_BUY_EXTENSION_RESPONSE_CLOSED);
        }
        extension.applyResponse(ExtensionRequestStatus.ACCEPTED, null, null, now);
        groupBuy.applyExtended(extension.getAfterEndAt());

        historyRecorder.recordByCreator(groupBuy, GroupBuyEventType.EXTENSION_ACCEPTED,
                "종료일 " + extension.getAfterEndAt().format(END_AT_FORMAT) + "로 변경", extension.getId(), now);
        notifier.notifySeller(groupBuy, "EXTENSION_ACCEPTED");
        return detailAssembler.assemble(groupBuy, null);
    }

    /**
     * 연장 거절(31 설계 5-2) — 수락과 같은 기한이다. 기한이 지나면 스케줄러가 이미 만료로 닫았다 — 한 요청에 「거절」과
     * 「만료」가 둘 다 남지 않게 PENDING 조건부 UPDATE가 하나만 남긴다. 메모는 <b>브랜드에게</b> 보인다(라벨이 수신자를 정한다).
     */
    public CreatorGroupBuyDetailResponse rejectExtension(String creatorEmail, Long groupBuyId,
                                                         CreatorExtensionRejectRequest request) {
        Creator creator = reader.resolveCreator(creatorEmail);
        GroupBuy groupBuy = reader.requireMineForUpdate(creator.getId(), groupBuyId);
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        LocalDateTime now = LocalDateTime.now();
        GroupBuyExtensionRequest extension = requireRespondable(facts, now);

        ExtensionRejectReason reason = request.reasonCode();
        String memo = trimToNull(request.memo());
        if (reason != null && reason.requiresMemo() && memo == null) {
            throw new BusinessException(ErrorCode.GROUP_BUY_REASON_MEMO_REQUIRED);
        }
        String reasonCode = reason == null ? null : reason.name();
        if (extensionRequestRepository.respond(extension.getId(), ExtensionRequestStatus.REJECTED,
                GroupBuyActorType.CREATOR, reasonCode, memo, now) != 1) {
            throw new BusinessException(ErrorCode.GROUP_BUY_EXTENSION_NOT_PENDING);
        }
        extension.applyResponse(ExtensionRequestStatus.REJECTED, reasonCode, memo, now);

        historyRecorder.recordByCreator(groupBuy, GroupBuyEventType.EXTENSION_REJECTED,
                reason == null ? null : reason.getLabel(), extension.getId(), now);
        notifier.notifySeller(groupBuy, "EXTENSION_REJECTED");
        return detailAssembler.assemble(groupBuy, null);
    }

    /** 응답 불가 사유를 가려 409 — 응답할 요청 없음 · 종료 시각 경과 · 그 밖(중단 예정 등). */
    private GroupBuyExtensionRequest requireRespondable(GroupBuyFacts facts, LocalDateTime now) {
        GroupBuyExtensionRequest extension = facts.extension();
        if (extension == null || !extension.isPending()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_EXTENSION_NOT_PENDING);
        }
        if (!now.isBefore(facts.groupBuy().getEndAt())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_EXTENSION_RESPONSE_CLOSED);
        }
        if (!permissionPolicy.canRespondExtension(facts, now)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ACTION_NOT_ALLOWED);
        }
        return extension;
    }

    // ── C7 공구 중단 요청 ─────────────────────────────────────────────────────

    /**
     * 중단 요청(31 설계 5-3) — 공구 상태는 그대로다(제16조② 요청만으로 판매가 멈추지 않는다). 메모는 운영자에게 쓰는 글이라
     * 이력에는 사유 라벨만 남긴다 — 브랜드 화면의 이력에 운영자용 메모가 나가지 않는다.
     */
    public CreatorGroupBuyDetailResponse requestSuspension(String creatorEmail, Long groupBuyId,
                                                           CreatorSuspensionRequestRequest request) {
        Creator creator = reader.resolveCreator(creatorEmail);
        GroupBuy groupBuy = reader.requireMineForUpdate(creator.getId(), groupBuyId);
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        if (facts.pendingChangeRequest().isPresent()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_REQUEST_ALREADY_PENDING);
        }
        if (!permissionPolicy.canRequestSuspension(facts)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ACTION_NOT_ALLOWED);
        }

        LocalDateTime now = LocalDateTime.now();
        // 어드민 「요청 후 증가분」의 기준점. 판매 포트가 비어 있으면 null — 0이 아니다.
        GroupBuySales sales = salesReader.readSales(groupBuyId).orElse(null);
        GroupBuyChangeRequest changeRequest = GroupBuyChangeRequest.builder()
                .groupBuy(groupBuy)
                .requestType(ChangeRequestType.SUSPEND)
                .requesterType(GroupBuyActorType.CREATOR)
                .requesterId(creator.getId())
                .reasonCode(request.reasonCode().name())
                .memo(request.memo().trim())
                .statusAtRequest(GroupBuyStatus.IN_PROGRESS)
                .salesOrderCountAtRequest(sales == null ? null : sales.orderCount())
                .salesAmountAtRequest(sales == null ? null : sales.amount())
                .status(ChangeRequestStatus.PENDING)
                .requestedAt(now)
                .build();
        try {
            changeRequest = changeRequestRepository.saveAndFlush(changeRequest);
        } catch (DataIntegrityViolationException e) {
            // pending_group_buy_id UNIQUE — 브랜드 요청과 동시에 들어오면 하나는 DB에서 떨어진다.
            throw new BusinessException(ErrorCode.GROUP_BUY_REQUEST_ALREADY_PENDING);
        }

        historyRecorder.recordByCreator(groupBuy, GroupBuyEventType.SUSPENSION_REQUESTED,
                request.reasonCode().getLabel(), changeRequest.getId(), now);
        notifier.notifyAdmin(groupBuy, "SUSPENSION_REQUESTED");
        notifier.notifySeller(groupBuy, "SUSPENSION_REQUESTED");
        return detailAssembler.assemble(groupBuy, null);
    }

    // ── C5 · C6 계약 이행 확인 ────────────────────────────────────────────────

    /**
     * 이행 확인(31 설계 5-4) — 파트너와 <b>같은 서비스 메서드</b>를 CREATOR 측으로 부른다. 확인 대상은 브랜드의 의무다.
     * 미이행이면 같은 트랜잭션에서 3자 스레드를 연다 — 스레드 포트가 준비되기 전에는 503으로 막힌다.
     */
    public CreatorGroupBuyDetailResponse checkFulfillment(String creatorEmail, Long groupBuyId,
                                                          CreatorFulfillmentCheckRequest request) {
        Creator creator = reader.resolveCreator(creatorEmail);
        GroupBuy groupBuy = reader.requireMineForUpdate(creator.getId(), groupBuyId);
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        if (facts.fulfillmentCheck(FulfillmentSide.CREATOR).isPresent()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_FULFILLMENT_ALREADY_CHECKED);
        }
        if (!permissionPolicy.canCheckFulfillment(facts)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_ACTION_NOT_ALLOWED);
        }
        fulfillmentService.check(groupBuy, FulfillmentSide.CREATOR, request.result(), trimToNull(request.reason()),
                creator.getId(), LocalDateTime.now());
        return detailAssembler.assemble(groupBuy, null);
    }

    // ── 공통 ─────────────────────────────────────────────────────────────────

    /** 빈 제목은 {@code ''}로 둔다 — 컬럼이 NOT NULL이고, 필수 검증은 제출 시점의 일이다. */
    private static String normalizeTitle(String title) {
        return title == null ? "" : title.strip();
    }

    private static String normalizeContent(String content) {
        return content == null || content.isBlank() ? null : content;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 게시물 쓰기의 잠금 결과 — 공구 행과 게시물 행을 잠근 뒤 모은 사실 스냅샷. */
    private record Locked(Long creatorId, GroupBuy groupBuy, GroupBuyPost post, GroupBuyFacts facts) {
    }
}
