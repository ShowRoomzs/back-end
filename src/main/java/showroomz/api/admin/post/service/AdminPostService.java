package showroomz.api.admin.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.post.dto.AdminPostDto;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostAppeal;
import showroomz.domain.post.entity.PostImage;
import showroomz.domain.post.entity.PostReport;
import showroomz.domain.post.entity.PostSuspension;
import showroomz.domain.post.repository.PostAppealRepository;
import showroomz.domain.post.repository.PostImageRepository;
import showroomz.domain.post.repository.PostReportRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.repository.PostSuspensionRepository;
import showroomz.domain.post.service.PostNotificationService;
import showroomz.domain.post.type.PostAppealStatus;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostNotificationEvent;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 운영자 게시물 조치 (§24-5 · §24-6).
 *
 * <p>세 갈래가 결과를 가른다 — 기간 내 신청 후 <b>승인</b>이면 재게시(좋아요·인사이트 유지),
 * <b>반려</b>면 영구 삭제, 기간 내 <b>미신청</b>이면 영구 삭제. 마지막 갈래는 배치가 처리한다.
 *
 * <p>모든 조치는 통지 이력을 남긴다. §24-5의 "알리지 않고 사라지는 경우는 없다"가 이 서비스의
 * 불변 조건이고, 발송 인프라가 없다는 사실이 그 요구를 미루는 이유가 되지는 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostService {

    private static final int CONTENT_PREVIEW_LENGTH = 40;

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostSuspensionRepository postSuspensionRepository;
    private final PostAppealRepository postAppealRepository;
    private final PostReportRepository postReportRepository;
    private final PostNotificationService postNotificationService;
    private final PostProperties postProperties;

    // ------------------------------------------------------------------ 조회

    public PageResponse<AdminPostDto.AdminPostListItem> getPosts(Long showroomId, PostStatus status,
                                                                 PagingRequest pagingRequest) {
        Pageable pageable = pagingRequest.toPageable();
        Page<Post> page = postRepository.findAdminPosts(showroomId, status, pageable);

        Map<Long, PostImage> thumbnails = representativeImages(page.getContent());

        Page<AdminPostDto.AdminPostListItem> mapped = page.map(post -> AdminPostDto.AdminPostListItem.builder()
                .postId(post.getId())
                .showroomId(post.getCreator().getId())
                .showroomName(showroomName(post.getCreator()))
                .status(post.getStatus())
                .thumbnailUrl(Optional.ofNullable(thumbnails.get(post.getId()))
                        .map(PostImage::getImageUrl).orElse(null))
                .contentPreview(preview(post.getContent()))
                .impressionCount(post.getImpressionCount())
                .likeCount(post.getLikeCount())
                .publishedAt(post.getPublishedAt())
                .deletedAt(post.getDeletedAt())
                .deleteReason(post.getDeleteReason())
                .purgeAt(post.getPurgeAt())
                .build());

        return new PageResponse<>(mapped);
    }

    /** 삭제·보관분도 그대로 보인다 — 플랫폼이 내린 판단의 정당성을 입증할 자료가 여기 남아 있어야 한다(§24-6) */
    public AdminPostDto.AdminPostDetailResponse getPost(Long postId) {
        Post post = postRepository.findByIdWithImages(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        List<AdminPostDto.SuspensionHistoryItem> suspensions =
                postSuspensionRepository.findByPost_IdOrderBySuspendedAtDesc(postId).stream()
                        .map(AdminPostService::toHistoryItem)
                        .toList();

        AdminPostDto.AppealItem appeal = postAppealRepository.findByPost_Id(postId)
                .map(AdminPostService::toAppealItem)
                .orElse(null);

        return AdminPostDto.AdminPostDetailResponse.builder()
                .postId(post.getId())
                .showroomId(post.getCreator().getId())
                .showroomName(showroomName(post.getCreator()))
                .status(post.getStatus())
                .content(post.getContent())
                .imageUrls(post.getImages().stream().map(PostImage::getImageUrl).toList())
                .impressionCount(post.getImpressionCount())
                .likeCount(post.getLikeCount())
                .publishedAt(post.getPublishedAt())
                .deletedAt(post.getDeletedAt())
                .purgeAt(post.getPurgeAt())
                .suspensions(suspensions)
                .appeal(appeal)
                .build();
    }

    public PageResponse<AdminPostDto.AppealItem> getAppeals(PostAppealStatus status, PagingRequest pagingRequest) {
        Pageable pageable = pagingRequest.toPageable();
        Page<PostAppeal> page = status == null
                ? postAppealRepository.findAllByOrderBySubmittedAtDesc(pageable)
                // 심사 대기는 오래 기다린 순으로 본다 — 기한이 걸린 절차라 순서가 곧 형평이다
                : postAppealRepository.findByStatusOrderBySubmittedAtAsc(status, pageable);

        return new PageResponse<>(page.map(AdminPostService::toAppealItem));
    }

    /** 어드민 드롭다운이 서버와 같은 코드를 쓰도록 목록을 내려준다 */
    public List<AdminPostDto.SuspensionReasonItem> getSuspensionReasons() {
        return Arrays.stream(PostSuspensionReason.values())
                .map(reason -> new AdminPostDto.SuspensionReasonItem(
                        reason, reason.getLabel(), reason.requiresDetail()))
                .toList();
    }

    // ------------------------------------------------------------------ 조치

    /**
     * 노출 중지 (§24-5) — 조치와 <b>동시에</b> 이의 신청 기간이 시작된다.
     *
     * <p>기한을 설정값에서 읽는 이유 — 기획서가 7일을 "가정값"이라고 명시했고 법률 자문을 기다리는
     * 중이다. 하드코딩하면 확정될 때마다 코드를 고쳐야 한다.
     */
    @Transactional
    public AdminPostDto.AdminPostActionResponse suspend(Long operatorId, Long postId,
                                                        AdminPostDto.SuspendRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

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

        PostSuspension suspension = postSuspensionRepository.save(new PostSuspension(
                post, request.getReasonCode(), request.getReasonDetail(), request.getPolicyRef(),
                operatorId, now, deadline));
        post.suspend();

        // 이 게시물에 걸려 있던 신고를 한꺼번에 닫는다. 신고 하나하나를 따로 처리하게 하면 같은
        // 게시물에 스무 건이 걸렸을 때 스무 번을 눌러야 하고, 그 사이 대기열이 조치가 끝난 건으로 찬다.
        acceptPendingReports(postId, operatorId, now);

        // 통지 문구를 지금 굳힌다 — 게시물이 파기된 뒤에는 사유·근거를 다시 만들어 낼 수 없다(§24-6)
        postNotificationService.notify(post, PostNotificationEvent.SUSPENDED,
                PostNotificationService.payload(
                        "suspensionId", suspension.getId(),
                        "reasonCode", request.getReasonCode().name(),
                        "reasonLabel", request.getReasonCode().getLabel(),
                        "reasonDetail", request.getReasonDetail(),
                        "policyRef", request.getPolicyRef(),
                        "suspendedAt", now.toString(),
                        "suspendedBy", operatorId,
                        "appealDeadline", deadline.toString()));

        return AdminPostDto.AdminPostActionResponse.builder()
                .postId(post.getId())
                .status(post.getStatus())
                .appealDeadline(deadline)
                .build();
    }

    /** 승인 → 재게시. 좋아요·인사이트는 중지 기간에도 보존됐으므로 상태만 되돌리면 복원된다 (§24-5) */
    @Transactional
    public AdminPostDto.AdminPostActionResponse approveAppeal(Long operatorId, Long appealId,
                                                              AdminPostDto.AppealReviewRequest request) {
        PostAppeal appeal = getPendingAppeal(appealId);
        LocalDateTime now = LocalDateTime.now();

        appeal.approve(operatorId, request.getComment(), now);
        appeal.getSuspension().resolve(SuspensionResolution.REPUBLISHED, now);

        Post post = appeal.getPost();
        post.republish();

        postNotificationService.notify(post, PostNotificationEvent.APPEAL_APPROVED,
                PostNotificationService.payload(
                        "appealId", appeal.getId(),
                        "reviewedAt", now.toString(),
                        "reviewedBy", operatorId,
                        "comment", request.getComment()));

        return AdminPostDto.AdminPostActionResponse.builder()
                .postId(post.getId())
                .status(post.getStatus())
                .build();
    }

    /**
     * 반려 → 영구 삭제 (§24-5).
     *
     * <p>"고쳐서 다시"라는 경로가 없다 — 반려가 곧 삭제이기 때문에 소명·재검토 요청 같은 중간 단계를
     * 두지 않았다. 대신 통지 직후 곧바로 지우지 않고 유예를 두어, 그동안 본인이 사진 원본을
     * 내려받을 수 있게 한다(§24-6).
     */
    @Transactional
    public AdminPostDto.AdminPostActionResponse rejectAppeal(Long operatorId, Long appealId,
                                                             AdminPostDto.AppealReviewRequest request) {
        PostAppeal appeal = getPendingAppeal(appealId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime graceUntil = now.plusDays(postProperties.getDownloadGraceDays());

        appeal.reject(operatorId, request.getComment(), now, graceUntil);
        appeal.getSuspension().resolve(SuspensionResolution.DELETED_BY_REJECT, now);

        Post post = appeal.getPost();
        post.softDelete(PostDeleteReason.APPEAL_REJECTED, now, now.plusMonths(postProperties.getRetentionMonths()));

        postNotificationService.notify(post, PostNotificationEvent.APPEAL_REJECTED,
                PostNotificationService.payload(
                        "appealId", appeal.getId(),
                        "reviewedAt", now.toString(),
                        "reviewedBy", operatorId,
                        "comment", request.getComment(),
                        "graceUntil", graceUntil.toString(),
                        "retentionMonths", postProperties.getRetentionMonths()));

        return AdminPostDto.AdminPostActionResponse.builder()
                .postId(post.getId())
                .status(post.getStatus())
                .build();
    }

    // ------------------------------------------------------------------ 내부

    /** 조치가 곧 신고의 답이다 — 신고자에게 따로 통지하지는 않는다(대상에게 신고자가 드러나면 안 된다) */
    private void acceptPendingReports(Long postId, Long operatorId, LocalDateTime now) {
        for (PostReport report : postReportRepository.findByPost_IdAndStatus(postId, PostReportStatus.PENDING)) {
            report.accept(operatorId, now);
        }
    }

    private PostAppeal getPendingAppeal(Long appealId) {
        PostAppeal appeal = postAppealRepository.findById(appealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_APPEAL_NOT_FOUND));
        if (!appeal.isPending()) {
            throw new BusinessException(ErrorCode.POST_APPEAL_ALREADY_REVIEWED);
        }
        return appeal;
    }

    private Map<Long, PostImage> representativeImages(List<Post> posts) {
        if (posts.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, PostImage> map = new HashMap<>();
        for (PostImage image : postImageRepository.findRepresentativesByPostIds(postIds)) {
            map.put(image.getPost().getId(), image);
        }
        return map;
    }

    private static AdminPostDto.SuspensionHistoryItem toHistoryItem(PostSuspension suspension) {
        return AdminPostDto.SuspensionHistoryItem.builder()
                .suspensionId(suspension.getId())
                .reasonCode(suspension.getReasonCode())
                .reasonLabel(suspension.getReasonCode().getLabel())
                .reasonDetail(suspension.getReasonDetail())
                .policyRef(suspension.getPolicyRef())
                .suspendedBy(suspension.getSuspendedBy())
                .suspendedAt(suspension.getSuspendedAt())
                .appealDeadline(suspension.getAppealDeadline())
                .resolution(suspension.getResolution() == null ? null : suspension.getResolution().name())
                .resolvedAt(suspension.getResolvedAt())
                .build();
    }

    private static AdminPostDto.AppealItem toAppealItem(PostAppeal appeal) {
        Creator creator = appeal.getPost().getCreator();
        return AdminPostDto.AppealItem.builder()
                .appealId(appeal.getId())
                .postId(appeal.getPost().getId())
                .showroomId(creator.getId())
                .showroomName(showroomName(creator))
                .status(appeal.getStatus())
                .content(appeal.getContent())
                .reasonCode(appeal.getSuspension().getReasonCode())
                .submittedAt(appeal.getSubmittedAt())
                .reviewedAt(appeal.getReviewedAt())
                .reviewComment(appeal.getReviewComment())
                .graceUntil(appeal.getGraceUntil())
                .build();
    }

    private static String showroomName(Creator creator) {
        return creator.getShowroomName() != null ? creator.getShowroomName() : creator.getUser().getNickname();
    }

    private static String preview(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String flattened = content.replaceAll("\\s+", " ").trim();
        return flattened.length() <= CONTENT_PREVIEW_LENGTH
                ? flattened
                : flattened.substring(0, CONTENT_PREVIEW_LENGTH) + "…";
    }
}
