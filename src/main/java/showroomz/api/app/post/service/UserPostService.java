package showroomz.api.app.post.service;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.post.DTO.PostDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.groupbuy.service.GroupBuyPostCard;
import showroomz.domain.groupbuy.service.GroupBuyPostCardLoader;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.repository.PublicShowrooms;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostImage;
import showroomz.domain.post.entity.PostLike;
import showroomz.domain.post.policy.PostPolicies;
import showroomz.domain.post.repository.PostImageRepository;
import showroomz.domain.post.repository.PostLikeRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.type.LikedPostSort;
import showroomz.domain.post.type.PostType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static showroomz.domain.member.creator.entity.QCreator.creator;
import static showroomz.domain.member.user.entity.QUsers.users;

/**
 * 소비자에게 게시물을 보여주는 서비스.
 *
 * <p>경로와 응답 구조는 <b>계약</b>이라 그대로 두고 안쪽만 §24에 맞췄다. 바뀐 것은 세 가지다 —
 * 노출 조건이 {@code isDisplay}에서 <b>게시중 상태</b>로, 위시리스트 용어가 <b>좋아요</b>로,
 * 그리고 응답에 <b>비율</b>이 실린다.
 *
 * <p>상세 조회에서 조회수를 올리지 않는다. 노출은 이제 뷰포트 진입을 기준으로
 * {@link PostImpressionService}가 적재한다 — 상세를 열 때마다 세면 피드에서 스쳐 지나간 노출과
 * 상세를 연 노출이 같은 지표에 뒤섞인다.
 *
 * <p>공구 게시물은 {@code groupBuy} 블록을 더해 같은 응답으로 나간다(공구 게시물 설계 5-2). 블록은
 * {@link GroupBuyPostCardLoader}가 페이지 단위로 한 번에 읽는다 — 페이지 크기와 무관하게 쿼리 3회다(6-2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPostService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostImageRepository postImageRepository;
    private final CreatorFollowRepository creatorFollowRepository;
    private final CreatorRepository creatorRepository;
    private final UserRepository userRepository;
    private final ConnectionRepository connectionRepository;
    private final PostPolicies postPolicies;
    private final GroupBuyPostCardLoader groupBuyPostCardLoader;
    private final JPAQueryFactory queryFactory;

    /**
     * C5 상세. 공구 게시물은 마감이어도 종료 후 3일까지 열리고(투영식이 그때까지 PUBLISHED로 둔다), 이후 404다.
     * 목록과 달리 마감이어도 상품 행을 전부 내린다 — 흑백 + 「공구 마감」으로 그린다(5-2).
     */
    public PostDto.PostDetailResponse getPostById(String username, Long postId) {
        Post post = postRepository.findByIdWithImages(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // 게시중이 아닌 게시물은 소비자에게 "없는 것"이다 — 작성중·노출 중지·삭제를 구분해 알려주지 않는다
        if (!post.isVisibleToConsumer()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        Users user = findUser(username);
        boolean liked = user != null && postLikeRepository.existsByUserIdAndPostId(user.getId(), postId);

        GroupBuyPostCard card = groupBuyCards(List.of(post)).get(post.getId());

        Creator showroom = post.getCreator();
        return PostDto.PostDetailResponse.builder()
                .contentType(post.getPostType().name())
                .postId(post.getId())
                .showroomId(showroom.getId())
                .showroomName(showroomName(showroom))
                .showroomImageUrl(showroom.getProfileImageUrl())
                .content(post.getContent())
                .imageUrls(post.getImages().stream().map(PostImage::getImageUrl).toList())
                .imageCount(post.getImageCount())
                .aspectRatio(post.getAspectRatio())
                .impressionCount(post.getImpressionCount())
                .isLiked(liked)
                .likeCount(post.getLikeCount())
                .likeLocked(likeLocked(post, card))
                .publishedAt(post.getPublishedAt())
                .modifiedAt(post.getModifiedAt())
                .groupBuy(card == null ? null : PostDto.GroupBuyBlock.of(card, true))
                .build();
    }

    /**
     * C4 「진행 중인 공구」 고정 섹션 — 이 쇼룸의 진행 중 공구 게시물 전부(페이징 없음 · 공구 시작일 최신순).
     *
     * <p>빈 배열이면 앱이 섹션을 감춘다. 정의가 아바타 링({@code hasOngoingGroupBuy})과 같아 둘이 어긋나지 않는다(4-5).
     * 없는·노출할 수 없는 쇼룸은 쇼룸 프로필({@code GET /{showroomId}})과 같은 404다.
     */
    public List<PostDto.FeedItemResponse> getOngoingGroupBuyPosts(String username, Long showroomId) {
        requireVisibleShowroom(showroomId);
        List<Post> posts = postRepository.findOngoingGroupBuyPostsByCreatorId(showroomId);

        Users user = findUser(username);
        FeedContext context = feedContext(posts, likedPostIds(user, posts), followedCreatorIds(user, posts));
        return posts.stream().map(post -> toFeedItem(post, context)).toList();
    }

    public PageResponse<PostDto.FeedItemResponse> getPostList(String username, PagingRequest pagingRequest, Long showroomId) {
        Pageable pageable = pagingRequest.toPageable();
        Page<Post> postPage = showroomId != null
                ? postRepository.findDisplayedPostsByCreatorId(showroomId, pageable)
                : postRepository.findDisplayedPosts(pageable);

        Users user = findUser(username);
        return toFeed(postPage, likedPostIds(user, postPage.getContent()),
                followedCreatorIds(user, postPage.getContent()));
    }

    public PageResponse<PostDto.FeedItemResponse> getFollowingFeed(String username, PagingRequest pagingRequest) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<Long> followingShowroomIds = creatorFollowRepository.findCreatorIdsByUserId(user.getId());
        Pageable pageable = pagingRequest.toPageable();

        // 팔로잉이 0이면 이 피드는 정의상 비어 있다 — C1 빈 상태는 추천 피드가 채운다
        if (followingShowroomIds.isEmpty()) {
            return new PageResponse<>(Page.empty(pageable));
        }

        Page<Post> postPage = postRepository.findDisplayedPostsByCreatorIds(followingShowroomIds, pageable);

        // 이 목록은 정의상 전부 팔로우 중인 쇼룸이다 — 다시 물어볼 필요가 없다
        return toFeed(postPage, likedPostIds(user, postPage.getContent()), Set.copyOf(followingShowroomIds));
    }

    /**
     * C1 "회원님을 위한 추천" — 팔로우하지 않은 쇼룸의 게시물 (§C1 추천 영역).
     *
     * <p>팔로잉 피드와 <b>겹치지 않게</b> 팔로우 중인 쇼룸을 통째로 뺀다. 화면이 "새 게시물을 모두
     * 확인했어요" 구분선으로 두 영역을 끊어 두는데, 겹치는 게시물이 있으면 그 선이 거짓말이 된다.
     *
     * <p>본인 쇼룸도 뺀다 — 크리에이터에게 자기 게시물을 추천하는 것은 발견이 아니다.
     *
     * <p>팔로잉이 0인 사용자에게는 이 응답이 그대로 <b>발견 피드</b>가 된다(C1 빈 상태). 쇼룸 이름만
     * 나열하는 목록으로는 팔로우를 결정할 근거가 없어서, 빈 상태에도 게시물을 그대로 보여준다.
     *
     * <p><b>비로그인도 본다.</b> 팔로우도 본인 쇼룸도 없으니 뺄 것이 없어 게시중 게시물 전체가
     * 그대로 발견 피드가 되고, {@code isLiked}·{@code isFollowing}은 전부 false로 나간다.
     */
    public PageResponse<PostDto.FeedItemResponse> getRecommendedFeed(String username, PagingRequest pagingRequest) {
        Users user = findFeedUser(username);

        // 비로그인은 뺄 쇼룸이 없다 — 제외 목록이 비면 게시중 게시물 전체가 후보다
        List<Long> excluded = new ArrayList<>();
        if (user != null) {
            excluded.addAll(creatorFollowRepository.findCreatorIdsByUserId(user.getId()));
            creatorRepository.findByUser_Id(user.getId()).ifPresent(creator -> excluded.add(creator.getId()));
        }

        Page<Post> postPage = postRepository.findRecommendedPosts(excluded, pagingRequest.toPageable());

        // 제외 조건이 곧 미팔로우 보장이라 팔로우 여부를 다시 묻지 않는다 — 전부 false다
        return toFeed(postPage, likedPostIds(user, postPage.getContent()), Set.of());
    }

    /**
     * 좋아요한 게시물 모음 (C3).
     *
     * <p>그 사이 내려간 게시물은 목록에서 빠지지만, <b>마감된 공구는 종료 후 3일 동안 남는다</b>. 살 수 없게
     * 됐다고 곧바로 사용자가 저장한 기록을 지우지 않고, 대신 {@code likeLocked}로 새 좋아요만 막는다(C3 §마감·품절).
     * 3일이 지나면 투영이 DRAFT로 내려 같은 {@code PUBLISHED} 조건이 걸러 낸다(공구 게시물 설계 4-1).
     *
     * <p>화면 상단의 "좋아요한 게시물 N"은 {@code pageInfo.totalResults}다 — 페이지에 담긴
     * 수가 아니라 전체 수라 스크롤 위치와 무관하게 같은 값이 나온다.
     */
    public PageResponse<PostDto.FeedItemResponse> getLikedPosts(
            String username, LikedPostSort sort, PagingRequest pagingRequest) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 정렬 키가 post_like에 있어 Pageable의 정렬은 쓰지 않는다 — 쿼리가 직접 순서를 잡는다
        Page<Post> postPage = postLikeRepository.findLikedPostsByUserId(
                user.getId(), sort, pagingRequest.toPageable(Sort.unsorted()));
        Set<Long> allLiked = postPage.getContent().stream().map(Post::getId).collect(Collectors.toSet());

        return toFeed(postPage, allLiked, followedCreatorIds(user, postPage.getContent()));
    }

    @Transactional
    public void likePost(String username, Long postId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // 좋아요 가능 여부는 타입별 규칙이다 — 공구 게시물은 마감이면 새 좋아요를 받지 않는다(C5)
        if (!postPolicies.of(post).canLike(post)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        // 멱등성: 이미 눌러 뒀으면 성공(204)으로 끝낸다
        if (postLikeRepository.existsByUserIdAndPostId(user.getId(), postId)) {
            return;
        }

        postLikeRepository.save(new PostLike(user, post));
        post.increaseLikeCount();
    }

    @Transactional
    public void unlikePost(String username, Long postId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // 멱등성: 누른 적이 없으면 그대로 성공으로 끝낸다
        if (!postLikeRepository.existsByUserIdAndPostId(user.getId(), postId)) {
            return;
        }

        postLikeRepository.deleteByUserIdAndPostId(user.getId(), postId);
        post.decreaseLikeCount();
    }

    // ------------------------------------------------------------------ 내부

    private PageResponse<PostDto.FeedItemResponse> toFeed(
            Page<Post> postPage, Set<Long> likedPostIds, Set<Long> followedCreatorIds) {
        FeedContext context = feedContext(postPage.getContent(), likedPostIds, followedCreatorIds);
        return new PageResponse<>(postPage.map(post -> toFeedItem(post, context)));
    }

    /** 페이지 단위로 모아 읽은 것 — 사진 · 로즈 링 · 공구 블록. 카드마다 묻지 않는다. */
    private FeedContext feedContext(List<Post> posts, Set<Long> likedPostIds, Set<Long> followedCreatorIds) {
        return new FeedContext(imagesByPost(posts), ongoingGroupBuyCreatorIds(posts), groupBuyCards(posts),
                likedPostIds, followedCreatorIds);
    }

    private record FeedContext(
            Map<Long, List<String>> imagesByPost,
            Set<Long> ongoingGroupBuyCreatorIds,
            Map<Long, GroupBuyPostCard> groupBuyCards,
            Set<Long> likedPostIds,
            Set<Long> followedCreatorIds
    ) {
    }

    /**
     * 목록 카드. 공구 게시물이 마감이면 상품 행을 싣지 않는다 — C3·C4는 마감 게시물을 글만 보여준다(5-2).
     * {@code productCount}는 그래도 실제 개수다.
     */
    private PostDto.FeedItemResponse toFeedItem(Post post, FeedContext context) {
        List<String> imageUrls = context.imagesByPost().getOrDefault(post.getId(), List.of());
        GroupBuyPostCard card = context.groupBuyCards().get(post.getId());
        Creator showroom = post.getCreator();
        PostDto.PostListItem item = PostDto.PostListItem.builder()
                .postId(post.getId())
                .showroomId(showroom.getId())
                .showroomName(showroomName(showroom))
                .showroomImageUrl(showroom.getProfileImageUrl())
                .isFollowing(context.followedCreatorIds().contains(showroom.getId()))
                .hasOngoingGroupBuy(context.ongoingGroupBuyCreatorIds().contains(showroom.getId()))
                .content(post.getContent())
                .imageUrls(imageUrls)
                .imageCount(imageUrls.size())
                .aspectRatio(post.getAspectRatio())
                .impressionCount(post.getImpressionCount())
                .isLiked(context.likedPostIds().contains(post.getId()))
                .likeCount(post.getLikeCount())
                .likeLocked(likeLocked(post, card))
                .publishedAt(post.getPublishedAt())
                .groupBuy(card == null ? null : PostDto.GroupBuyBlock.of(card, !card.saleState().isClosed()))
                .build();
        return PostDto.FeedItemResponse.builder()
                .contentType(post.getPostType().name())
                .post(item)
                .build();
    }

    /**
     * 페이지의 공구 게시물 블록 — 일반 게시물만 있으면 쿼리를 내지 않는다.
     */
    private Map<Long, GroupBuyPostCard> groupBuyCards(List<Post> posts) {
        List<Long> groupBuyPostIds = posts.stream()
                .filter(post -> post.getPostType() == PostType.GROUP_BUY)
                .map(Post::getId)
                .toList();
        return groupBuyPostCardLoader.load(groupBuyPostIds, LocalDateTime.now());
    }

    /**
     * 하트 잠금 — 공구는 로더가 읽은 판매 상태로 판정한다. 게시물마다 {@code canLike}를 부르면 카드 수만큼
     * {@code findById}가 나간다(6-3). 쓰기(좋아요 {@code POST})는 계속 정책이 판정한다.
     */
    private boolean likeLocked(Post post, GroupBuyPostCard card) {
        if (post.getPostType() == PostType.GROUP_BUY) {
            return card == null || card.likeLocked();
        }
        return !postPolicies.of(post).canLike(post);
    }

    /** 쇼룸 프로필과 같은 노출 조건 — 상태를 구분해 알려주지 않는다. */
    private void requireVisibleShowroom(Long showroomId) {
        Integer found = queryFactory
                .selectOne()
                .from(creator)
                .join(creator.user, users)
                .where(PublicShowrooms.visible().and(creator.id.eq(showroomId)))
                .fetchFirst();
        if (found == null) {
            throw new BusinessException(ErrorCode.SHOWROOM_NOT_FOUND);
        }
    }

    private Set<Long> likedPostIds(Users user, List<Post> posts) {
        if (user == null || posts.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        return Set.copyOf(postLikeRepository.findLikedPostIdsByUserIdAndPostIds(user.getId(), postIds));
    }

    /**
     * C1 아바타 로즈 링 — 페이지에 실린 쇼룸 중 진행 중인 공구를 가진 쇼룸.
     *
     * <p>게시물마다 묻지 않고 페이지의 쇼룸을 한 번에 대조한다. 링은 <b>쇼룸</b>의 상태라 같은
     * 쇼룸의 게시물이 여러 장 실려도 답은 하나다.
     */
    private Set<Long> ongoingGroupBuyCreatorIds(List<Post> posts) {
        if (posts.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> creatorIds = posts.stream().map(post -> post.getCreator().getId()).distinct().toList();
        return Set.copyOf(connectionRepository.findCreatorIdsWithOngoingGroupBuy(creatorIds));
    }

    /** 페이지에 실린 쇼룸만 대조한다 — 팔로잉이 많은 사용자에게 전체 목록을 읽게 하지 않는다 */
    private Set<Long> followedCreatorIds(Users user, List<Post> posts) {
        if (user == null || posts.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> creatorIds = posts.stream().map(post -> post.getCreator().getId()).distinct().toList();
        return Set.copyOf(creatorFollowRepository.findFollowedCreatorIds(user.getId(), creatorIds));
    }

    private Users findUser(String username) {
        return username == null ? null : userRepository.findByUsername(username).orElse(null);
    }

    /**
     * 비로그인은 허용하되 <b>깨진 토큰은 허용하지 않는</b> 조회용 사용자 해석.
     *
     * <p>{@link #findUser}처럼 없는 사용자를 조용히 null로 떨어뜨리면 탈퇴한 계정의 토큰이
     * 비로그인처럼 통과해 버린다. 토큰이 실려 왔는데 그 사용자가 없으면 404다.
     */
    private Users findFeedUser(String username) {
        if (username == null) {
            return null;
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /** 피드 한 페이지의 사진을 한 번에 읽어 게시물별로 묶는다 — 게시물마다 지연 로딩하면 쿼리가 페이지 크기만큼 늘어난다 */
    private Map<Long, List<String>> imagesByPost(List<Post> posts) {
        if (posts.isEmpty()) {
            return Map.of();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        return postImageRepository.findByPostIdsOrdered(postIds).stream()
                .collect(Collectors.groupingBy(
                        image -> image.getPost().getId(),
                        Collectors.mapping(PostImage::getImageUrl, Collectors.toList())));
    }

    /**
     * §22-1 — 소비자에게 보이는 이름은 <b>쇼룸명</b>이다. 앱 계정 닉네임은 개인 소비 계정의 이름이라
     * 판매 채널의 간판으로 쓰지 않는다. 쇼룸명이 아직 없는 가입 도중 상태에서만 닉네임으로 떨어진다.
     */
    private static String showroomName(Creator creator) {
        return creator.getShowroomName() != null
                ? creator.getShowroomName()
                : creator.getUser().getNickname();
    }

}
