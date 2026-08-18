package showroomz.api.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostSuspension;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.repository.PostSuspensionRepository;
import showroomz.domain.post.type.PostSuspensionReason;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §24 게시 → C1 소비자 확인까지 <b>한 줄로</b> 태우는 통합 테스트.
 *
 * <p>기존 피드 테스트는 게시물을 리포지토리로 직접 심어 두고 조회만 확인한다. 그러면 작성 API가
 * 실제로 무엇을 저장하는지(사진 순서·비율·상태)는 검증되지 않고, 저장 경로에 생긴 회귀가
 * 소비자 화면에 도달할 때까지 아무 테스트도 울리지 않는다. 여기서는 <b>크리에이터 API로 올리고
 * 소비자 API로 확인한다</b> — 그 사이의 계약이 이 파일의 검증 대상이다.
 *
 * <p>사진 교체를 특히 눈여겨본다. {@code (post_id, sort_order)} 유니크가 걸려 있어서
 * "전부 지우고 다시 넣는" 교체가 한 플러시 안에서 충돌할 수 있는데, 리포지토리를 흉내 내는
 * 단위 테스트는 이 제약을 볼 수 없다.
 */
@DisplayName("[통합] §24 게시물 게시 → C1 소비자 확인")
class PostPublishToFeedIntegrationTest extends IntegrationTestSupport {

    private static final String CREATOR_POSTS = "/v1/creator/posts";
    private static final String CREATOR_POST = "/v1/creator/posts/{postId}";
    private static final String CREATOR_PUBLISH = "/v1/creator/posts/{postId}/publish";
    private static final String CREATOR_APPEAL = "/v1/creator/posts/{postId}/appeal";

    private static final String SHOWROOM_FEED = "/v1/user/showrooms/{showroomId}/posts";
    private static final String FOLLOWING_FEED = "/v1/user/feed/following";
    private static final String POST_DETAIL = "/v1/user/showrooms/posts/{postId}";
    private static final String POST_LIKE = "/v1/user/showrooms/posts/{postId}/wishlist";
    private static final String IMPRESSIONS = "/v1/user/posts/impressions";

    /** 상태 탭은 [전체, 게시중, 노출 중지, 작성중] 순으로 내려간다 (§24-1) */
    private static final int TAB_ALL = 0;
    private static final int TAB_SUSPENDED = 2;

    private static final String CDN = "https://cdn.example.com/";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;
    @Autowired
    private CreatorFollowRepository creatorFollowRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostSuspensionRepository postSuspensionRepository;

    private Creator showroom;
    private String creatorToken;
    private String viewerToken;

    @BeforeEach
    void setUpParticipants() {
        showroom = createShowroom("제니의 뷰티룸", "jenny");
        Users owner = showroom.getUser();
        creatorToken = bearerToken(owner.getUsername(), RoleType.CREATOR, owner.getId());

        Users viewer = createUser("mia", "미아");
        viewerToken = bearerToken(viewer.getUsername(), RoleType.USER, viewer.getId());
        creatorFollowRepository.save(new CreatorFollow(viewer, showroom));
    }

    @Nested
    @DisplayName("게시하면 소비자에게 보인다")
    class PublishedPostReachesConsumer {

