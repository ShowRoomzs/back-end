package showroomz.api.app.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.post.DTO.PostDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostWishlist;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.repository.PostWishlistRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserPostService {

    private final PostRepository postRepository;
    private final PostWishlistRepository postWishlistRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PostDto.PostDetailResponse getPostById(String username, Long postId) {
        // 1. Post 조회
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // 2. 전시 여부 확인
        if (!post.getIsDisplay()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        // 3. 조회수 증가
        post.incrementViewCount();

        // 4. 위시리스트 여부 확인 (로그인 사용자만)
        Boolean isWishlisted = false;
        if (username != null) {
            Users user = userRepository.findByUsername(username)
                    .orElse(null);
            if (user != null) {
                isWishlisted = postWishlistRepository.existsByUserIdAndPostId(user.getId(), postId);
            }
        }

        // 5. Response 생성
        Market market = post.getMarket();
        return PostDto.PostDetailResponse.builder()
                .postId(post.getId())
                .marketId(market.getId())
                .marketName(market.getMarketName())
                .marketImageUrl(market.getMarketImageUrl())
                .title(post.getTitle())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .viewCount(post.getViewCount())
                .isWishlisted(isWishlisted)
                .createdAt(post.getCreatedAt())
                .modifiedAt(post.getModifiedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<PostDto.PostListItem> getPostList(String username, Integer page, Integer limit, Long marketId) {
        // 1. Pageable 생성
        int pageNum = page != null ? page : 0;
        int limitNum = limit != null ? limit : 20;
        Pageable pageable = PageRequest.of(pageNum, limitNum, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 2. Post 목록 조회
        Page<Post> postPage;
        if (marketId != null) {
            postPage = postRepository.findDisplayedPostsByMarketId(marketId, pageable);
        } else {
            postPage = postRepository.findDisplayedPosts(pageable);
        }

        // 3. 위시리스트 여부 확인 (로그인 사용자만)
        Map<Long, Boolean> wishlistMap = Map.of();
        if (username != null) {
            Users user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                List<Long> postIds = postPage.getContent().stream()
                        .map(Post::getId)
                        .collect(Collectors.toList());
                
                List<Long> wishlistedPostIds = postWishlistRepository
                        .findWishlistedPostIdsByUserIdAndPostIds(user.getId(), postIds);
                
                wishlistMap = wishlistedPostIds.stream()
                        .collect(Collectors.toMap(id -> id, id -> true));
            }
        }

        // 4. DTO 변환
        final Map<Long, Boolean> finalWishlistMap = wishlistMap;
        Page<PostDto.PostListItem> dtoPage = postPage.map(post -> {
            Market market = post.getMarket();
            return PostDto.PostListItem.builder()
                    .postId(post.getId())
                    .marketId(market.getId())
                    .marketName(market.getMarketName())
                    .marketImageUrl(market.getMarketImageUrl())
                    .title(post.getTitle())
                    .imageUrl(post.getImageUrl())
                    .viewCount(post.getViewCount())
                    .isWishlisted(finalWishlistMap.getOrDefault(post.getId(), false))
                    .createdAt(post.getCreatedAt())
                    .build();
        });

        return new PageResponse<>(dtoPage);
    }

    public void addPostToWishlist(String username, Long postId) {
        // 1. User 조회
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. Post 조회
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // 3. 전시 여부 확인
        if (!post.getIsDisplay()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        // 4. 이미 위시리스트에 있는지 확인
        if (postWishlistRepository.existsByUserIdAndPostId(user.getId(), postId)) {
            throw new BusinessException(ErrorCode.WISHLIST_ALREADY_EXISTS);
        }

        // 5. 위시리스트에 추가
        PostWishlist postWishlist = new PostWishlist(user, post);
        postWishlistRepository.save(postWishlist);
    }

    public void removePostFromWishlist(String username, Long postId) {
        // 1. User 조회
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 위시리스트에서 삭제
        postWishlistRepository.deleteByUserIdAndPostId(user.getId(), postId);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostDto.PostListItem> getWishlistedPosts(String username, Integer page, Integer limit) {
        // 1. User 조회
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. Pageable 생성
        int pageNum = page != null ? page : 0;
        int limitNum = limit != null ? limit : 20;
        Pageable pageable = PageRequest.of(pageNum, limitNum);

        // 3. 위시리스트 조회
        Page<Post> postPage = postWishlistRepository.findWishlistedPostsByUserId(user.getId(), pageable);

        // 4. DTO 변환 (모두 위시리스트에 있으므로 isWishlisted = true)
        Page<PostDto.PostListItem> dtoPage = postPage.map(post -> {
            Market market = post.getMarket();
            return PostDto.PostListItem.builder()
                    .postId(post.getId())
                    .marketId(market.getId())
                    .marketName(market.getMarketName())
                    .marketImageUrl(market.getMarketImageUrl())
                    .title(post.getTitle())
                    .imageUrl(post.getImageUrl())
                    .viewCount(post.getViewCount())
                    .isWishlisted(true)
                    .createdAt(post.getCreatedAt())
                    .build();
        });

        return new PageResponse<>(dtoPage);
    }
}
