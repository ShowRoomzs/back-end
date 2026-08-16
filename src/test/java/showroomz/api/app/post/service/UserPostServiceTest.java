package showroomz.api.app.post.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    // ------------------------------------------------------------------ 픽스처

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
