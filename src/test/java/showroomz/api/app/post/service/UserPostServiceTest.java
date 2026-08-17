package showroomz.api.app.post.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.app.post.DTO.PostDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.policy.GeneralPostPolicy;
import showroomz.domain.post.policy.PostPolicies;
import showroomz.domain.post.repository.PostImageRepository;
import showroomz.domain.post.repository.PostLikeRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.type.LikedPostSort;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * C3 좋아요 — 목록 조회가 화면 규칙과 맞는지 본다.
 *
 * <p>정렬 자체는 쿼리가 하는 일이라 여기서 순서를 검증하지 않는다. 대신 <b>서비스가 정렬을 삼키지
 * 않고 그대로 넘기는지</b>와, 페이징 객체에 엉뚱한 {@code Sort}가 묻어 들어가지 않는지를 본다 —
 * 정렬 키가 {@code post_like}에 있어 {@code Pageable}의 정렬이 섞이면 조용히 어긋난다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserPostServiceTest {

    private static final String USERNAME = "mia";
    private static final long USER_ID = 42L;
    private static final long POST_ID = 301L;

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private PostImageRepository postImageRepository;
    @Mock
    private CreatorFollowRepository creatorFollowRepository;
    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private UserRepository userRepository;

    private final PostPolicies postPolicies = new PostPolicies(List.of(new GeneralPostPolicy()));

    private UserPostService userPostService;

    @BeforeEach
    void setUp() {
        userPostService = new UserPostService(
                postRepository, postLikeRepository, postImageRepository,
                creatorFollowRepository, creatorRepository, userRepository, postPolicies);

        Users user = new Users();
        user.setId(USER_ID);
        user.setUsername(USERNAME);
        user.setNickname("미아");
        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
        given(postImageRepository.findByPostIdsOrdered(any())).willReturn(List.of());
    }

    @Test
    @DisplayName("정렬 기준을 그대로 쿼리에 넘긴다 — 서비스가 기본값으로 덮어쓰지 않는다")
    void passesSortThrough() {
        givenLikedPage(List.of(publishedPost()), 1);

        userPostService.getLikedPosts(USERNAME, LikedPostSort.MOST_LIKED, new PagingRequest());

        verify(postLikeRepository).findLikedPostsByUserId(eq(USER_ID), eq(LikedPostSort.MOST_LIKED), any());
    }

    @Test
    @DisplayName("페이징에 정렬을 싣지 않는다 — 순서는 post_like 기준이라 쿼리가 직접 잡는다")
    void doesNotLeakPageableSort() {
        givenLikedPage(List.of(publishedPost()), 1);

        userPostService.getLikedPosts(USERNAME, LikedPostSort.DEFAULT, new PagingRequest());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(postLikeRepository).findLikedPostsByUserId(anyLong(), any(), captor.capture());
        assertThat(captor.getValue().getSort().isSorted()).isFalse();
    }

    @Test
    @DisplayName("목록의 모든 항목은 좋아요 상태다 — 좋아요한 것만 모은 목록이므로 다시 물어보지 않는다")
    void marksEveryItemLiked() {
        givenLikedPage(List.of(publishedPost()), 1);

        PageResponse<PostDto.FeedItemResponse> response =
                userPostService.getLikedPosts(USERNAME, LikedPostSort.DEFAULT, new PagingRequest());

        assertThat(response.getContent()).singleElement()
                .satisfies(item -> assertThat(item.getPost().getIsLiked()).isTrue());
    }

    @Test
    @DisplayName("일반 게시물은 좋아요가 잠기지 않는다 — likeLocked는 마감된 공구에서만 켜진다")
    void generalPostIsNotLikeLocked() {
        givenLikedPage(List.of(publishedPost()), 1);

        PageResponse<PostDto.FeedItemResponse> response =
                userPostService.getLikedPosts(USERNAME, LikedPostSort.DEFAULT, new PagingRequest());

        assertThat(response.getContent().get(0).getPost().getLikeLocked()).isFalse();
    }

    @Test
    @DisplayName("상단 카운트는 페이지가 아니라 전체 개수다 — 스크롤해도 값이 흔들리면 안 된다")
    void countIsTotalNotPageSize() {
        givenLikedPage(List.of(publishedPost()), 37);

        PageResponse<PostDto.FeedItemResponse> response =
                userPostService.getLikedPosts(USERNAME, LikedPostSort.DEFAULT, new PagingRequest());

        assertThat(response.getPageInfo().getTotalResults()).isEqualTo(37);
        assertThat(response.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("C1 추천 — 팔로우한 쇼룸과 본인 쇼룸을 제외하고 조회한다")
    void recommendedFeedExcludesFollowedAndOwnShowroom() {
        given(creatorFollowRepository.findCreatorIdsByUserId(USER_ID)).willReturn(List.of(10L, 11L));
        given(creatorRepository.findByUser_Id(USER_ID))
                .willReturn(Optional.of(Creator.builder().id(99L).showroomName("내 쇼룸").build()));
        givenRecommendedPage(List.of(publishedPost()), 1);

        userPostService.getRecommendedFeed(USERNAME, new PagingRequest());

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(postRepository).findRecommendedPosts(captor.capture(), any());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(10L, 11L, 99L);
    }

    @Test
    @DisplayName("C1 추천 — 크리에이터가 아닌 유저는 팔로잉만 제외한다")
    void recommendedFeedExcludesOnlyFollowingForPlainUser() {
        given(creatorFollowRepository.findCreatorIdsByUserId(USER_ID)).willReturn(List.of(10L));
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.empty());
        givenRecommendedPage(List.of(publishedPost()), 1);

        userPostService.getRecommendedFeed(USERNAME, new PagingRequest());

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(postRepository).findRecommendedPosts(captor.capture(), any());
        assertThat(captor.getValue()).containsExactly(10L);
    }

    @Test
    @DisplayName("C1 추천 — 항목은 전부 미팔로우다. 팔로우 여부를 다시 묻지 않는다")
    void recommendedFeedItemsAreNotFollowing() {
        given(creatorFollowRepository.findCreatorIdsByUserId(USER_ID)).willReturn(List.of());
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.empty());
        givenRecommendedPage(List.of(publishedPost()), 1);

        PageResponse<PostDto.FeedItemResponse> response =
                userPostService.getRecommendedFeed(USERNAME, new PagingRequest());

        assertThat(response.getContent()).singleElement()
                .satisfies(item -> assertThat(item.getPost().getIsFollowing()).isFalse());
        verify(creatorFollowRepository, never()).findFollowedCreatorIds(anyLong(), any());
    }

    @Test
    @DisplayName("팔로잉 피드 항목은 전부 팔로우 중이다 — 팔로우 버튼이 붙지 않게 한다")
    void followingFeedItemsAreFollowing() {
        given(creatorFollowRepository.findCreatorIdsByUserId(USER_ID)).willReturn(List.of(10L));
        given(postRepository.findDisplayedPostsByFollowingCreatorIds(any(), any()))
                .willAnswer(invocation -> new PageImpl<>(
                        List.of(publishedPost()), invocation.getArgument(1), 1));

        PageResponse<PostDto.FeedItemResponse> response =
                userPostService.getFollowingFeed(USERNAME, new PagingRequest());

        assertThat(response.getContent()).singleElement()
                .satisfies(item -> assertThat(item.getPost().getIsFollowing()).isTrue());
        verify(creatorFollowRepository, never()).findFollowedCreatorIds(anyLong(), any());
    }

    @Test
    @DisplayName("팔로우한 쇼룸이 없으면 팔로잉 피드는 빈 페이지다 — 빈 상태는 추천 피드가 채운다")
    void followingFeedIsEmptyWithoutFollowing() {
        given(creatorFollowRepository.findCreatorIdsByUserId(USER_ID)).willReturn(List.of());

        PageResponse<PostDto.FeedItemResponse> response =
                userPostService.getFollowingFeed(USERNAME, new PagingRequest());

        assertThat(response.getContent()).isEmpty();
    }

    /**
     * 좋아요 토글 — <b>멱등</b>하다.
     *
     * <p>앱이 낙관적 토글이라 같은 요청이 두 번 도착하는 일이 흔하다. 두 번째 요청을 오류로 돌려주면
     * 화면의 하트가 되돌아가고, 그렇다고 카운터를 두 번 올리면 좋아요 수가 부풀어 목록 정렬이 어긋난다.
     * 그래서 이미 눌린 상태에서 다시 눌러도 <b>성공으로 끝내되 카운터는 그대로</b> 둔다.
     */
    @Nested
    @DisplayName("좋아요 토글")
    class LikeToggle {

        private Post givenPost() {
            Post post = publishedPost();
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user()));
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
            return post;
        }

        @Test
        @DisplayName("처음 누르면 좋아요가 저장되고 카운터가 올라간다")
        void firstLikeIsSaved() {
            Post post = givenPost();
            given(postLikeRepository.existsByUserIdAndPostId(USER_ID, POST_ID)).willReturn(false);

            userPostService.likePost(USERNAME, POST_ID);

            verify(postLikeRepository).save(any());
            assertThat(post.getLikeCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 눌러 뒀으면 다시 저장하지 않고 카운터도 그대로다")
        void repeatedLikeIsIdempotent() {
            Post post = givenPost();
            given(postLikeRepository.existsByUserIdAndPostId(USER_ID, POST_ID)).willReturn(true);

            userPostService.likePost(USERNAME, POST_ID);

            verify(postLikeRepository, never()).save(any());
            assertThat(post.getLikeCount()).isZero();
        }

        @Test
        @DisplayName("취소하면 좋아요가 지워지고 카운터가 내려간다")
        void unlikeRemovesAndDecrements() {
            Post post = givenPost();
            post.increaseLikeCount();
            given(postLikeRepository.existsByUserIdAndPostId(USER_ID, POST_ID)).willReturn(true);

            userPostService.unlikePost(USERNAME, POST_ID);

            verify(postLikeRepository).deleteByUserIdAndPostId(USER_ID, POST_ID);
            assertThat(post.getLikeCount()).isZero();
        }

        /** 누른 적 없는 좋아요를 취소해도 오류가 아니다 — 카운터가 음수로 내려가는 것만 막으면 된다. */
        @Test
        @DisplayName("누른 적 없이 취소해도 성공으로 끝나고 카운터는 내려가지 않는다")
        void unlikeWithoutLikeIsIdempotent() {
            Post post = givenPost();
            given(postLikeRepository.existsByUserIdAndPostId(USER_ID, POST_ID)).willReturn(false);

            userPostService.unlikePost(USERNAME, POST_ID);

            verify(postLikeRepository, never()).deleteByUserIdAndPostId(anyLong(), anyLong());
            assertThat(post.getLikeCount()).isZero();
        }

        /** 내려간 게시물에 좋아요를 받으면 목록에 없는 글의 카운터가 올라간다. */
        @Test
        @DisplayName("내려간 게시물에는 좋아요를 누를 수 없다 — 없는 게시물과 같이 응답한다")
        void suspendedPostCannotBeLiked() {
            Post post = publishedPost();
            post.suspend();
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user()));
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> userPostService.likePost(USERNAME, POST_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

            verify(postLikeRepository, never()).save(any());
        }

        /** 취소는 내려간 게시물에도 열어 둔다 — 이미 누른 것을 되돌릴 길을 막으면 안 된다. */
        @Test
        @DisplayName("내려간 게시물의 좋아요는 취소할 수 있다")
        void suspendedPostCanStillBeUnliked() {
            Post post = publishedPost();
            post.increaseLikeCount();
            post.suspend();
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user()));
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
            given(postLikeRepository.existsByUserIdAndPostId(USER_ID, POST_ID)).willReturn(true);

            userPostService.unlikePost(USERNAME, POST_ID);

            assertThat(post.getLikeCount()).isZero();
        }

        @Test
        @DisplayName("없는 게시물에는 좋아요를 누를 수 없다")
        void unknownPostIsRejected() {
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user()));
            given(postRepository.findById(POST_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userPostService.likePost(USERNAME, POST_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        }

        @Test
        @DisplayName("비로그인 상태로는 좋아요를 누를 수 없다")
        void unknownUserIsRejected() {
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userPostService.likePost(USERNAME, POST_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }
    }

    /**
     * 게시물 단건 조회 — 게시중이 아닌 게시물은 소비자에게 <b>없는 것</b>이다.
     *
     * <p>작성중·노출 중지·삭제를 구분해 알려주면 상태가 드러난다. 특히 노출 중지는 조치 사실이므로,
     * 404 하나로 덮어 상태를 추측할 수 없게 한다.
     */
    @Nested
    @DisplayName("게시물 단건 조회")
    class PostDetail {

        @Test
        @DisplayName("게시중인 게시물은 열리고 좋아요 상태가 함께 실린다")
        void publishedPostIsServedWithLikeState() {
            given(postRepository.findByIdWithImages(POST_ID)).willReturn(Optional.of(publishedPost()));
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user()));
            given(postLikeRepository.existsByUserIdAndPostId(USER_ID, POST_ID)).willReturn(true);

            PostDto.PostDetailResponse response = userPostService.getPostById(USERNAME, POST_ID);

            assertThat(response.getPostId()).isEqualTo(POST_ID);
            assertThat(response.getIsLiked()).isTrue();
            assertThat(response.getShowroomName()).isEqualTo("미아 스킨노트");
        }

        /** 비로그인도 상세를 볼 수 있다 — 다만 하트는 꺼진 상태로 그린다. */
        @Test
        @DisplayName("비로그인이 열면 좋아요 상태는 꺼져 있다")
        void anonymousSeesUnlikedState() {
            given(postRepository.findByIdWithImages(POST_ID)).willReturn(Optional.of(publishedPost()));

            PostDto.PostDetailResponse response = userPostService.getPostById(null, POST_ID);

            assertThat(response.getIsLiked()).isFalse();
        }

        @Test
        @DisplayName("일반 게시물의 하트는 잠기지 않는다 — 잠김은 마감된 공구에서만이다")
        void generalPostIsNotLikeLocked() {
            given(postRepository.findByIdWithImages(POST_ID)).willReturn(Optional.of(publishedPost()));

            assertThat(userPostService.getPostById(null, POST_ID).getLikeLocked()).isFalse();
        }

        @Test
        @DisplayName("노출 중지된 게시물은 404다 — 조치 사실을 드러내지 않는다")
        void suspendedPostIsNotFound() {
            Post post = publishedPost();
            post.suspend();
            given(postRepository.findByIdWithImages(POST_ID)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> userPostService.getPostById(USERNAME, POST_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        }

        @Test
        @DisplayName("없는 게시물도 같은 404다")
        void unknownPostIsNotFound() {
            given(postRepository.findByIdWithImages(POST_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userPostService.getPostById(USERNAME, POST_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("쇼룸별 · 전체 목록")
    class PostList {

        /** 쇼룸 프로필의 게시물 그리드와 전체 탐색이 같은 메서드를 쓰므로 어느 쿼리로 가는지가 계약이다. */
        @Test
        @DisplayName("쇼룸을 지정하면 그 쇼룸의 게시물만 조회한다")
        void showroomScopedQueryIsUsed() {
            given(postRepository.findDisplayedPostsByCreatorId(anyLong(), any()))
                    .willAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));

            userPostService.getPostList(USERNAME, new PagingRequest(), 10L);

            verify(postRepository).findDisplayedPostsByCreatorId(org.mockito.ArgumentMatchers.eq(10L), any());
            verify(postRepository, never()).findDisplayedPosts(any());
        }

        @Test
        @DisplayName("쇼룸을 지정하지 않으면 전체 게시물을 조회한다")
        void globalQueryIsUsedWithoutShowroom() {
            given(postRepository.findDisplayedPosts(any()))
                    .willAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(0), 0));

            userPostService.getPostList(USERNAME, new PagingRequest(), null);

            verify(postRepository).findDisplayedPosts(any());
            verify(postRepository, never()).findDisplayedPostsByCreatorId(anyLong(), any());
        }

        /** 비로그인 목록에서 사용자 기준 조회를 돌리면 불필요한 쿼리가 나가고 NPE 위험이 생긴다. */
        @Test
        @DisplayName("비로그인 목록은 좋아요·팔로우 조회 없이 그려진다")
        void anonymousListSkipsPersonalQueries() {
            given(postRepository.findDisplayedPosts(any()))
                    .willAnswer(invocation -> new PageImpl<>(List.of(publishedPost()), invocation.getArgument(0), 1));

            PageResponse<PostDto.FeedItemResponse> response =
                    userPostService.getPostList(null, new PagingRequest(), null);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getPost().getIsLiked()).isFalse();
            verify(postLikeRepository, never()).findLikedPostIdsByUserIdAndPostIds(anyLong(), any());
        }
    }

    // ------------------------------------------------------------------ 픽스처

    private Users user() {
        Users found = new Users();
        ReflectionTestUtils.setField(found, "id", USER_ID);
        ReflectionTestUtils.setField(found, "username", USERNAME);
        return found;
    }

    private void givenRecommendedPage(List<Post> posts, long total) {
        given(postRepository.findRecommendedPosts(any(), any()))
                .willAnswer(invocation -> new PageImpl<>(posts, invocation.getArgument(1), total));
    }

    private void givenLikedPage(List<Post> posts, long total) {
        given(postLikeRepository.findLikedPostsByUserId(anyLong(), any(), any()))
                .willAnswer(invocation -> new PageImpl<>(posts, invocation.getArgument(2), total));
    }

    private Post publishedPost() {
        Creator creator = Creator.builder().id(10L).showroomName("미아 스킨노트").build();
        Post post = Post.published(creator, "요즘 아침 루틴 정리했어요",
                new BigDecimal("0.8000"), LocalDateTime.now());
        ReflectionTestUtils.setField(post, "id", POST_ID);
        return post;
    }
}