        /**
         * 비율은 <b>첫 사진</b>에서 나오고 나머지 사진에는 영향받지 않는다(§24-2). 소비자 카드가
         * 고정 높이로 그려지지 않으려면 이 값이 응답까지 살아 있어야 한다.
         */
        @Test
        @DisplayName("올린 그대로 — 사진 순서·장수·비율이 피드와 상세에 살아 있다")
        void publishedPostIsServedAsUploaded() throws Exception {
            Long postId = createPost("아침 루틴 정리했어요", "PUBLISH",
                    List.of(image("cover.jpg", 1080, 1350), image("second.jpg", 1000, 1000)));

            mockMvc.perform(get(SHOWROOM_FEED, showroom.getId()).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                    .andExpect(jsonPath("$.content[0].post.postId").value(postId))
                    .andExpect(jsonPath("$.content[0].post.showroomName").value("제니의 뷰티룸"))
                    .andExpect(jsonPath("$.content[0].post.imageCount").value(2))
                    .andExpect(jsonPath("$.content[0].post.imageUrls[0]").value(CDN + "cover.jpg"))
                    .andExpect(jsonPath("$.content[0].post.imageUrls[1]").value(CDN + "second.jpg"))
                    .andExpect(jsonPath("$.content[0].post.aspectRatio").value(0.8))
                    .andExpect(jsonPath("$.content[0].post.isFollowing").value(true));

            mockMvc.perform(get(POST_DETAIL, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("아침 루틴 정리했어요"))
                    .andExpect(jsonPath("$.imageCount").value(2))
                    .andExpect(jsonPath("$.imageUrls[0]").value(CDN + "cover.jpg"))
                    .andExpect(jsonPath("$.aspectRatio").value(0.8))
                    .andExpect(jsonPath("$.likeLocked").value(false));
        }

        /** 팔로워 피드까지 닿아야 "새 게시물"이 의미를 가진다 — 게시 시점이 통지가 나가는 유일한 자리다(§24-3). */
        @Test
        @DisplayName("팔로워의 팔로잉 피드에도 곧바로 실린다")
        void publishedPostReachesFollowingFeed() throws Exception {
            Long postId = createPost("팔로워에게 먼저", "PUBLISH", List.of(image("a.jpg", 1080, 1350)));

            mockMvc.perform(get(FOLLOWING_FEED).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                    .andExpect(jsonPath("$.content[0].post.postId").value(postId))
                    .andExpect(jsonPath("$.content[0].post.isFollowing").value(true));
        }

        /** 임시저장은 <b>아무에게도</b> 보이지 않는다. 작성 도중의 글이 피드에 새면 §24-1이 무너진다. */
        @Test
        @DisplayName("임시저장은 소비자에게 없는 글이고, 게시해야 비로소 보인다")
        void draftIsInvisibleUntilPublished() throws Exception {
            Long postId = createPost("아직 다듬는 중", "DRAFT", List.of(image("draft.jpg", 1080, 1350)));

            mockMvc.perform(get(POST_DETAIL, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get(SHOWROOM_FEED, showroom.getId()).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(0));

            mockMvc.perform(post(CREATOR_PUBLISH, postId).header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(status().isOk());

            mockMvc.perform(get(POST_DETAIL, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.postId").value(postId));
        }

        /** 비로그인도 피드를 본다 — 좋아요 상태만 꺼져 있다(로그인 유도는 하트를 눌렀을 때다). */
        @Test
        @DisplayName("비로그인도 게시물을 볼 수 있고 좋아요 상태만 꺼져 있다")
        void anonymousCanRead() throws Exception {
            Long postId = createPost("누구나 보는 글", "PUBLISH", List.of(image("a.jpg", 1080, 1350)));

            mockMvc.perform(get(POST_DETAIL, postId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.postId").value(postId))
                    .andExpect(jsonPath("$.isLiked").value(false));
        }
    }

    @Nested
    @DisplayName("올린 뒤 고치면 소비자 화면도 따라온다")
    class EditsPropagate {

        /**
         * 사진 교체는 전체 교체다 — {@code (post_id, sort_order)} 유니크 때문에 (1,2) → (2,1)
         * 재배열이 한 플러시 안에서 충돌하지 않는지가 이 테스트의 진짜 대상이다. 단위 테스트는
         * 리포지토리를 흉내 내므로 이 제약을 볼 수 없다.
         */
        @Test
        @DisplayName("사진 순서를 뒤집어 저장해도 유니크 제약에 걸리지 않고 소비자 순서가 바뀐다")
        void reorderingImagesSurvivesUniqueConstraint() throws Exception {
            Long postId = createPost("순서를 바꿔 볼게요", "PUBLISH",
                    List.of(image("first.jpg", 1080, 1350), image("second.jpg", 1080, 1350)));

            updatePost(postId, "순서를 바꿔 볼게요", "PUBLISH",
                    List.of(image("second.jpg", 1080, 1350), image("first.jpg", 1080, 1350)))
                    .andExpect(status().isOk());

            mockMvc.perform(get(POST_DETAIL, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imageCount").value(2))
                    .andExpect(jsonPath("$.imageUrls[0]").value(CDN + "second.jpg"))
                    .andExpect(jsonPath("$.imageUrls[1]").value(CDN + "first.jpg"));
        }

        /** 사진을 줄이면 남은 것만 보여야 한다 — 지운 사진이 남으면 소비자 카드에 유령이 생긴다. */
        @Test
        @DisplayName("사진을 줄이면 소비자 쪽에서도 줄어든다")
        void removingImagesPropagates() throws Exception {
            Long postId = createPost("사진 정리", "PUBLISH", List.of(
                    image("a.jpg", 1080, 1350), image("b.jpg", 1080, 1350), image("c.jpg", 1080, 1350)));

            updatePost(postId, "사진 정리", "PUBLISH", List.of(image("b.jpg", 1080, 1350)))
                    .andExpect(status().isOk());

            mockMvc.perform(get(POST_DETAIL, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(jsonPath("$.imageCount").value(1))
                    .andExpect(jsonPath("$.imageUrls[0]").value(CDN + "b.jpg"));
        }

        /**
         * 게시중인 글은 임시저장으로 저장해도 계속 노출된다. 그래서 사진을 전부 뺀 저장을 허용하면
         * 소비자 피드에 사진 없는 카드가 남는다(§24-3).
         */
        @Test
        @DisplayName("게시중인 글에서 사진을 전부 빼는 저장은 거절되고 기존 사진이 그대로 남는다")
        void publishedPostCannotBecomeImageless() throws Exception {
            Long postId = createPost("사진은 지울 수 없다", "PUBLISH", List.of(image("a.jpg", 1080, 1350)));

            updatePost(postId, "본문만 남긴다", "DRAFT", List.of())
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get(POST_DETAIL, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("사진은 지울 수 없다"))
                    .andExpect(jsonPath("$.imageCount").value(1));
        }

        /** 삭제는 소비자에게 즉시 "없는 것"이 된다 — 보관은 서버 사정이지 화면의 사정이 아니다(§24-6). */
        @Test
        @DisplayName("삭제하면 상세는 404가 되고 피드에서도 빠진다")
        void deletedPostDisappearsFromConsumer() throws Exception {
            Long postId = createPost("곧 지울 글", "PUBLISH", List.of(image("a.jpg", 1080, 1350)));

            mockMvc.perform(delete(CREATOR_POST, postId).header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(POST_DETAIL, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get(SHOWROOM_FEED, showroom.getId()).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        }
    }

    @Nested
    @DisplayName("스튜디오 목록 — 탭 숫자와 목록이 같은 것을 센다")
    class StudioList {

        /**
         * 심사 중은 화면에서 여전히 「노출 중지」 탭에 머문다(§24-5). 탭 개수만 그렇게 세고 목록은
         * 정확히 SUSPENDED만 내려주면, 이의 신청을 넣은 순간 게시물이 자기 탭에서 사라지면서
         * 숫자만 남는다 — 인플루언서 입장에서는 글이 증발한 것으로 보인다.
         */
        @Test
        @DisplayName("이의 신청으로 심사 중이 된 글도 노출 중지 탭의 목록에 남는다")
        void underReviewPostStaysInSuspendedTab() throws Exception {
            Long postId = createPost("조치를 받은 글", "PUBLISH", List.of(image("a.jpg", 1080, 1350)));
            suspend(postId);
            submitAppeal(postId);

            mockMvc.perform(get(CREATOR_POSTS).param("status", "SUSPENDED")
                            .header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                    .andExpect(jsonPath("$.content[0].postId").value(postId))
                    .andExpect(jsonPath("$.content[0].status").value("UNDER_REVIEW"))
                    .andExpect(jsonPath("$.content[0].appealDeadline").exists());
        }

        /** 탭 숫자와 목록 건수가 어긋나면 어느 쪽도 믿을 수 없게 된다. */
        @Test
        @DisplayName("노출 중지 탭 숫자와 그 탭 목록의 건수가 일치한다")
        void suspendedTabCountMatchesItsList() throws Exception {
            Long suspended = createPost("중지된 글", "PUBLISH", List.of(image("a.jpg", 1080, 1350)));
            Long underReview = createPost("심사 중인 글", "PUBLISH", List.of(image("b.jpg", 1080, 1350)));
            suspend(suspended);
            suspend(underReview);
            submitAppeal(underReview);

            mockMvc.perform(get(CREATOR_POSTS).param("status", "SUSPENDED")
                            .header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(2))
                    .andExpect(jsonPath("$.statusCounts[" + TAB_SUSPENDED + "].status").value("SUSPENDED"))
                    .andExpect(jsonPath("$.statusCounts[" + TAB_SUSPENDED + "].count").value(2));
        }

        /** 목록 카드에는 제목이 없다 — 대표 사진과 본문 앞부분이 게시물을 알아보는 유일한 단서다(§24-1). */
        @Test
        @DisplayName("목록 카드는 대표 사진·장수·본문 미리보기로 게시물을 식별한다")
        void listCardCarriesThumbnailAndPreview() throws Exception {
            Long postId = createPost("대표 사진이 걸리는지", "PUBLISH",
                    List.of(image("cover.jpg", 1080, 1350), image("second.jpg", 1080, 1350)));

            mockMvc.perform(get(CREATOR_POSTS).header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].postId").value(postId))
                    .andExpect(jsonPath("$.content[0].thumbnailUrl").value(CDN + "cover.jpg"))
                    .andExpect(jsonPath("$.content[0].imageCount").value(2))
                    .andExpect(jsonPath("$.content[0].contentPreview").value("대표 사진이 걸리는지"))
                    .andExpect(jsonPath("$.statusCounts[" + TAB_ALL + "].label").value("전체"))
                    .andExpect(jsonPath("$.statusCounts[" + TAB_ALL + "].count").value(1));
        }

        /** 삭제된 글은 어느 탭에도 나타나지 않고 전체 개수에서도 빠진다(§24-6). */
        @Test
        @DisplayName("삭제한 글은 전체 탭 숫자에서도 빠진다")
        void deletedPostLeavesEveryTab() throws Exception {
            Long kept = createPost("남는 글", "PUBLISH", List.of(image("a.jpg", 1080, 1350)));
            Long removed = createPost("지울 글", "PUBLISH", List.of(image("b.jpg", 1080, 1350)));

            mockMvc.perform(delete(CREATOR_POST, removed).header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(CREATOR_POSTS).header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                    .andExpect(jsonPath("$.content[0].postId").value(kept))
                    .andExpect(jsonPath("$.statusCounts[" + TAB_ALL + "].count").value(1));
        }
    }

    @Nested
    @DisplayName("소비자의 반응이 크리에이터 화면 숫자로 돌아온다")
    class ConsumerReactions {

        /**
         * 좋아요는 소비자 화면의 상태이면서 동시에 인플루언서가 보는 성과 숫자다. 둘이 갈리면
         * 어느 쪽 숫자를 믿어야 할지 알 수 없어진다.
         */
        @Test
        @DisplayName("좋아요를 누르면 소비자 상세와 스튜디오 목록의 숫자가 함께 오른다")
        void likeIsReflectedOnBothSides() throws Exception {
            Long postId = createPost("좋아요를 받아 볼게요", "PUBLISH", List.of(image("a.jpg", 1080, 1350)));

            mockMvc.perform(post(POST_LIKE, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(POST_DETAIL, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(jsonPath("$.isLiked").value(true))
                    .andExpect(jsonPath("$.likeCount").value(1));
            mockMvc.perform(get(CREATOR_POSTS).header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(jsonPath("$.content[0].likeCount").value(1));
        }

        /** 취소도 같은 자리로 돌아와야 한다 — 한쪽만 되돌면 숫자가 영원히 어긋난 채로 남는다. */
        @Test
        @DisplayName("좋아요를 취소하면 양쪽 숫자가 함께 내려간다")
        void unlikeIsReflectedOnBothSides() throws Exception {
            Long postId = createPost("눌렀다 뗄 글", "PUBLISH", List.of(image("a.jpg", 1080, 1350)));

            mockMvc.perform(post(POST_LIKE, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete(POST_LIKE, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(POST_DETAIL, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(jsonPath("$.isLiked").value(false))
                    .andExpect(jsonPath("$.likeCount").value(0));
            mockMvc.perform(get(CREATOR_POSTS).header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(jsonPath("$.content[0].likeCount").value(0));
        }

        /**
         * 노출은 상세를 여는 것이 아니라 <b>뷰포트 진입</b>으로 센다(§24-7). 그래서 상세를 아무리
         * 열어도 숫자가 오르지 않고, 적재 API가 부르는 순간에만 오른다.
         */
        @Test
        @DisplayName("노출을 적재해야 노출 수가 오른다 — 상세를 여는 것만으로는 오르지 않는다")
        void impressionCountsOnlyWhenRecorded() throws Exception {
            Long postId = createPost("노출을 세어 볼게요", "PUBLISH", List.of(image("a.jpg", 1080, 1350)));

            mockMvc.perform(get(POST_DETAIL, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(jsonPath("$.impressionCount").value(0));

            mockMvc.perform(post(IMPRESSIONS)
                            .header(HttpHeaders.AUTHORIZATION, viewerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("postIds", List.of(postId)))))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(POST_DETAIL, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(jsonPath("$.impressionCount").value(1));
            mockMvc.perform(get(CREATOR_POSTS).header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(jsonPath("$.content[0].impressionCount").value(1));
        }

        /** 같은 사람이 스크롤을 오르내리며 같은 카드를 다시 보는 일이 흔하다 — 30분 세션으로 접는다(§24-7). */
        @Test
        @DisplayName("같은 사람의 재노출은 30분 세션 안에서 한 번만 센다")
        void repeatedImpressionIsFoldedIntoOneSession() throws Exception {
            Long postId = createPost("두 번 스쳐 갈 글", "PUBLISH", List.of(image("a.jpg", 1080, 1350)));
            String body = toJson(Map.of("postIds", List.of(postId)));

            for (int i = 0; i < 2; i++) {
                mockMvc.perform(post(IMPRESSIONS)
                                .header(HttpHeaders.AUTHORIZATION, viewerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isNoContent());
            }

            mockMvc.perform(get(POST_DETAIL, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(jsonPath("$.impressionCount").value(1));
        }
    }

    @Nested
    @DisplayName("남의 쇼룸에는 손대지 못한다")
    class Ownership {

        @Test
        @DisplayName("다른 크리에이터의 게시물은 수정도 삭제도 할 수 없다")
        void othersPostIsUntouchable() throws Exception {
            Long postId = createPost("내 글", "PUBLISH", List.of(image("a.jpg", 1080, 1350)));

            Creator other = createShowroom("소연의 살림", "soyeon");
            String otherToken = bearerToken(
                    other.getUser().getUsername(), RoleType.CREATOR, other.getUser().getId());

            mockMvc.perform(put(CREATOR_POST, postId)
                            .header(HttpHeaders.AUTHORIZATION, otherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(savePayload("가로채기", "PUBLISH", List.of(image("x.jpg", 1080, 1350))))))
                    .andExpect(status().isForbidden());

            mockMvc.perform(delete(CREATOR_POST, postId).header(HttpHeaders.AUTHORIZATION, otherToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get(POST_DETAIL, postId).header(HttpHeaders.AUTHORIZATION, viewerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("내 글"));
        }

        /** 게시물은 크리에이터 소유 콘텐츠다 — 소비자 토큰으로는 스튜디오가 아예 열리지 않는다. */
        @Test
        @DisplayName("소비자 토큰으로는 게시물을 올릴 수 없다")
        void consumerCannotPost() throws Exception {
            mockMvc.perform(post(CREATOR_POSTS)
                            .header(HttpHeaders.AUTHORIZATION, viewerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(savePayload("소비자가 쓴 글", "PUBLISH",
                                    List.of(image("a.jpg", 1080, 1350))))))
                    .andExpect(status().isForbidden());
        }
    }

    // ------------------------------------------------------------------ 스텝

    private Long createPost(String content, String action, List<Map<String, Object>> images) throws Exception {
        String body = mockMvc.perform(post(CREATOR_POSTS)
                        .header(HttpHeaders.AUTHORIZATION, creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(savePayload(content, action, images))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("postId").asLong();
    }

    private ResultActions updatePost(Long postId, String content, String action, List<Map<String, Object>> images)
            throws Exception {
        return mockMvc.perform(put(CREATOR_POST, postId)
                .header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(savePayload(content, action, images))));
    }

    private void submitAppeal(Long postId) throws Exception {
        mockMvc.perform(post(CREATOR_APPEAL, postId)
                        .header(HttpHeaders.AUTHORIZATION, creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("content", "광고 표시를 넣어 두었습니다"))))
                .andExpect(status().isCreated());
    }

    /** 운영자 조치는 어드민 API의 몫이라 여기서는 결과 상태만 만들어 둔다. */
    private void suspend(Long postId) {
        Post post = postRepository.findById(postId).orElseThrow();
        post.suspend();
        postRepository.save(post);

        LocalDateTime now = LocalDateTime.now();
        postSuspensionRepository.save(new PostSuspension(
                post, PostSuspensionReason.AD_DISCLOSURE, "대가관계 표시가 없습니다",
                "운영정책 제12조 3항", 1L, now, now.plusDays(7)));
    }

    // ------------------------------------------------------------------ 픽스처

    private static Map<String, Object> savePayload(String content, String action, List<Map<String, Object>> images) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", content);
        payload.put("images", images);
        payload.put("action", action);
        return payload;
    }

    private static Map<String, Object> image(String name, int width, int height) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("imageUrl", CDN + name);
        image.put("originalUrl", CDN + "origin-" + name);
        image.put("width", width);
        image.put("height", height);
        image.put("fileSize", 2_048_000);
        return image;
    }

    private Users createUser(String username, String nickname) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(new Users(
                username, nickname, username + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now));
    }

    private Creator createShowroom(String showroomName, String handle) {
        Users owner = createUser("creator-" + handle, showroomName);
        return creatorRepository.save(Creator.builder()
                .user(owner)
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/" + handle)
                .accountId(handle)
                .followerCount(1000)
                .businessEmail(handle + "@showroomz.test")
                .showroomName(showroomName)
                .build());
    }
}
