package showroomz.api.admin.post.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.admin.post.dto.AdminPostDto;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostAppeal;
import showroomz.domain.post.entity.PostSuspension;
import showroomz.domain.post.repository.PostAppealRepository;
import showroomz.domain.post.repository.PostImageRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.repository.PostSuspensionRepository;
import showroomz.domain.post.service.PostNotificationService;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostNotificationEvent;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.PostSuspensionReason;
import showroomz.domain.post.type.SuspensionResolution;
import showroomz.global.config.properties.PostProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * §24-5 운영자 조치 — 세 갈래 중 사람이 조작하는 두 갈래(승인·반려)를 검증한다.
 *
 * <p>세 번째 갈래(기한 내 미신청)는 배치의 몫이라 {@code PostLifecycleServiceTest}가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminPostServiceTest {

    private static final long OPERATOR_ID = 9L;
    private static final long POST_ID = 301L;
    private static final long APPEAL_ID = 2L;

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostImageRepository postImageRepository;
    @Mock
    private PostSuspensionRepository postSuspensionRepository;
    @Mock
    private PostAppealRepository postAppealRepository;
    @Mock
    private PostNotificationService postNotificationService;

    private final PostProperties postProperties = new PostProperties();

    private AdminPostService adminPostService;
    private Creator creator;

    @BeforeEach
    void setUp() {
        creator = Creator.builder().id(5L).showroomName("뷰티 소연").build();
        adminPostService = new AdminPostService(postRepository, postImageRepository, postSuspensionRepository,
                postAppealRepository, postNotificationService, postProperties);

        given(postSuspensionRepository.save(any(PostSuspension.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("노출 중지는 조치와 동시에 이의 신청 기한을 연다")
    void suspendOpensAppealWindow() {
        Post post = publishedPost();
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

        AdminPostDto.AdminPostActionResponse response = adminPostService.suspend(
                OPERATOR_ID, POST_ID, suspendRequest(PostSuspensionReason.AD_DISCLOSURE, null));

        assertThat(post.getStatus()).isEqualTo(PostStatus.SUSPENDED);
        assertThat(response.getAppealDeadline())
                .isAfter(LocalDateTime.now().plusDays(postProperties.getAppealDeadlineDays() - 1L));
    }

    @Test
    @DisplayName("중지 통지 이력을 남긴다 — 알리지 않고 사라지는 경우는 없다")
    void suspendLeavesNotificationTrail() {
        Post post = publishedPost();
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

        adminPostService.suspend(OPERATOR_ID, POST_ID, suspendRequest(PostSuspensionReason.COPYRIGHT, null));

        then(postNotificationService).should()
                .notify(eq(post), eq(PostNotificationEvent.SUSPENDED), any());
    }

    @Test
    @DisplayName("기타 사유는 상세 설명 없이 중지할 수 없다 — 사유 고지가 성립하지 않는다")
    void rejectsOtherReasonWithoutDetail() {
        Post post = publishedPost();
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> adminPostService.suspend(
                OPERATOR_ID, POST_ID, suspendRequest(PostSuspensionReason.OTHER, "  ")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_SUSPENSION_DETAIL_REQUIRED);
    }

    @Test
    @DisplayName("게시중이 아닌 게시물은 내릴 것이 없다")
    void rejectsSuspendOnNonPublished() {
        Post post = publishedPost();
        post.suspend();
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> adminPostService.suspend(
                OPERATOR_ID, POST_ID, suspendRequest(PostSuspensionReason.MEDICAL_CLAIM, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_EDITABLE);
    }

    @Test
    @DisplayName("승인하면 재게시되고 좋아요·게시 시각이 그대로 남는다 — 별도 복원 로직이 필요 없다")
    void approveRepublishesWithCountersIntact() {
        Post post = publishedPost();
        LocalDateTime originalPublishedAt = post.getPublishedAt();
        post.increaseLikeCount();
        post.suspend();
        post.startReview();
        PostAppeal appeal = pendingAppeal(post);
        given(postAppealRepository.findById(APPEAL_ID)).willReturn(Optional.of(appeal));

        adminPostService.approveAppeal(OPERATOR_ID, APPEAL_ID, review("재게시합니다"));

        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.getLikeCount()).isEqualTo(1L);
        assertThat(post.getPublishedAt()).isEqualTo(originalPublishedAt);
        assertThat(appeal.getSuspension().getResolution()).isEqualTo(SuspensionResolution.REPUBLISHED);
    }

    @Test
    @DisplayName("반려는 곧 영구 삭제이며, 원본 내려받기 유예를 함께 연다")
    void rejectDeletesAndOpensGracePeriod() {
        Post post = publishedPost();
        post.suspend();
        post.startReview();
        PostAppeal appeal = pendingAppeal(post);
        given(postAppealRepository.findById(APPEAL_ID)).willReturn(Optional.of(appeal));

        adminPostService.rejectAppeal(OPERATOR_ID, APPEAL_ID, review("규정 위반이 확인됩니다"));

        assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
        assertThat(post.getDeleteReason()).isEqualTo(PostDeleteReason.APPEAL_REJECTED);
        assertThat(post.getPurgeAt()).isNotNull();
        assertThat(appeal.getGraceUntil()).isNotNull();
        assertThat(appeal.isWithinGracePeriod(LocalDateTime.now())).isTrue();
        assertThat(appeal.getSuspension().getResolution()).isEqualTo(SuspensionResolution.DELETED_BY_REJECT);
    }

    @Test
    @DisplayName("이미 심사가 끝난 신청은 다시 처리하지 않는다")
    void rejectsReviewingTwice() {
        Post post = publishedPost();
        PostAppeal appeal = pendingAppeal(post);
        appeal.approve(OPERATOR_ID, "승인", LocalDateTime.now());
        given(postAppealRepository.findById(APPEAL_ID)).willReturn(Optional.of(appeal));

        assertThatThrownBy(() -> adminPostService.rejectAppeal(OPERATOR_ID, APPEAL_ID, review("번복")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_APPEAL_ALREADY_REVIEWED);
    }

    // ------------------------------------------------------------------ fixture

    private Post publishedPost() {
        Post post = Post.published(creator, "3주 루틴 기록", new BigDecimal("0.8000"), LocalDateTime.now().minusDays(3));
        ReflectionTestUtils.setField(post, "id", POST_ID);
        return post;
    }

    private PostAppeal pendingAppeal(Post post) {
        PostSuspension suspension = new PostSuspension(post, PostSuspensionReason.AD_DISCLOSURE, null,
                "운영정책 제12조", OPERATOR_ID, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(6));
        ReflectionTestUtils.setField(suspension, "id", 7L);
        PostAppeal appeal = new PostAppeal(suspension, post, "소명합니다", LocalDateTime.now());
        ReflectionTestUtils.setField(appeal, "id", APPEAL_ID);
        return appeal;
    }

    private static AdminPostDto.SuspendRequest suspendRequest(PostSuspensionReason reason, String detail) {
        return new AdminPostDto.SuspendRequest(reason, detail, "운영정책 제12조 3항");
    }

    private static AdminPostDto.AppealReviewRequest review(String comment) {
        return new AdminPostDto.AppealReviewRequest(comment);
    }
}
