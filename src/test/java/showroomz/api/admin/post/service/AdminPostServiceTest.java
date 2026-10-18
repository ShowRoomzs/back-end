package showroomz.api.admin.post.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.admin.common.AdminOperatorResolver;
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
import showroomz.domain.post.service.PostReportTransitions;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostMonitoringStatus;
import showroomz.domain.post.type.PostNotificationEvent;
import showroomz.domain.post.type.PostPolicyClause;
import showroomz.domain.post.type.PostReportReason;
import showroomz.domain.post.type.PostReportStatus;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.PostSuspensionReason;
import showroomz.domain.post.type.SuspensionResolution;
import showroomz.global.config.properties.PostProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.BusinessCalendar;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * §24-5 · §54-5 운영자 조치 — 노출 중지 · 신고 종결 · 조치 취소 · 이의 승인 · 반려 · 신고 지목.
 *
 * <p>기한 내 미신청 갈래는 배치의 몫이라 {@code PostLifecycleServiceTest}가 맡는다.
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
    private PostReportRepository postReportRepository;
    @Mock
    private PostNotificationService postNotificationService;
    @Mock
    private AdminOperatorResolver adminOperatorResolver;

    private final PostProperties postProperties = new PostProperties();

    private AdminPostService adminPostService;
    private Creator creator;

    @BeforeEach
    void setUp() {
        creator = Creator.builder().id(5L).showroomName("뷰티 소연").build();
        PostAppealReviewDue reviewDue = new PostAppealReviewDue(new BusinessCalendar(new String[0]), postProperties);
        adminPostService = new AdminPostService(postRepository, postImageRepository, postSuspensionRepository,
                postAppealRepository, postReportRepository, postNotificationService, postProperties, reviewDue,
                adminOperatorResolver, new PostReportTransitions(postReportRepository));

        given(postSuspensionRepository.save(any(PostSuspension.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(adminOperatorResolver.namesOf(any())).willReturn(Map.of());
    }

    @Nested
    @DisplayName("노출 중지")
    class Suspend {

        @Test
        @DisplayName("조치와 동시에 이의 신청 기한을 연다")
        void opensAppealWindow() {
            Post post = lockedPost(publishedPost());

            AdminPostDto.AdminPostActionResponse response = adminPostService.suspend(
                    OPERATOR_ID, POST_ID, suspendRequest(PostSuspensionReason.MISLEADING_INFO, null));

            assertThat(post.getStatus()).isEqualTo(PostStatus.SUSPENDED);
            assertThat(response.getMonitoringStatus()).isEqualTo(PostMonitoringStatus.SUSPENDED);
            assertThat(response.getAppealDeadline())
                    .isAfter(LocalDateTime.now().plusDays(postProperties.getAppealDeadlineDays() - 1L));
        }

        @Test
        @DisplayName("근거 규정 코드와 그 시점 조항 문구를 함께 굳히고, 통지에도 남긴다")
        void snapshotsPolicyClause() {
            Post post = lockedPost(publishedPost());

            adminPostService.suspend(OPERATOR_ID, POST_ID, suspendRequest(PostSuspensionReason.MISLEADING_INFO, null));

            then(postSuspensionRepository).should().save(org.mockito.ArgumentMatchers.argThat(s ->
                    s.getPolicyClauseCode() == PostPolicyClause.ARTICLE_3_2
                            && PostPolicyClause.ARTICLE_3_2.getLabel().equals(s.getPolicyRef())));
            then(postNotificationService).should().notify(eq(post), eq(PostNotificationEvent.SUSPENDED),
                    org.mockito.ArgumentMatchers.argThat(payload -> "ARTICLE_3_2".equals(payload.get("policyClauseCode"))));
        }

        @Test
        @DisplayName("근거 규정 없이 내릴 수 없다 — 이의 · 반려 심사의 판단 기준이다")
        void requiresPolicyClause() {
            lockedPost(publishedPost());

            assertThatThrownBy(() -> adminPostService.suspend(OPERATOR_ID, POST_ID,
                    new AdminPostDto.SuspendRequest(PostSuspensionReason.SPAM, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_POLICY_CLAUSE_REQUIRED);
        }

        @Test
        @DisplayName("운영자 전용 사유(개인정보 노출)도 정상 조치다")
        void operatorOnlyReason() {
            Post post = lockedPost(publishedPost());

            adminPostService.suspend(OPERATOR_ID, POST_ID, new AdminPostDto.SuspendRequest(
                    PostSuspensionReason.PERSONAL_INFO, null, PostPolicyClause.ARTICLE_4_3));

            assertThat(post.getStatus()).isEqualTo(PostStatus.SUSPENDED);
        }

        @Test
        @DisplayName("그 게시물에 걸린 대기 신고가 함께 닫힌다 — 스무 건을 스무 번 누르게 하지 않는다")
        void closesPendingReports() {
            Post post = lockedPost(publishedPost());
            PostReport first = report(post);
            PostReport second = report(post);
            given(postReportRepository.findByPost_IdAndStatus(POST_ID, PostReportStatus.PENDING))
                    .willReturn(List.of(first, second));

            AdminPostDto.AdminPostActionResponse response = adminPostService.suspend(
                    OPERATOR_ID, POST_ID, suspendRequest(PostSuspensionReason.MISLEADING_INFO, null));

            assertThat(first.getStatus()).isEqualTo(PostReportStatus.ACCEPTED);
            assertThat(second.getStatus()).isEqualTo(PostReportStatus.ACCEPTED);
            assertThat(first.getHandledBy()).isEqualTo(OPERATOR_ID);
            assertThat(response.getClosedReportCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("기타 사유는 상세 설명 없이 중지할 수 없다 — 사유 고지가 성립하지 않는다")
        void otherRequiresDetail() {
            lockedPost(publishedPost());

            assertThatThrownBy(() -> adminPostService.suspend(
                    OPERATOR_ID, POST_ID, suspendRequest(PostSuspensionReason.OTHER, "  ")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_SUSPENSION_DETAIL_REQUIRED);
        }

        @Test
        @DisplayName("게시중이 아닌 게시물은 내릴 것이 없다")
        void rejectsNonPublished() {
            Post post = publishedPost();
            post.suspend();
            lockedPost(post);

            assertThatThrownBy(() -> adminPostService.suspend(
                    OPERATOR_ID, POST_ID, suspendRequest(PostSuspensionReason.SPAM, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_EDITABLE);
        }

        @Test
        @DisplayName("행을 잠근 뒤 판정한다 — 동시에 두 번 내려도 열린 조치는 하나다")
        void locksPostRow() {
            lockedPost(publishedPost());

            adminPostService.suspend(OPERATOR_ID, POST_ID, suspendRequest(PostSuspensionReason.SPAM, null));

            then(postRepository).should().findByIdForUpdate(POST_ID);
            then(postRepository).should(never()).findById(any());
        }
    }

    @Nested
    @DisplayName("신고 종결")
    class CloseReports {

        @Test
        @DisplayName("대기 신고 전부를 반려로 닫고 게시물은 그대로 게시중이다 — 누구에게도 알리지 않는다")
        void dismissesAllPendingQuietly() {
            Post post = lockedPost(publishedPost());
            PostReport first = report(post);
            PostReport second = report(post);
            given(postReportRepository.findByPost_IdAndStatus(POST_ID, PostReportStatus.PENDING))
                    .willReturn(List.of(first, second));

            AdminPostDto.AdminPostActionResponse response = adminPostService.closeReports(OPERATOR_ID, POST_ID);

            assertThat(first.getStatus()).isEqualTo(PostReportStatus.DISMISSED);
            assertThat(second.getStatus()).isEqualTo(PostReportStatus.DISMISSED);
            assertThat(first.getHandledAt()).isEqualTo(second.getHandledAt());
            assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
            assertThat(response.getMonitoringStatus()).isEqualTo(PostMonitoringStatus.PUBLISHED);
            assertThat(response.getClosedReportCount()).isEqualTo(2);
            then(postNotificationService).should(never()).notify(any(Post.class), any(), anyMap());
        }

        @Test
        @DisplayName("대기 신고가 없으면 종결할 것이 없다")
        void nothingToClose() {
            lockedPost(publishedPost());
            given(postReportRepository.findByPost_IdAndStatus(POST_ID, PostReportStatus.PENDING)).willReturn(List.of());

            assertThatThrownBy(() -> adminPostService.closeReports(OPERATOR_ID, POST_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_REPORT_NOTHING_TO_CLOSE);
        }

        @Test
        @DisplayName("노출 중지된 게시물은 신고 종결 대상이 아니다")
        void notOnSuspended() {
            Post post = publishedPost();
            post.suspend();
            lockedPost(post);

            assertThatThrownBy(() -> adminPostService.closeReports(OPERATOR_ID, POST_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_REPORT_NOTHING_TO_CLOSE);
        }
    }

    @Nested
    @DisplayName("조치 취소 · 재게시")
    class CancelSuspension {

        @Test
        @DisplayName("재게시하고 조치를 운영자 취소로 닫으며 작성자에게 알린다 — 좋아요는 그대로다")
        void republishesAndNotifies() {
            Post post = publishedPost();
            post.increaseLikeCount();
            post.suspend();
            lockedPost(post);
            PostSuspension suspension = suspension(post);
            given(postSuspensionRepository.findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(POST_ID))
                    .willReturn(Optional.of(suspension));

            AdminPostDto.AdminPostActionResponse response =
                    adminPostService.cancelSuspension(OPERATOR_ID, POST_ID, "근거 규정 적용 오류");

            assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
            assertThat(post.getLikeCount()).isEqualTo(1L);
            assertThat(suspension.getResolution()).isEqualTo(SuspensionResolution.CANCELED_BY_ADMIN);
            assertThat(suspension.getResolvedBy()).isEqualTo(OPERATOR_ID);
            assertThat(response.getMonitoringStatus()).isEqualTo(PostMonitoringStatus.PUBLISHED);
            then(postNotificationService).should().notify(eq(post), eq(PostNotificationEvent.SUSPENSION_CANCELED),
                    org.mockito.ArgumentMatchers.argThat(payload -> "근거 규정 적용 오류".equals(payload.get("note"))));
        }

        @Test
        @DisplayName("이의 심사 중에는 취소할 수 없다 — 승인이 같은 자리다")
        void notDuringReview() {
            Post post = publishedPost();
            post.suspend();
            post.startReview();
            lockedPost(post);

            assertThatThrownBy(() -> adminPostService.cancelSuspension(OPERATOR_ID, POST_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_SUSPENSION_NOT_CANCELABLE);
        }

        @Test
        @DisplayName("승인 재게시 뒤 다시 중지된 게시물도 취소할 수 있다 — 지난 이의 신청 행이 막지 않는다")
        void afterPreviousAppeal() {
            Post post = publishedPost();
            post.suspend();
            lockedPost(post);
            given(postAppealRepository.existsByPost_Id(POST_ID)).willReturn(true);
            given(postSuspensionRepository.findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(POST_ID))
                    .willReturn(Optional.of(suspension(post)));

            adminPostService.cancelSuspension(OPERATOR_ID, POST_ID, null);

            assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        }

        @Test
        @DisplayName("삭제된 게시물은 되돌릴 수 없다")
        void notOnDeleted() {
            Post post = publishedPost();
            post.softDelete(PostDeleteReason.APPEAL_EXPIRED, LocalDateTime.now(), LocalDateTime.now().plusMonths(6));
            lockedPost(post);

            assertThatThrownBy(() -> adminPostService.cancelSuspension(OPERATOR_ID, POST_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_SUSPENSION_NOT_CANCELABLE);
        }
    }

    @Nested
    @DisplayName("이의 신청 심사")
    class Appeal {

        @Test
        @DisplayName("승인하면 재게시되고 좋아요 · 게시 시각이 그대로 남는다")
        void approveRepublishesWithCountersIntact() {
            Post post = publishedPost();
            LocalDateTime originalPublishedAt = post.getPublishedAt();
            post.increaseLikeCount();
            post.suspend();
            post.startReview();
            PostAppeal appeal = pendingAppeal(post);

            adminPostService.approveAppeal(OPERATOR_ID, APPEAL_ID, review("재게시합니다"));

            assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
            assertThat(post.getLikeCount()).isEqualTo(1L);
            assertThat(post.getPublishedAt()).isEqualTo(originalPublishedAt);
            assertThat(appeal.getSuspension().getResolution()).isEqualTo(SuspensionResolution.REPUBLISHED);
        }

        @Test
        @DisplayName("반려는 곧 영구 삭제이며 원본 내려받기 유예(기본 7일)를 함께 연다")
        void rejectDeletesAndOpensGracePeriod() {
            Post post = publishedPost();
            post.suspend();
            post.startReview();
            PostAppeal appeal = pendingAppeal(post);

            adminPostService.rejectAppeal(OPERATOR_ID, APPEAL_ID, review("규정 위반이 확인됩니다"));

            assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
            assertThat(post.getDeleteReason()).isEqualTo(PostDeleteReason.APPEAL_REJECTED);
            assertThat(post.getPurgeAt()).isNotNull();
            assertThat(appeal.getGraceUntil()).isBetween(
                    LocalDateTime.now().plusDays(6), LocalDateTime.now().plusDays(7).plusMinutes(1));
            assertThat(appeal.getSuspension().getResolution()).isEqualTo(SuspensionResolution.DELETED_BY_REJECT);
        }

        @Test
        @DisplayName("반려 사유 없이 반려할 수 없다 — 작성자에게 그대로 전달되는 문구다")
        void rejectRequiresReason() {
            Post post = publishedPost();
            post.suspend();
            post.startReview();
            pendingAppeal(post);

            assertThatThrownBy(() -> adminPostService.rejectAppeal(OPERATOR_ID, APPEAL_ID, review("  ")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_APPEAL_REJECT_REASON_REQUIRED);
            assertThat(post.getStatus()).isEqualTo(PostStatus.UNDER_REVIEW);
        }

        @Test
        @DisplayName("이미 심사가 끝난 신청은 다시 처리하지 않는다")
        void rejectsReviewingTwice() {
            PostAppeal appeal = pendingAppeal(publishedPost());
            appeal.approve(OPERATOR_ID, "승인", LocalDateTime.now());

            assertThatThrownBy(() -> adminPostService.rejectAppeal(OPERATOR_ID, APPEAL_ID, review("번복")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_APPEAL_ALREADY_REVIEWED);
        }
    }

    @Nested
    @DisplayName("신고 지목")
    class Flag {

        @Test
        @DisplayName("켜면 시각 · 운영자가 남고, 끄면 지워진다")
        void togglesFlag() {
            Post post = publishedPost();
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
            PostImage image = new PostImage("https://cdn/3.jpg", "https://cdn/IMG_3302.jpg", 1080, 1350, 1000);
            given(postImageRepository.findByIdAndPost_Id(33L, POST_ID)).willReturn(Optional.of(image));

            adminPostService.flagImage(OPERATOR_ID, POST_ID, 33L, true);
            assertThat(image.isReportFlagged()).isTrue();
            assertThat(image.getReportFlaggedBy()).isEqualTo(OPERATOR_ID);

            adminPostService.flagImage(OPERATOR_ID, POST_ID, 33L, false);
            assertThat(image.isReportFlagged()).isFalse();
            assertThat(image.getReportFlaggedBy()).isNull();
        }

        @Test
        @DisplayName("그 게시물의 사진이 아니면 404다")
        void imageMustBelongToPost() {
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(publishedPost()));
            given(postImageRepository.findByIdAndPost_Id(99L, POST_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminPostService.flagImage(OPERATOR_ID, POST_ID, 99L, true))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_IMAGE_NOT_FOUND);
        }
    }

    @Test
    @DisplayName("사유 목록은 7종이고 운영자 전용은 개인정보 노출 하나다")
    void suspensionReasons() {
        List<AdminPostDto.SuspensionReasonItem> reasons = adminPostService.getSuspensionReasons();

        assertThat(reasons).hasSize(7);
        assertThat(reasons.stream().filter(AdminPostDto.SuspensionReasonItem::isOperatorOnly))
                .extracting(AdminPostDto.SuspensionReasonItem::getCode)
                .containsExactly(PostSuspensionReason.PERSONAL_INFO);
    }

    // ------------------------------------------------------------------ fixture

    private Post lockedPost(Post post) {
        given(postRepository.findByIdForUpdate(POST_ID)).willReturn(Optional.of(post));
        return post;
    }

    /** 신고자는 이 검증에 필요 없다 — 조치가 닫는 것은 상태이지 누가 눌렀는지가 아니다 */
    private static PostReport report(Post post) {
        return new PostReport(post, null, PostReportReason.MISLEADING_INFO, null, LocalDateTime.now());
    }

    private Post publishedPost() {
        Post post = Post.published(creator, "3주 루틴 기록", new BigDecimal("0.8000"), LocalDateTime.now().minusDays(3));
        ReflectionTestUtils.setField(post, "id", POST_ID);
        return post;
    }

    private static PostSuspension suspension(Post post) {
        PostSuspension suspension = new PostSuspension(post, PostSuspensionReason.MISLEADING_INFO, null,
                PostPolicyClause.ARTICLE_3_2, OPERATOR_ID, LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(6));
        ReflectionTestUtils.setField(suspension, "id", 7L);
        return suspension;
    }

    private PostAppeal pendingAppeal(Post post) {
        PostAppeal appeal = new PostAppeal(suspension(post), post, "소명합니다", LocalDateTime.now());
        ReflectionTestUtils.setField(appeal, "id", APPEAL_ID);
        given(postAppealRepository.findByIdForUpdate(APPEAL_ID)).willReturn(Optional.of(appeal));
        return appeal;
    }

    private static AdminPostDto.SuspendRequest suspendRequest(PostSuspensionReason reason, String detail) {
        return new AdminPostDto.SuspendRequest(reason, detail, PostPolicyClause.ARTICLE_3_2);
    }

    private static AdminPostDto.AppealReviewRequest review(String comment) {
        return new AdminPostDto.AppealReviewRequest(comment);
    }
}
