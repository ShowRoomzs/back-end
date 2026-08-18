package showroomz.api.app.post.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.post.DTO.PostReportReasonItem;
import showroomz.api.app.post.DTO.PostReportRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostReport;
import showroomz.domain.post.repository.PostReportRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.type.PostReportReason;
import showroomz.domain.post.type.PostReportStatus;
import showroomz.domain.post.type.PostSuspensionReason;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 게시물 신고 접수 (C4 ⋯ 시트 · 하단 고지).
 *
 * <p>이 서비스가 지켜야 하는 것은 <b>접수만 한다</b>는 것과 <b>사람당 게시물당 1회</b>다. 앞의 것이
 * 깨지면 신고가 곧 조치가 되어 경쟁 쇼룸을 신고로 내릴 수 있고, 뒤의 것이 깨지면 신고 건수가
 * "몇 명이 문제라고 봤는가"를 뜻하지 않게 되어 운영자가 그 수로 우선순위를 매길 수 없다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserPostReportServiceTest {

    private static final String USERNAME = "mia";
    private static final long USER_ID = 11L;
    private static final long OWNER_USER_ID = 77L;
    private static final long POST_ID = 301L;
    private static final long SHOWROOM_ID = 5L;

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostReportRepository postReportRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserPostReportService userPostReportService;

    @Nested
    @DisplayName("신고 사유 목록")
    class Reasons {

        @Test
        @DisplayName("기타만 상세 사유를 필수로 요구한다")
        void onlyOtherRequiresDetail() {
            List<PostReportReasonItem> reasons = userPostReportService.getReportReasons();

            assertThat(reasons).hasSize(PostReportReason.values().length);
            assertThat(reasons.stream().filter(PostReportReasonItem::detailRequired))
                    .extracting(PostReportReasonItem::code)
                    .containsExactly(PostReportReason.OTHER);
        }

        @Test
        @DisplayName("사유 코드가 운영자 조치 사유와 같은 축이라 신고를 받아 그대로 내릴 수 있다")
        void mapsOntoSuspensionReasons() {
            for (PostReportReason reason : PostReportReason.values()) {
                PostSuspensionReason mapped = reason.toSuspensionReason();
                assertThat(mapped).isNotNull();
                assertThat(mapped.requiresDetail()).isEqualTo(reason.requiresDetail());
            }
        }
    }

    @Nested
    @DisplayName("신고 접수")
    class Report {

        @Test
        @DisplayName("접수는 대기 상태로만 남고 게시물에는 아무 일도 일어나지 않는다")
        void savesPendingWithoutTouchingThePost() {
            Post post = publishedPost();
            givenUserAndPost(post);
            given(postReportRepository.existsByPost_IdAndReporter_Id(POST_ID, USER_ID)).willReturn(false);

            userPostReportService.reportPost(USERNAME, POST_ID, request(PostReportReason.AD_DISCLOSURE, null));

            ArgumentCaptor<PostReport> captor = ArgumentCaptor.forClass(PostReport.class);
            verify(postReportRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PostReportStatus.PENDING);
            assertThat(captor.getValue().getReasonCode()).isEqualTo(PostReportReason.AD_DISCLOSURE);
            assertThat(post.isVisibleToConsumer()).isTrue();
        }

        @Test
        @DisplayName("기타 사유는 상세 설명 없이 접수하지 않는다")
        void otherRequiresDetail() {
            givenUserAndPost(publishedPost());

            assertThatThrownBy(() ->
                    userPostReportService.reportPost(USERNAME, POST_ID, request(PostReportReason.OTHER, "  ")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_REPORT_DETAIL_REQUIRED);

            verify(postReportRepository, never()).save(any());
        }

        @Test
        @DisplayName("같은 사람이 같은 게시물을 다시 신고하면 409다")
        void oncePerPersonPerPost() {
            givenUserAndPost(publishedPost());
            given(postReportRepository.existsByPost_IdAndReporter_Id(POST_ID, USER_ID)).willReturn(true);

            assertThatThrownBy(() ->
                    userPostReportService.reportPost(USERNAME, POST_ID, request(PostReportReason.COPYRIGHT, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_REPORT_ALREADY_SUBMITTED);

            verify(postReportRepository, never()).save(any());
        }

        @Test
        @DisplayName("선검사를 파고든 동시 요청은 유니크가 잡고 같은 응답으로 돌아간다")
        void concurrentDuplicateFallsBackToUniqueConstraint() {
            givenUserAndPost(publishedPost());
            given(postReportRepository.existsByPost_IdAndReporter_Id(POST_ID, USER_ID)).willReturn(false);
            given(postReportRepository.save(any(PostReport.class)))
                    .willThrow(new DataIntegrityViolationException("uk_post_report"));

            assertThatThrownBy(() ->
                    userPostReportService.reportPost(USERNAME, POST_ID, request(PostReportReason.COPYRIGHT, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_REPORT_ALREADY_SUBMITTED);
        }

        @Test
        @DisplayName("자기 게시물은 신고 대상이 아니다 — 내리려면 스튜디오에서 삭제한다")
        void cannotReportOwnPost() {
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user(OWNER_USER_ID)));
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(publishedPost()));

            assertThatThrownBy(() ->
                    userPostReportService.reportPost(USERNAME, POST_ID, request(PostReportReason.MEDICAL_CLAIM, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_REPORT_SELF_NOT_ALLOWED);

            verify(postReportRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 내려간 게시물은 404다 — 조치 사실이 신고자를 통해 새어 나가지 않는다")
        void suspendedPostLooksMissing() {
            Post post = publishedPost();
            post.suspend();
            givenUserAndPost(post);

            assertThatThrownBy(() ->
                    userPostReportService.reportPost(USERNAME, POST_ID, request(PostReportReason.MISLEADING_AD, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

            verify(postReportRepository, never()).save(any());
        }
    }

    // ------------------------------------------------------------------ 픽스처

    private void givenUserAndPost(Post post) {
        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user(USER_ID)));
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
    }

    private static PostReportRequest request(PostReportReason reason, String detail) {
        PostReportRequest request = new PostReportRequest();
        request.setReasonCode(reason);
        request.setReasonDetail(detail);
        return request;
    }

    private static Users user(long id) {
        LocalDateTime now = LocalDateTime.now();
        Users user = new Users(USERNAME, "미아", "mia@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Post publishedPost() {
        Creator showroom = Creator.builder()
                .id(SHOWROOM_ID)
                .showroomName("제니의 뷰티룸")
                .user(user(OWNER_USER_ID))
                .build();
        Post post = Post.published(showroom, "본문", new BigDecimal("0.8000"), LocalDateTime.now());
        ReflectionTestUtils.setField(post, "id", POST_ID);
        return post;
    }
}
