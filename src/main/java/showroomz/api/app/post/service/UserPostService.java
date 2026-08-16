package showroomz.api.app.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.post.DTO.PostDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostImage;
import showroomz.domain.post.entity.PostLike;
import showroomz.domain.post.policy.PostPolicies;
import showroomz.domain.post.repository.PostImageRepository;
import showroomz.domain.post.repository.PostLikeRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final UserRepository userRepository;
    private final PostPolicies postPolicies;

    public PostDto.PostDetailResponse getPostById(String username, Long postId) {
        Post post = postRepository.findByIdWithImages(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // 게시중이 아닌 게시물은 소비자에게 "없는 것"이다 — 작성중·노출 중지·삭제를 구분해 알려주지 않는다
        if (!post.isVisibleToConsumer()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        Users user = findUser(username);
        boolean liked = user != null && postLikeRepository.existsByUserIdAndPostId(user.getId(), postId);

        Creator creator = post.getCreator();
        return PostDto.PostDetailResponse.builder()
                .postId(post.getId())
                .showroomId(creator.getId())
                .showroomName(showroomName(creator))
                .showroomImageUrl(creator.getProfileImageUrl())
                .content(post.getContent())
                .imageUrls(post.getImages().stream().map(PostImage::getImageUrl).toList())
                .imageCount(post.getImageCount())
                .aspectRatio(post.getAspectRatio())
                .impressionCount(post.getImpressionCount())
                .isLiked(liked)
                .likeCount(post.getLikeCount())
                .publishedAt(post.getPublishedAt())
                .modifiedAt(post.getModifiedAt())
                .build();
    }

    public PageResponse<PostDto.FeedItemResponse> getPostList(String username, PagingRequest pagingRequest, Long showroomId) {
        Pageable pageable = pagingRequest.toPageable();
        Page<Post> postPage = showroomId != null
                ? postRepository.findDisplayedPostsByCreatorId(showroomId, pageable)
                : postRepository.findDisplayedPosts(pageable);

        return toFeed(postPage, likedPostIds(findUser(username), postPage.getContent()));
    }

    public PageResponse<PostDto.FeedItemResponse> getFollowingFeed(String username, PagingRequest pagingRequest) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<Long> followingShowroomIds = creatorFollowRepository.findCreatorIdsByUserId(user.getId());
        Pageable pageable = pagingRequest.toPageable();

        if (followingShowroomIds.isEmpty()) {
            return new PageResponse<>(Page.empty(pageable));
        }

        Page<Post> postPage = postRepository.findDisplayedPostsByFollowingCreatorIds(followingShowroomIds, pageable);
        return toFeed(postPage, likedPostIds(user, postPage.getContent()));
    }

    /** 좋아요한 게시물 모음 — 그 사이 내려간 게시물은 목록에서 빠진다 */
    public PageResponse<PostDto.FeedItemResponse> getLikedPosts(String username, PagingRequest pagingRequest) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Page<Post> postPage = postLikeRepository.findLikedPostsByUserId(user.getId(), pagingRequest.toPageable());
        Set<Long> allLiked = postPage.getContent().stream().map(Post::getId).collect(Collectors.toSet());

        return toFeed(postPage, allLiked);
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

    private PageResponse<PostDto.FeedItemResponse> toFeed(Page<Post> postPage, Set<Long> likedPostIds) {
        Map<Long, List<String>> imagesByPost = imagesByPost(postPage.getContent());

        Page<PostDto.FeedItemResponse> dtoPage = postPage.map(post -> {
            List<String> imageUrls = imagesByPost.getOrDefault(post.getId(), List.of());
            Creator creator = post.getCreator();
            PostDto.PostListItem item = PostDto.PostListItem.builder()
                    .postId(post.getId())
                    .showroomId(creator.getId())
                    .showroomName(showroomName(creator))
                    .showroomImageUrl(creator.getProfileImageUrl())
                    .content(post.getContent())
                    .imageUrls(imageUrls)
                    .imageCount(imageUrls.size())
                    .aspectRatio(post.getAspectRatio())
                    .impressionCount(post.getImpressionCount())
                    .isLiked(likedPostIds.contains(post.getId()))
                    .likeCount(post.getLikeCount())
                    .publishedAt(post.getPublishedAt())
                    .build();
            return PostDto.FeedItemResponse.builder()
                    .contentType(post.getPostType().name())
                    .post(item)
                    .build();
        });
        return new PageResponse<>(dtoPage);
    }

    private Set<Long> likedPostIds(Users user, List<Post> posts) {
        if (user == null || posts.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        return Set.copyOf(postLikeRepository.findLikedPostIdsByUserIdAndPostIds(user.getId(), postIds));
    }

    private Users findUser(String username) {
        return username == null ? null : userRepository.findByUsername(username).orElse(null);
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
