package showroomz.api.creator.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.creator.post.DTO.PostDto;
import showroomz.api.creator.post.type.PostSaveAction;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostAppeal;
import showroomz.domain.post.entity.PostImage;
import showroomz.domain.post.entity.PostSuspension;
import showroomz.domain.post.policy.PostPolicies;
import showroomz.domain.post.repository.PostAppealRepository;
import showroomz.domain.post.repository.PostImageRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.repository.PostSuspensionRepository;
import showroomz.domain.post.service.PostNotificationService;
import showroomz.domain.post.type.PostAppealStatus;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostNotificationEvent;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.SuspensionResolution;
import showroomz.global.config.properties.PostProperties;
import showroomz.global.dto.PaginationInfo;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 쇼룸 스튜디오 「게시물」 (§24) — 인플루언서가 자기 콘텐츠를 만들고 관리하는 화면의 서버 쪽.
 *
 * <p>이 서비스가 지키는 규칙 세 가지.
 *
 * <ol>
 *   <li><b>버튼 활성 조건을 서버도 검증한다.</b> FE는 "에러 문구 없이 비활성만"으로 표현하지만(§24-3),
 *       서버까지 막지 않으면 API 직접 호출로 빈 게시물이 생긴다. FE가 그 상태에 도달할 일이 없으므로
 *       서버가 거절해도 문구 정책과 충돌하지 않는다.</li>
 *   <li><b>삭제는 상태 전이다.</b> §24-6의 "영구 삭제"는 인플루언서 기준의 삭제이고, 서버는
 *       보관 기간이 끝날 때까지 비공개로 들고 있는다. 물리 삭제는 파기 배치만 한다.</li>
 *   <li><b>자율 숨김이 없다.</b> 인플루언서가 스스로 내리는 방법은 삭제뿐이므로(§24-1)
 *       노출 여부를 켜고 끄는 API를 만들지 않는다.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowroomPostService {

    /** 목록 카드의 본문 미리보기 길이 — 제목이 없으므로 이 조각이 게시물을 알아보는 단서다 */
    private static final int CONTENT_PREVIEW_LENGTH = 40;

    /** 상태 탭 4종 (§24-1) — 전체 탭은 status = null로 표현한다 */
    private static final List<PostStatus> TAB_STATUSES =
            List.of(PostStatus.PUBLISHED, PostStatus.SUSPENDED, PostStatus.DRAFT);

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostSuspensionRepository postSuspensionRepository;
    private final PostAppealRepository postAppealRepository;
    private final CreatorRepository creatorRepository;
    private final PostPolicies postPolicies;
    private final PostNotificationService postNotificationService;
    private final PostProperties postProperties;

    // ------------------------------------------------------------------ 작성·수정

    @Transactional
    public PostDto.PostIdResponse createPost(Long userId, PostDto.SavePostRequest request) {
        Creator creator = getMyCreator(userId);
        List<PostDto.PostImageRequest> images = safeImages(request.getImages());

        validateSaveable(request.getAction(), images, request.getContent());

        LocalDateTime now = LocalDateTime.now();
        BigDecimal aspectRatio = resolveAspectRatio(images);
        Post post = request.getAction() == PostSaveAction.PUBLISH
                ? Post.published(creator, request.getContent(), aspectRatio, now)
                : Post.draft(creator, request.getContent(), aspectRatio);

        post.replaceImages(toImageEntities(images));
        if (request.getAction() == PostSaveAction.PUBLISH) {
            postPolicies.of(post).validateForPublish(post);
        }

        Post saved = postRepository.save(post);

        if (request.getAction() == PostSaveAction.PUBLISH) {
            notifyFollowers(saved);
        }
        return PostDto.PostIdResponse.of(saved);
    }

    /**
     * 수정 — 사진은 <b>전체 교체</b>다.
     *
     * <p>추가·삭제·순서 변경을 각각의 엔드포인트로 나누지 않는다. 화면이 그리는 것은 언제나
     * "지금 이 순서의 사진들"이고, {@code (post_id, sort_order)} 유니크 때문에 부분 갱신은
     * 중간 상태에서 충돌한다.
     */
    @Transactional
    public PostDto.PostIdResponse updatePost(Long userId, Long postId, PostDto.SavePostRequest request) {
        Creator creator = getMyCreator(userId);
        Post post = getMyPost(creator, postId);
        List<PostDto.PostImageRequest> images = safeImages(request.getImages());

        // 중지·심사 중에는 수정할 수 없다 — 심사 대상이 도중에 바뀌면 안 된다(§24-5)
        postPolicies.of(post).validateEditable(post);
        validateSaveable(request.getAction(), images, request.getContent());

        post.updateContent(request.getContent(), resolveAspectRatio(images));
        post.replaceImages(toImageEntities(images));

        boolean firstPublish = post.getStatus() == PostStatus.DRAFT && request.getAction() == PostSaveAction.PUBLISH;
        if (request.getAction() == PostSaveAction.PUBLISH) {
            postPolicies.of(post).validateForPublish(post);
            post.publish(LocalDateTime.now());
        }

        // 게시 후 수정은 팔로워 알림을 재발송하지 않는다(§24-3) — 처음 나갈 때만 통지한다
        if (firstPublish) {
            notifyFollowers(post);
        }
        return PostDto.PostIdResponse.of(post);
    }

    /** 작성중 → 게시중. 목록 카드의 「게시하기」가 부르는 자리다 */
    @Transactional
    public PostDto.PostIdResponse publish(Long userId, Long postId) {
        Creator creator = getMyCreator(userId);
        Post post = getMyPost(creator, postId);

        if (post.getStatus() != PostStatus.DRAFT) {
            throw new BusinessException(post.getStatus() == PostStatus.PUBLISHED
                    ? ErrorCode.POST_ALREADY_PUBLISHED
                    : ErrorCode.POST_NOT_EDITABLE);
        }

        postPolicies.of(post).validateForPublish(post);
        post.publish(LocalDateTime.now());
        notifyFollowers(post);

        return PostDto.PostIdResponse.of(post);
    }

    /**
     * 삭제 — 되돌릴 수 없고 좋아요 수도 함께 사라진다(§24-3 모달 고지).
     *
     * <p>중지 기간 중의 본인 삭제는 <b>허용</b>한다. 다투고 싶지 않은 인플루언서에게 출구가 필요하고,
     * 기한을 기다려 자동 삭제되는 것을 보고만 있게 하면 자기 콘텐츠에 대한 통제권을 빼앗는 셈이다.
     * 다만 심사 중에는 막는다 — 신청 후 도중에 지우면 처리 결과가 붕 뜬다(§24-5).
     */
    @Transactional
    public void deletePost(Long userId, Long postId) {
        Creator creator = getMyCreator(userId);
        Post post = getMyPost(creator, postId);

        if (!post.getStatus().isDeletable()) {
            throw new BusinessException(ErrorCode.POST_NOT_DELETABLE);
        }

        LocalDateTime now = LocalDateTime.now();
        // 진행 중인 조치가 있었다면 함께 닫는다 — 열린 채로 두면 기한 만료 배치가 이미 지운 것을 또 집는다
        postSuspensionRepository.findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(postId)
                .ifPresent(suspension -> suspension.resolve(SuspensionResolution.DELETED_BY_SELF, now));

        post.softDelete(PostDeleteReason.SELF, now, now.plusMonths(postProperties.getRetentionMonths()));

        postNotificationService.notify(post, PostNotificationEvent.DELETED_BY_SELF,
                PostNotificationService.payload(
                        "deletedAt", now.toString(),
                        "retentionMonths", postProperties.getRetentionMonths()));
    }

    // ------------------------------------------------------------------ 조회

    /**
     * 목록 — 상태 탭 건수를 <b>같은 트랜잭션에서</b> 함께 내려준다 (§24-1).
     *
     * <p>탭 4개를 각각 호출하면 요청이 4배가 되고, 탭을 옮길 때마다 숫자가 흔들린다.
     */
    public PostDto.PostPageResponse getPostList(Long userId, PostStatus status, PagingRequest pagingRequest) {
        Creator creator = getMyCreator(userId);
        Pageable pageable = pagingRequest.toPageable();

        Page<Post> page = postRepository.findStudioPosts(creator.getId(), status, pageable);
        List<Long> postIds = page.getContent().stream().map(Post::getId).toList();

        Map<Long, PostImage> thumbnails = representativeImages(postIds);
        Map<Long, Long> imageCounts = imageCounts(postIds);
        Map<Long, LocalDateTime> appealDeadlines = openAppealDeadlines(page.getContent());

        List<PostDto.PostListItem> content = page.getContent().stream()
                .map(post -> PostDto.PostListItem.builder()
                        .postId(post.getId())
                        .status(post.getStatus())
                        .thumbnailUrl(Optional.ofNullable(thumbnails.get(post.getId()))
                                .map(PostImage::getImageUrl).orElse(null))
                        .imageCount(imageCounts.getOrDefault(post.getId(), 0L).intValue())
                        .contentPreview(preview(post.getContent()))
                        .impressionCount(post.getImpressionCount())
                        .likeCount(post.getLikeCount())
                        .publishedAt(post.getPublishedAt())
                        .createdAt(post.getCreatedAt())
                        .appealDeadline(appealDeadlines.get(post.getId()))
                        .build())
                .toList();

        return PostDto.PostPageResponse.builder()
                .content(content)
                .pageInfo(new PaginationInfo(page))
                .statusCounts(statusCounts(creator.getId()))
                .build();
    }

    public PostDto.PostDetailResponse getPost(Long userId, Long postId) {
        Creator creator = getMyCreator(userId);
        Post post = postRepository.findByIdWithImages(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        requireOwnedAndAlive(post, creator);

        PostSuspension suspension = postSuspensionRepository
                .findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(postId)
                .orElse(null);
        PostAppeal appeal = postAppealRepository.findByPost_Id(postId).orElse(null);

        return PostDto.PostDetailResponse.builder()
                .postId(post.getId())
                .status(post.getStatus())
                .content(post.getContent())
                .aspectRatio(post.getAspectRatio())
                .images(toImageResponses(post.getImages()))
                .impressionCount(post.getImpressionCount())
                .likeCount(post.getLikeCount())
                .publishedAt(post.getPublishedAt())
                .createdAt(post.getCreatedAt())
                .modifiedAt(post.getModifiedAt())
                .editable(post.getStatus().isEditable())
                .deletable(post.getStatus().isDeletable())
                .suspension(toSuspensionResponse(suspension, appeal))
                .appeal(toAppealResponse(appeal))
                .build();
    }

    // ------------------------------------------------------------------ 이의 신청 · 원본

    /**
     * 이의 신청 (§24-5) — 게시물당 1회.
     *
     * <p>중복은 DB 유니크가 최종적으로 막는다. 여기서 먼저 검사하는 것은 사용자에게
     * 도메인 메시지를 돌려주기 위해서지, 동시 요청을 막기 위해서가 아니다.
     */
    @Transactional
    public PostDto.AppealResponse submitAppeal(Long userId, Long postId, PostDto.AppealRequest request) {
        Creator creator = getMyCreator(userId);
        Post post = getMyPost(creator, postId);

        if (post.getStatus() != PostStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.POST_NOT_SUSPENDED);
        }
        if (postAppealRepository.existsByPost_Id(postId)) {
            throw new BusinessException(ErrorCode.POST_APPEAL_ALREADY_SUBMITTED);
        }

        LocalDateTime now = LocalDateTime.now();
        PostSuspension suspension = postSuspensionRepository
                .findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_SUSPENDED));
        if (!suspension.isAppealable(now)) {
            throw new BusinessException(ErrorCode.POST_APPEAL_DEADLINE_PASSED);
        }

        PostAppeal appeal = postAppealRepository.save(
                new PostAppeal(suspension, post, request.getContent(), now));
        post.startReview();

        postNotificationService.notify(post, PostNotificationEvent.APPEAL_RECEIVED,
                PostNotificationService.payload(
                        "appealId", appeal.getId(),
                        "submittedAt", now.toString(),
                        "expectedReviewBusinessDays", postProperties.getAppealReviewBusinessDays()));

        return toAppealResponse(appeal);
    }

    /**
     * 원본 내려받기 (§24-6) — 반려 통지 후 유예 기간 동안 <b>본인만</b> 받을 수 있다.
     *
     * <p>크롭본이 아니라 원본을 주는 이유 — 인플루언서가 자기 콘텐츠의 원본을 잃는 상황이
     * 분쟁으로 이어진다. 그래서 파기 시점까지 {@code original_url}을 함께 보관한다.
     */
    public PostDto.OriginalImagesResponse getOriginalImages(Long userId, Long postId) {
        Creator creator = getMyCreator(userId);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.isOwnedBy(creator.getId())) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        LocalDateTime now = LocalDateTime.now();
        PostAppeal appeal = postAppealRepository.findByPost_Id(postId).orElse(null);
        boolean rejectedWithinGrace = appeal != null
                && appeal.getStatus() == PostAppealStatus.REJECTED
                && appeal.isWithinGracePeriod(now);

        // 살아 있는 게시물은 상세 화면에서 원본을 그대로 볼 수 있으므로, 이 경로는 내려간 게시물을 위한 것이다.
        if (!rejectedWithinGrace && post.isDeleted()) {
            throw new BusinessException(ErrorCode.POST_ORIGINAL_DOWNLOAD_UNAVAILABLE);
        }

        List<PostImage> images = postImageRepository.findByPost_IdOrderBySortOrderAsc(postId);
        return PostDto.OriginalImagesResponse.builder()
                .graceUntil(appeal == null ? null : appeal.getGraceUntil())
                .images(toImageResponses(images))
                .build();
    }

    // ------------------------------------------------------------------ 내부

    /**
     * 버튼 활성 조건 (§24-3).
     *
     * <ul>
     *   <li>임시저장 — 사진 1장 <b>또는</b> 본문 1자. 무엇이든 남길 게 있어야 저장이 성립한다</li>
     *   <li>게시하기 — 사진 최소 1장 (이 검증은 {@code PostPolicy}가 맡는다)</li>
     * </ul>
     */
    private void validateSaveable(PostSaveAction action, List<PostDto.PostImageRequest> images, String content) {
        if (images.size() > Post.MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.POST_IMAGE_LIMIT_EXCEEDED);
        }
        if (content != null && content.length() > Post.MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.POST_CONTENT_TOO_LONG);
        }
        if (action == PostSaveAction.DRAFT && images.isEmpty() && (content == null || content.isBlank())) {
            throw new BusinessException(ErrorCode.POST_EMPTY);
        }
    }

    /**
     * 게시물 비율 — <b>첫 번째 사진</b>의 가로/세로가 나머지 전부에 적용된다 (§24-2 게시물 단위 통일).
     *
     * <p>크롭은 FE가 하고 서버는 결과가 허용 범위(1.91:1 ~ 4:5) 안인지 확인만 한다.
     * 서버에 이미지 처리 파이프라인을 새로 들이지 않기 위한 경계다.
     */
    private BigDecimal resolveAspectRatio(List<PostDto.PostImageRequest> images) {
        if (images.isEmpty()) {
            return null;
        }
        PostDto.PostImageRequest first = images.get(0);
        BigDecimal ratio = BigDecimal.valueOf(first.getWidth())
                .divide(BigDecimal.valueOf(first.getHeight()), 4, RoundingMode.HALF_UP);

        if (ratio.compareTo(Post.MIN_ASPECT_RATIO) < 0 || ratio.compareTo(Post.MAX_ASPECT_RATIO) > 0) {
            throw new BusinessException(ErrorCode.POST_ASPECT_RATIO_OUT_OF_RANGE);
        }
        return ratio;
    }

    private List<PostImage> toImageEntities(List<PostDto.PostImageRequest> images) {
        return images.stream()
                .map(image -> new PostImage(
                        image.getImageUrl(), image.getOriginalUrl(),
                        image.getWidth(), image.getHeight(), image.getFileSize()))
                .collect(Collectors.toList());
    }

    private static List<PostDto.PostImageResponse> toImageResponses(List<PostImage> images) {
        return images.stream()
                .map(image -> PostDto.PostImageResponse.builder()
                        .sortOrder(image.getSortOrder())
                        .imageUrl(image.getImageUrl())
                        .originalUrl(image.getOriginalUrl())
                        .width(image.getWidth())
                        .height(image.getHeight())
                        .build())
                .toList();
    }

    private PostDto.SuspensionResponse toSuspensionResponse(PostSuspension suspension, PostAppeal appeal) {
        if (suspension == null) {
            return null;
        }
        boolean appealable = appeal == null && suspension.isAppealable(LocalDateTime.now());
        return PostDto.SuspensionResponse.builder()
                .suspensionId(suspension.getId())
                .reasonCode(suspension.getReasonCode())
                .reasonLabel(suspension.getReasonCode().getLabel())
                .reasonDetail(suspension.getReasonDetail())
                .policyRef(suspension.getPolicyRef())
                .suspendedAt(suspension.getSuspendedAt())
                .suspendedBy(suspension.getSuspendedBy())
                .appealDeadline(suspension.getAppealDeadline())
                .appealable(appealable)
                .build();
    }

    private PostDto.AppealResponse toAppealResponse(PostAppeal appeal) {
        if (appeal == null) {
            return null;
        }
        return PostDto.AppealResponse.builder()
                .appealId(appeal.getId())
                .status(appeal.getStatus())
                .content(appeal.getContent())
                .submittedAt(appeal.getSubmittedAt())
                .reviewedAt(appeal.getReviewedAt())
                .reviewComment(appeal.getReviewComment())
                .graceUntil(appeal.getGraceUntil())
                .expectedReviewBusinessDays(postProperties.getAppealReviewBusinessDays())
                .build();
    }

    private List<PostDto.StatusCount> statusCounts(Long creatorId) {
        Map<PostStatus, Long> counts = new EnumMap<>(PostStatus.class);
        for (Object[] row : postRepository.countByCreatorGroupedByStatus(creatorId)) {
            counts.put((PostStatus) row[0], ((Number) row[1]).longValue());
        }
        // 심사 중은 화면에서 여전히 "노출 중지" 탭에 머문다 — 사실이 바뀐 게 아니다(§24-5)
        long suspended = counts.getOrDefault(PostStatus.SUSPENDED, 0L)
                + counts.getOrDefault(PostStatus.UNDER_REVIEW, 0L);

        List<PostDto.StatusCount> result = new ArrayList<>();
        result.add(new PostDto.StatusCount(null, "전체", counts.values().stream().mapToLong(Long::longValue).sum()));
        for (PostStatus status : TAB_STATUSES) {
            long count = status == PostStatus.SUSPENDED ? suspended : counts.getOrDefault(status, 0L);
            result.add(new PostDto.StatusCount(status, label(status), count));
        }
        return result;
    }

    private static String label(PostStatus status) {
        return switch (status) {
            case DRAFT -> "작성중";
            case PUBLISHED -> "게시중";
            case SUSPENDED, UNDER_REVIEW -> "노출 중지";
            case DELETED -> "삭제됨";
        };
    }

    private Map<Long, PostImage> representativeImages(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, PostImage> map = new HashMap<>();
        for (PostImage image : postImageRepository.findRepresentativesByPostIds(postIds)) {
            map.put(image.getPost().getId(), image);
        }
        return map;
    }

    private Map<Long, Long> imageCounts(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : postImageRepository.countByPostIds(postIds)) {
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }

    /** 목록 카드에 남은 기한을 함께 보여주기 위한 값 — 중지·심사 중인 게시물만 조회한다 */
    private Map<Long, LocalDateTime> openAppealDeadlines(List<Post> posts) {
        Map<Long, LocalDateTime> deadlines = new HashMap<>();
        for (Post post : posts) {
            if (post.getStatus() != PostStatus.SUSPENDED && post.getStatus() != PostStatus.UNDER_REVIEW) {
                continue;
            }
            postSuspensionRepository.findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(post.getId())
                    .ifPresent(suspension -> deadlines.put(post.getId(), suspension.getAppealDeadline()));
        }
        return deadlines;
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

    private static List<PostDto.PostImageRequest> safeImages(List<PostDto.PostImageRequest> images) {
        return images == null ? List.of() : images.stream().filter(Objects::nonNull).toList();
    }

    /**
     * 팔로워 신규 게시물 알림 (§24-8 ⓗ) — 발송 채널·시점이 확정되지 않았고 발송 인프라도 없다.
     * 이력만 남기고 실제 발송은 어댑터가 붙을 때 살아난다.
     */
    private void notifyFollowers(Post post) {
        postNotificationService.notify(post, PostNotificationEvent.PUBLISHED_TO_FOLLOWERS,
                PostNotificationService.payload("publishedAt", String.valueOf(post.getPublishedAt())));
    }

    private Creator getMyCreator(Long userId) {
        return creatorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }

    private Post getMyPost(Creator creator, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        requireOwnedAndAlive(post, creator);
        return post;
    }

    /** 삭제된 게시물은 본인에게도 없는 것으로 보인다 — 별도 화면을 두지 않는다(§24-6) */
    private void requireOwnedAndAlive(Post post, Creator creator) {
        if (!post.isOwnedBy(creator.getId())) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }
        if (post.isDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }
}
