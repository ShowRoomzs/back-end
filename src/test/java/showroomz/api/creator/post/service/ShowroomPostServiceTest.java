package showroomz.api.creator.post.service;

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
import showroomz.api.creator.post.DTO.PostDto;
import showroomz.api.creator.post.type.PostSaveAction;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostAppeal;
import showroomz.domain.post.entity.PostSuspension;
import showroomz.domain.post.policy.GeneralPostPolicy;
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
import showroomz.domain.post.type.PostSuspensionReason;
import showroomz.domain.post.type.SuspensionResolution;
import showroomz.global.config.properties.PostProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * §24 스튜디오 게시물 서비스 — 기획이 못박은 <b>거절 조건</b>을 중심으로 검증한다.
 *
 * <p>화면은 버튼 비활성으로 막지만 서버도 같은 규칙으로 막아야 한다는 것이 §24-3의 요구다.
 * 그래서 "정상 저장"보다 "무엇을 거절하는가"가 이 테스트의 본체다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShowroomPostServiceTest {

    private static final long USER_ID = 42L;
    private static final long CREATOR_ID = 5L;
    private static final long POST_ID = 301L;

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostImageRepository postImageRepository;
    @Mock
    private PostSuspensionRepository postSuspensionRepository;
    @Mock
    private PostAppealRepository postAppealRepository;
    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private PostNotificationService postNotificationService;

    private final PostProperties postProperties = new PostProperties();
    private final PostPolicies postPolicies = new PostPolicies(List.of(new GeneralPostPolicy()));

    private ShowroomPostService showroomPostService;
    private Creator me;

    @BeforeEach
    void setUp() {
        me = Creator.builder().id(CREATOR_ID).showroomName("뷰티 소연").build();
        showroomPostService = new ShowroomPostService(
                postRepository, postImageRepository, postSuspensionRepository, postAppealRepository,
                creatorRepository, postPolicies, postNotificationService, postProperties);

        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));
        given(postRepository.save(any(Post.class))).willAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", POST_ID);
            return saved;
        });
    }

    // ------------------------------------------------------------------ 작성

    @Test
    @DisplayName("사진 없이 게시하려 하면 거절한다 — FE는 버튼 비활성으로 막지만 서버도 같은 규칙을 건다")
    void rejectsPublishWithoutImage() {
        PostDto.SavePostRequest request = request(PostSaveAction.PUBLISH, "본문만 있는 게시물", List.of());

        assertThatThrownBy(() -> showroomPostService.createPost(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_IMAGE_REQUIRED);
    }

    @Test
    @DisplayName("임시저장은 본문 1자만 있어도 성립한다 — 무엇이든 남길 게 있으면 저장된다")
    void allowsDraftWithContentOnly() {
        PostDto.SavePostRequest request = request(PostSaveAction.DRAFT, "메", List.of());

        PostDto.PostIdResponse response = showroomPostService.createPost(USER_ID, request);

        assertThat(response.getPostId()).isEqualTo(POST_ID);
    }

    @Test
    @DisplayName("사진도 본문도 없으면 임시저장조차 거절한다")
    void rejectsEmptyDraft() {
        PostDto.SavePostRequest request = request(PostSaveAction.DRAFT, "   ", List.of());

        assertThatThrownBy(() -> showroomPostService.createPost(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_EMPTY);
    }

    @Test
    @DisplayName("사진이 20장을 넘으면 거절한다")
    void rejectsTooManyImages() {
        List<PostDto.PostImageRequest> images = new ArrayList<>();
        for (int i = 0; i < Post.MAX_IMAGE_COUNT + 1; i++) {
            images.add(image(1080, 1350));
        }

        assertThatThrownBy(() -> showroomPostService.createPost(USER_ID, request(PostSaveAction.PUBLISH, null, images)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_IMAGE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("첫 사진 비율이 허용 범위(1.91:1 ~ 4:5) 밖이면 거절한다 — 크롭은 FE가 하고 서버는 결과를 검증한다")
    void rejectsAspectRatioOutOfRange() {
        // 9:16 세로 — 4:5(0.8)보다 좁다
        List<PostDto.PostImageRequest> images = List.of(image(1080, 1920));

        assertThatThrownBy(() -> showroomPostService.createPost(USER_ID, request(PostSaveAction.PUBLISH, null, images)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_ASPECT_RATIO_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("게시물 비율은 첫 사진 기준으로 정해지고 나머지 사진은 영향을 주지 않는다")
    void aspectRatioComesFromFirstImage() {
        List<PostDto.PostImageRequest> images = List.of(image(1080, 1350), image(1920, 1080));

        showroomPostService.createPost(USER_ID, request(PostSaveAction.PUBLISH, null, images));

        // 1080/1350 = 0.8 — 4:5
        assertThat(savedPost().getAspectRatio()).isEqualByComparingTo("0.8000");
    }

    // ------------------------------------------------------------------ 수정·삭제

    @Test
    @DisplayName("노출 중지 상태에서는 수정할 수 없다 — 심사 대상이 도중에 바뀌면 안 된다")
    void rejectsUpdateWhileSuspended() {
        Post post = publishedPost();
        post.suspend();
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

        PostDto.SavePostRequest request = request(PostSaveAction.PUBLISH, "고쳐 볼까", List.of(image(1080, 1350)));

        assertThatThrownBy(() -> showroomPostService.updatePost(USER_ID, POST_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_EDITABLE);
    }

    @Test
    @DisplayName("심사 중에는 삭제할 수 없다 — 신청 후 도중에 지우면 처리 결과가 붕 뜬다")
    void rejectsDeleteWhileUnderReview() {
        Post post = publishedPost();
        post.startReview();
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> showroomPostService.deletePost(USER_ID, POST_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_DELETABLE);
    }

    @Test
    @DisplayName("노출 중지 중 본인 삭제는 허용하고, 진행 중이던 조치도 함께 닫는다")
    void allowsSelfDeleteWhileSuspendedAndClosesSuspension() {
        Post post = publishedPost();
        post.suspend();
        PostSuspension suspension = suspension(post);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(postSuspensionRepository.findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(POST_ID))
                .willReturn(Optional.of(suspension));

        showroomPostService.deletePost(USER_ID, POST_ID);

        assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
        assertThat(post.getDeleteReason()).isEqualTo(PostDeleteReason.SELF);
        assertThat(suspension.getResolution()).isEqualTo(SuspensionResolution.DELETED_BY_SELF);
    }

    @Test
    @DisplayName("삭제는 행을 지우지 않고 보관 만료 시각을 남긴다 — 인플루언서 기준의 삭제다")
    void softDeleteSetsPurgeSchedule() {
        Post post = publishedPost();
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

        showroomPostService.deletePost(USER_ID, POST_ID);

        assertThat(post.getDeletedAt()).isNotNull();
        assertThat(post.getPurgeAt()).isAfter(post.getDeletedAt());
    }

    // ------------------------------------------------------------------ 이의 신청

    @Test
    @DisplayName("이의 신청은 게시물당 1회다")
    void rejectsSecondAppeal() {
        Post post = publishedPost();
        post.suspend();
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(postAppealRepository.existsByPost_Id(POST_ID)).willReturn(true);

        PostDto.AppealRequest request = new PostDto.AppealRequest("다시 봐 주세요");

        assertThatThrownBy(() -> showroomPostService.submitAppeal(USER_ID, POST_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_APPEAL_ALREADY_SUBMITTED);
    }

    @Test
    @DisplayName("기한이 지난 뒤의 이의 신청은 거절한다")
    void rejectsAppealAfterDeadline() {
        Post post = publishedPost();
        post.suspend();
        PostSuspension expired = new PostSuspension(post, PostSuspensionReason.AD_DISCLOSURE, null, null,
                9L, LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(3));
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(postSuspensionRepository.findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(POST_ID))
                .willReturn(Optional.of(expired));

        PostDto.AppealRequest request = new PostDto.AppealRequest("늦었지만 소명합니다");

        assertThatThrownBy(() -> showroomPostService.submitAppeal(USER_ID, POST_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_APPEAL_DEADLINE_PASSED);
    }

    @Test
    @DisplayName("이의 신청이 접수되면 상태가 심사 중으로 바뀐다 — 이 상태에서만 삭제가 막힌다")
    void appealMovesPostToUnderReview() {
        Post post = publishedPost();
        post.suspend();
        PostSuspension suspension = suspension(post);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(postSuspensionRepository.findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(POST_ID))
                .willReturn(Optional.of(suspension));
        given(postAppealRepository.save(any(PostAppeal.class))).willAnswer(invocation -> invocation.getArgument(0));

        PostDto.AppealResponse response =
                showroomPostService.submitAppeal(USER_ID, POST_ID, new PostDto.AppealRequest("소명합니다"));

        assertThat(post.getStatus()).isEqualTo(PostStatus.UNDER_REVIEW);
        assertThat(response.getStatus()).isEqualTo(PostAppealStatus.PENDING);
        assertThat(post.getStatus().isDeletable()).isFalse();
    }

    /**
     * 게시 — 작성중 → 게시중으로 넘어가는 자리이자 <b>팔로워 통지가 나가는 유일한 시점</b>이다 (§24-3).
     *
     * <p>통지는 되돌릴 수 없다. 그래서 두 가지를 못 박는다: 이미 게시된 글을 다시 게시하려 하면
     * 막아 통지가 두 번 나가지 않게 하고, 게시 후 수정은 통지를 <b>재발송하지 않는다</b>.
     * 수정마다 알림이 가면 팔로워는 같은 글로 여러 번 불린다.
     */
    @Nested
    @DisplayName("게시 · 통지")
    class Publish {

        private Post draft() {
            Post post = Post.draft(me, "초안 본문", new java.math.BigDecimal("1.0000"));
            post.replaceImages(List.of(new showroomz.domain.post.entity.PostImage(
                    "https://cdn.example.com/posts/a.jpg", "https://cdn.example.com/posts/a-origin.jpg",
                    1080, 1080, 2_048_000)));
            ReflectionTestUtils.setField(post, "id", POST_ID);
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
            return post;
        }

        @Test
        @DisplayName("작성중 게시물을 게시하면 게시중으로 바뀌고 게시 시각이 남는다")
        void draftBecomesPublished() {
            Post post = draft();

            showroomPostService.publish(USER_ID, POST_ID);

            assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
            assertThat(post.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("게시하면 팔로워에게 통지한다 — 통지가 나가는 유일한 시점이다")
        void publishNotifiesFollowers() {
            draft();

            showroomPostService.publish(USER_ID, POST_ID);

            verify(postNotificationService).notify(any(Post.class),
                    org.mockito.ArgumentMatchers.eq(PostNotificationEvent.PUBLISHED_TO_FOLLOWERS),
                    org.mockito.ArgumentMatchers.any());
        }

        /** 두 번 게시되면 팔로워에게 같은 글 알림이 두 번 간다 — 되돌릴 수 없는 부작용이다. */
        @Test
        @DisplayName("이미 게시된 글은 다시 게시할 수 없다")
        void republishIsRejected() {
            Post post = publishedPost();
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> showroomPostService.publish(USER_ID, POST_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_ALREADY_PUBLISHED);

            verify(postNotificationService, never()).notify(any(), any(), any());
        }

        @Test
        @DisplayName("노출 중지된 글은 게시할 수 없다 — 조치를 우회하는 경로가 되면 안 된다")
        void suspendedPostCannotBePublished() {
            Post post = publishedPost();
            post.suspend();
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> showroomPostService.publish(USER_ID, POST_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_EDITABLE);
        }

        /** 게시 후 수정마다 알림이 가면 팔로워는 같은 글로 여러 번 불린다 (§24-3). */
        @Test
        @DisplayName("게시 후 수정은 팔로워 통지를 재발송하지 않는다")
        void editAfterPublishDoesNotRenotify() {
            Post post = publishedPost();
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

            showroomPostService.updatePost(USER_ID, POST_ID,
                    request(PostSaveAction.PUBLISH, "고친 본문", List.of(image(1080, 1080))));

            verify(postNotificationService, never()).notify(any(),
                    org.mockito.ArgumentMatchers.eq(PostNotificationEvent.PUBLISHED_TO_FOLLOWERS),
                    org.mockito.ArgumentMatchers.any());
        }

        /** 임시저장에서 곧바로 게시로 넘어가는 것은 첫 게시이므로 통지가 나가야 한다. */
        @Test
        @DisplayName("임시저장을 수정하며 바로 게시하면 첫 게시로 보고 통지한다")
        void publishingFromDraftViaUpdateNotifies() {
            draft();

            showroomPostService.updatePost(USER_ID, POST_ID,
                    request(PostSaveAction.PUBLISH, "완성한 본문", List.of(image(1080, 1080))));

            verify(postNotificationService).notify(any(Post.class),
                    org.mockito.ArgumentMatchers.eq(PostNotificationEvent.PUBLISHED_TO_FOLLOWERS),
                    org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("임시저장으로 수정하면 통지하지 않는다 — 아직 아무에게도 보이지 않는 글이다")
        void savingDraftDoesNotNotify() {
            draft();

            showroomPostService.updatePost(USER_ID, POST_ID,
                    request(PostSaveAction.DRAFT, "고친 초안", List.of(image(1080, 1080))));

            verify(postNotificationService, never()).notify(any(), any(), any());
        }

        /** 남의 게시물은 조회 자체가 (postId, creatorId)로 좁혀져 잡히지 않는다. */
        @Test
        @DisplayName("남의 게시물은 게시할 수 없다")
        void othersPostCannotBePublished() {
            given(postRepository.findById(POST_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> showroomPostService.publish(USER_ID, POST_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("수정 시 사진 교체")
    class ImageReplacement {

        /**
         * 사진은 전체 교체다 — {@code (post_id, sort_order)} 유니크 때문에 부분 갱신은 중간 상태에서
         * 충돌한다. 교체 후 장수와 순서가 요청과 정확히 같아야 한다.
         */
        @Test
        @DisplayName("사진을 보내면 기존 사진을 전부 대체한다")
        void imagesAreFullyReplaced() {
            Post post = publishedPost();
            post.replaceImages(List.of(new showroomz.domain.post.entity.PostImage(
                    "https://cdn.example.com/posts/old.jpg", null, 1080, 1080, 1000)));
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

            showroomPostService.updatePost(USER_ID, POST_ID, request(PostSaveAction.PUBLISH, "본문",
                    List.of(image(1080, 1080), image(1080, 1080))));

            assertThat(post.getImages()).hasSize(2);
            assertThat(post.getImageCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("수정으로도 사진을 20장 넘게 올릴 수 없다")
        void tooManyImagesOnUpdateIsRejected() {
            Post post = publishedPost();
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

            List<PostDto.PostImageRequest> tooMany = new java.util.ArrayList<>();
            for (int i = 0; i < 21; i++) {
                tooMany.add(image(1080, 1080));
            }

            assertThatThrownBy(() -> showroomPostService.updatePost(USER_ID, POST_ID,
                    request(PostSaveAction.PUBLISH, "본문", tooMany)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ------------------------------------------------------------------ fixture

    private Post savedPost() {
        org.mockito.ArgumentCaptor<Post> captor = org.mockito.ArgumentCaptor.forClass(Post.class);
        org.mockito.BDDMockito.then(postRepository).should().save(captor.capture());
        return captor.getValue();
    }

    private Post publishedPost() {
        Post post = Post.published(me, "3주 루틴 기록", new java.math.BigDecimal("0.8000"), LocalDateTime.now());
        ReflectionTestUtils.setField(post, "id", POST_ID);
        return post;
    }

    private PostSuspension suspension(Post post) {
        return new PostSuspension(post, PostSuspensionReason.AD_DISCLOSURE, null, "운영정책 제12조",
                9L, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(6));
    }

    private static PostDto.SavePostRequest request(PostSaveAction action, String content,
                                                   List<PostDto.PostImageRequest> images) {
        return new PostDto.SavePostRequest(content, images, action);
    }

    private static PostDto.PostImageRequest image(int width, int height) {
        return new PostDto.PostImageRequest(
                "https://cdn.example.com/posts/a.jpg", "https://cdn.example.com/posts/a-origin.jpg",
                width, height, 2_048_000);
    }
}
