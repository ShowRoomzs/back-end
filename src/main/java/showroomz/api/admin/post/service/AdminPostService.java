package showroomz.api.admin.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.api.admin.post.dto.AdminPostDto;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostAppeal;
import showroomz.domain.post.entity.PostImage;
import showroomz.domain.post.entity.PostSuspension;
import showroomz.domain.post.repository.PostAppealRepository;
import showroomz.domain.post.repository.PostImageRepository;
import showroomz.domain.post.repository.PostReportRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.repository.PostSuspensionRepository;
import showroomz.domain.post.service.PostNotificationService;
import showroomz.domain.post.service.PostReportTransitions;
import showroomz.domain.post.type.PostAppealStatus;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostMonitoringStatus;
import showroomz.domain.post.type.PostNotificationEvent;
import showroomz.domain.post.type.PostPolicyClause;
import showroomz.domain.post.type.PostReportStatus;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.PostSuspensionReason;
import showroomz.domain.post.type.SuspensionResolution;
import showroomz.global.config.properties.PostProperties;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 운영자 게시물 조치 (§24-5 · §54-5).
 *
 * <p>운영자가 할 수 있는 것은 <b>노출 중지 · 신고 종결 · 조치 취소(재게시) · 이의 신청 심사</b>뿐이다(§54-0).
 * 게시물 수정 · 사진 단위 조치 · 즉시 강제 삭제는 없다 — 삭제는 기한 만료 · 반려 · 작성자 삭제로만 일어난다.
 *
 * <p>모든 조치는 게시물(또는 이의 신청) 행을 잠근 뒤 상태를 판정한다(54 설계 0-8). 운영자 둘이 같은 게시물을
 * 동시에 내리면 열린 조치가 두 행이 되고, 기한 만료 배치가 둘을 집는다.
 *
 * <p>작성자에게 영향을 주는 조치는 통지 이력을 남긴다 — §24-5의 "알리지 않고 사라지는 경우는 없다"가
 * 이 서비스의 불변 조건이다. 신고 종결은 게시물에 아무 일도 일으키지 않으므로 누구에게도 알리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostService {

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostSuspensionRepository postSuspensionRepository;
    private final PostAppealRepository postAppealRepository;
    private final PostReportRepository postReportRepository;
    private final PostNotificationService postNotificationService;
    private final PostProperties postProperties;
    private final PostAppealReviewDue appealReviewDue;
    private final AdminOperatorResolver adminOperatorResolver;
    private final PostReportTransitions reportTransitions;

    // ------------------------------------------------------------------ 코드 · 목록

    /** 어드민 드롭다운이 서버와 같은 코드를 쓰도록 목록을 내려준다 */
    public List<AdminPostDto.SuspensionReasonItem> getSuspensionReasons() {
        return Arrays.stream(PostSuspensionReason.values())
                .map(reason -> new AdminPostDto.SuspensionReasonItem(
                        reason, reason.getLabel(), reason.requiresDetail(), reason.operatorOnly()))
                .toList();
    }

    /** 근거 규정 조항 — 운영정책 문서 확정 전 가정값 */
    public List<AdminPostDto.PolicyClauseItem> getPolicyClauses() {
        return Arrays.stream(PostPolicyClause.values())
                .map(clause -> new AdminPostDto.PolicyClauseItem(clause, clause.getLabel(), clause.getSuggestedFor()))
                .toList();
    }

    public PageResponse<AdminPostDto.AppealItem> getAppeals(PostAppealStatus status, PagingRequest pagingRequest) {
        // 정렬 키가 submitted_at이라 PagingRequest의 기본 정렬(createdAt — 이의 신청에는 없는 속성)을 쓰지 않는다
        Pageable pageable = pagingRequest.toPageable(Sort.unsorted());
        Page<PostAppeal> page = status == null
                ? postAppealRepository.findAllByOrderBySubmittedAtDesc(pageable)
                // 심사 대기는 오래 기다린 순으로 본다 — 기한이 걸린 절차라 순서가 곧 형평이다
                : postAppealRepository.findByStatusOrderBySubmittedAtAsc(status, pageable);

        Map<Long, String> names = adminOperatorResolver.namesOf(
                page.getContent().stream().map(PostAppeal::getReviewedBy).filter(Objects::nonNull).toList());
        return new PageResponse<>(page.map(appeal -> toAppealItem(appeal, names)));
    }

    // ------------------------------------------------------------------ 조치

    /**
     * 노출 중지 (§24-5 · §54-5 C4) — 조치와 <b>동시에</b> 이의 신청 기간이 시작된다.
     *
     * <p>조치 기록 5항목(사유 · 근거 규정 · 조치 시각 · 처리자 · 기한)을 조치 행과 통지 이력에 동시에 굳힌다.
     * 근거 규정은 이의 · 반려 심사에서 판단 기준이 되므로 필수다.
     */
    @Transactional
    public AdminPostDto.AdminPostActionResponse suspend(Long operatorId, Long postId,
                                                        AdminPostDto.SuspendRequest request) {
        Post post = lockGeneralPost(postId);

        if (request.getPolicyClause() == null) {
            throw new BusinessException(ErrorCode.POST_POLICY_CLAUSE_REQUIRED);
        }
        if (request.getReasonCode().requiresDetail()
                && (request.getReasonDetail() == null || request.getReasonDetail().isBlank())) {
            throw new BusinessException(ErrorCode.POST_SUSPENSION_DETAIL_REQUIRED);
        }
        // 게시중인 것만 내릴 수 있다 — 작성중은 애초에 노출되지 않고, 삭제된 것은 이미 내려가 있다
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.POST_NOT_EDITABLE);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusDays(postProperties.getAppealDeadlineDays());
        String detail = blankToNull(request.getReasonDetail());

        PostSuspension suspension = postSuspensionRepository.save(new PostSuspension(
                post, request.getReasonCode(), detail, request.getPolicyClause(), operatorId, now, deadline));
        post.suspend();

        // 이 게시물에 걸려 있던 신고를 한꺼번에 닫는다. 신고 하나하나를 따로 처리하게 하면 같은
        // 게시물에 스무 건이 걸렸을 때 스무 번을 눌러야 하고, 그 사이 대기열이 조치가 끝난 건으로 찬다.
        int accepted = reportTransitions.closePending(postId, operatorId, now, true);

        // 통지 문구를 지금 굳힌다 — 게시물이 파기된 뒤에는 사유 · 근거를 다시 만들어 낼 수 없다(§24-6)
        postNotificationService.notify(post, PostNotificationEvent.SUSPENDED,
                PostNotificationService.payload(
                        "suspensionId", suspension.getId(),
                        "reasonCode", request.getReasonCode().name(),
                        "reasonLabel", request.getReasonCode().getLabel(),
                        "reasonDetail", detail,
                        "policyClauseCode", request.getPolicyClause().name(),
                        "policyRef", request.getPolicyClause().getLabel(),
                        "suspendedAt", now.toString(),
                        "suspendedBy", operatorId,
                        "appealDeadline", deadline.toString()));

        return AdminPostDto.AdminPostActionResponse.builder()
                .postId(post.getId())
                .status(post.getStatus())
                .monitoringStatus(PostMonitoringStatus.SUSPENDED)
                .appealDeadline(deadline)
                .closedReportCount(accepted)
                .build();
    }

    /**
     * 신고 종결 (§54-5 C3) — 확인했으나 조치가 필요 없다. 대기 신고 전부를 반려로 닫는다.
     *
     * <p>게시물 상태는 바뀌지 않는다. 「게시중으로 되돌린다」는 대기 신고가 0이 되는 것이고, 신고 이력은 남는다.
     * 이 경로가 없으면 경고 상태가 영구히 남는다.
     *
     * <p>누구에게도 알리지 않는다 — 작성자에게는 일어난 일이 없고, 신고자에게 결과를 돌려주면 그 값이
     * 게시물 상태를 캐는 창구가 된다(신고 1건 반려와 같은 규칙).
     */
    @Transactional
    public AdminPostDto.AdminPostActionResponse closeReports(Long operatorId, Long postId) {
        Post post = lockGeneralPost(postId);
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.POST_REPORT_NOTHING_TO_CLOSE);
        }

        int dismissed = reportTransitions.closePending(postId, operatorId, LocalDateTime.now(), false);
        if (dismissed == 0) {
            throw new BusinessException(ErrorCode.POST_REPORT_NOTHING_TO_CLOSE);
        }

        return AdminPostDto.AdminPostActionResponse.builder()
                .postId(post.getId())
                .status(post.getStatus())
                .monitoringStatus(PostMonitoringStatus.PUBLISHED)
                .closedReportCount(dismissed)
                .build();
    }

    /**
     * 조치 취소 · 재게시 (§54-4 C5) — 노출 중지 상태에서 운영자가 판단을 거둔다.
     *
     * <p>이의 신청이 들어온 뒤(심사 중)에는 「승인」이 같은 자리다 — 신청을 둔 채 조치만 거두면 심사가 붕 뜬다.
     * 좋아요 · 인사이트는 중지 중에도 깎지 않았으므로 상태만 되돌리면 복원된다. 조치로 닫혔던 신고는 그대로 둔다 —
     * 그 시점에 조치로 이어진 것은 사실이다.
     */
    @Transactional
    public AdminPostDto.AdminPostActionResponse cancelSuspension(Long operatorId, Long postId, String note) {
        Post post = lockGeneralPost(postId);
        // 심사 중은 UNDER_REVIEW라 여기서 걸린다. 이의 신청 행의 존재로 판정하지 않는다 — 승인 재게시 뒤 다시 중지된
        // 게시물에는 지난 신청 행이 남아 있어(게시물당 1회 UNIQUE) 이번 조치를 거둘 수 없게 된다.
        if (post.getStatus() != PostStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.POST_SUSPENSION_NOT_CANCELABLE);
        }
        PostSuspension suspension = postSuspensionRepository
                .findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_SUSPENSION_NOT_CANCELABLE));

        LocalDateTime now = LocalDateTime.now();
        suspension.cancelByAdmin(operatorId, now);
        post.republish();

        // 작성자가 "왜 다시 올라왔나"를 알 수 있어야 한다 — 메모는 선택이지만 통지에 굳힌다
        postNotificationService.notify(post, PostNotificationEvent.SUSPENSION_CANCELED,
                PostNotificationService.payload(
                        "suspensionId", suspension.getId(),
                        "canceledAt", now.toString(),
                        "canceledBy", operatorId,
                        "note", blankToNull(note)));

        return AdminPostDto.AdminPostActionResponse.builder()
                .postId(post.getId())
                .status(post.getStatus())
                .monitoringStatus(PostMonitoringStatus.of(post.getStatus(),
                        postReportRepository.countByPost_IdAndStatus(postId, PostReportStatus.PENDING)))
                .build();
    }

    /** 승인 → 재게시. 좋아요 · 인사이트는 중지 기간에도 보존됐으므로 상태만 되돌리면 복원된다 (§24-5) */
    @Transactional
    public AdminPostDto.AdminPostActionResponse approveAppeal(Long operatorId, Long appealId,
                                                              AdminPostDto.AppealReviewRequest request) {
        PostAppeal appeal = lockPendingAppeal(appealId);
        LocalDateTime now = LocalDateTime.now();
        String comment = blankToNull(request.getComment());

        appeal.approve(operatorId, comment, now);
        appeal.getSuspension().resolve(SuspensionResolution.REPUBLISHED, now);

        Post post = appeal.getPost();
        post.republish();

        postNotificationService.notify(post, PostNotificationEvent.APPEAL_APPROVED,
                PostNotificationService.payload(
                        "appealId", appeal.getId(),
                        "reviewedAt", now.toString(),
                        "reviewedBy", operatorId,
                        "comment", comment));

        return AdminPostDto.AdminPostActionResponse.builder()
                .postId(post.getId())
                .status(post.getStatus())
                .monitoringStatus(PostMonitoringStatus.PUBLISHED)
                .build();
    }

    /**
     * 반려 → 영구 삭제 (§24-5 · §54-5 C7).
     *
     * <p>반려 사유는 <b>필수</b>다 — 작성자에게 그대로 전달되고 알림 이력에 영구 보존된다. 신청은 1회라 이 판단이 최종이다.
     *
     * <p>반려 시각이 어드민 기준 삭제 확정 시각이다(54 설계 0-5). 유예(기본 7일)는 작성자가 사진 원본을 내려받을 수 있는
     * 기간이고, 서버는 보관 기간이 끝날 때까지 원문을 비공개로 들고 있다가 파기한다(§24-6).
     */
    @Transactional
    public AdminPostDto.AdminPostActionResponse rejectAppeal(Long operatorId, Long appealId,
                                                             AdminPostDto.AppealReviewRequest request) {
        String comment = blankToNull(request.getComment());
        if (comment == null) {
            throw new BusinessException(ErrorCode.POST_APPEAL_REJECT_REASON_REQUIRED);
        }
        PostAppeal appeal = lockPendingAppeal(appealId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime graceUntil = now.plusDays(postProperties.getDownloadGraceDays());

        appeal.reject(operatorId, comment, now, graceUntil);
        appeal.getSuspension().resolve(SuspensionResolution.DELETED_BY_REJECT, now);

        Post post = appeal.getPost();
        post.softDelete(PostDeleteReason.APPEAL_REJECTED, now, now.plusMonths(postProperties.getRetentionMonths()));

        postNotificationService.notify(post, PostNotificationEvent.APPEAL_REJECTED,
                PostNotificationService.payload(
                        "appealId", appeal.getId(),
                        "reviewedAt", now.toString(),
                        "reviewedBy", operatorId,
                        "comment", comment,
                        "graceUntil", graceUntil.toString(),
                        "retentionMonths", postProperties.getRetentionMonths()));

        return AdminPostDto.AdminPostActionResponse.builder()
                .postId(post.getId())
                .status(post.getStatus())
                .monitoringStatus(PostMonitoringStatus.CLOSED)
                .build();
    }

    /**
     * 사진 「신고 지목」 켜기 · 끄기 (§54-3 · C3 · C11) — 운영자 읽기 보조 표식이다. 알림 · 이력은 남기지 않는다.
     * 보관 중(영구 삭제)인 게시물에도 허용한다 — 사후 자료 요청 대응 때 표식이 쓸모 있다.
     */
    @Transactional
    public void flagImage(Long operatorId, Long postId, Long imageId, boolean flagged) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        requireGeneral(post);
        PostImage image = postImageRepository.findByIdAndPost_Id(imageId, postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_IMAGE_NOT_FOUND));
        if (flagged) {
            image.flagReported(operatorId, LocalDateTime.now());
        } else {
            image.unflagReported();
        }
    }

    // ------------------------------------------------------------------ 내부

    /**
     * 게시물 행을 잠그고 일반 게시물인지 본다.
     *
     * <p>공구 게시물을 여기서 내리면 이의 신청 기한 뒤 영구 삭제로 넘어간다 — 분쟁 근거인 원문이 사라진다.
     * 공구 게시물은 공구 관리의 숨김(되돌릴 수 있는 조치)으로 내린다(31 설계 0-6 ②). 이 판정이 입력 검증보다 먼저다.
     */
    private Post lockGeneralPost(Long postId) {
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        requireGeneral(post);
        return post;
    }

    private static void requireGeneral(Post post) {
        if (!post.isGeneral()) {
            throw new BusinessException(ErrorCode.POST_NOT_EDITABLE, "공구 게시물은 공구 관리에서 숨김 처리해 주세요.");
        }
    }

    private PostAppeal lockPendingAppeal(Long appealId) {
        PostAppeal appeal = postAppealRepository.findByIdForUpdate(appealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_APPEAL_NOT_FOUND));
        if (!appeal.isPending()) {
            throw new BusinessException(ErrorCode.POST_APPEAL_ALREADY_REVIEWED);
        }
        return appeal;
    }

    private AdminPostDto.AppealItem toAppealItem(PostAppeal appeal, Map<Long, String> operatorNames) {
        Creator creator = appeal.getPost().getCreator();
        return AdminPostDto.AppealItem.builder()
                .appealId(appeal.getId())
                .postId(appeal.getPost().getId())
                .showroomId(creator.getId())
                .showroomName(AdminPostFormats.showroomName(creator))
                .status(appeal.getStatus())
                .content(appeal.getContent())
                .reasonCode(appeal.getSuspension().getReasonCode())
                .submittedAt(appeal.getSubmittedAt())
                .reviewDueAt(appealReviewDue.of(appeal.getSubmittedAt()))
                .reviewedAt(appeal.getReviewedAt())
                .reviewedByName(appeal.getReviewedBy() == null ? null : operatorNames.get(appeal.getReviewedBy()))
                .reviewComment(appeal.getReviewComment())
                .graceUntil(appeal.getGraceUntil())
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
