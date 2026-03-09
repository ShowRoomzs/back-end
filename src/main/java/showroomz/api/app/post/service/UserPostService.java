package showroomz.api.app.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.post.DTO.PostDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketFollowRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostProduct;
import showroomz.domain.post.entity.PostWishlist;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.repository.PostWishlistRepository;
import showroomz.domain.product.entity.Product;
import showroomz.domain.review.repository.ReviewRepository;
import showroomz.domain.wishlist.repository.WishlistRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserPostService {

    private final PostRepository postRepository;
    private final PostWishlistRepository postWishlistRepository;
    private final MarketFollowRepository marketFollowRepository;
    private final UserRepository userRepository;
    private final WishlistRepository wishlistRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public PostDto.PostDetailResponse getPostById(String username, Long postId) {
        // 1. Post 조회 (등록 상품 목록 포함, N+1 방지)
        Post post = postRepository.findByIdWithPostProductsAndProducts(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // 2. 전시 여부 확인
        if (!post.getIsDisplay()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        // 3. 조회수 증가
        post.incrementViewCount();

        // 4. 위시리스트 여부 확인 (로그인 사용자만)
        Boolean isPostWishlisted = false;
        Users user = null;
        if (username != null) {
            user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                isPostWishlisted = postWishlistRepository.existsByUserIdAndPostId(user.getId(), postId);
            }
        }

        // 5. 단일 포스트에 대한 상품 ID 목록 추출 및 일괄 조회
        List<Long> productIds = post.getPostProducts().stream()
                .map(pp -> pp.getProduct().getProductId())
                .collect(Collectors.toList());

        Map<Long, Long> wishlistCountMap = toWishlistCountMap(productIds);
        Map<Long, Long> reviewCountMap = toReviewCountMap(productIds);
        Set<Long> wishedProductIds = user != null && !productIds.isEmpty()
                ? wishlistRepository.findProductIdsWishedByUserAndProductIdIn(user.getId(), productIds)
                : Collections.emptySet();

        // 6. 포스트에 등록된 상품 목록 DTO 변환 (메모리 매핑)
        List<PostDto.PostProductResponse> registeredProducts = buildRegisteredProducts(
                post, wishlistCountMap, reviewCountMap, wishedProductIds);

        // 7. Response 생성
        Market market = post.getMarket();
        return PostDto.PostDetailResponse.builder()
                .postId(post.getId())
                .showroomId(market.getId())
                .showroomName(market.getMarketName())
                .showroomImageUrl(market.getMarketImageUrl())
                .title(post.getTitle())
                .content(post.getContent())
                .imageUrls(post.getImageUrls())
                .viewCount(post.getViewCount())
                .isWishlisted(isPostWishlisted)
                .wishlistCount(post.getWishlistCount())
                .registeredProducts(registeredProducts)
                .createdAt(post.getCreatedAt())
                .modifiedAt(post.getModifiedAt())
                .build();
    }

    private Map<Long, Long> toWishlistCountMap(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Object[]> rows = wishlistRepository.countWishlistByProductIds(productIds);
        return rows.stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));
    }

    private Map<Long, Long> toReviewCountMap(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Object[]> rows = reviewRepository.countByProductIds(productIds);
        return rows.stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));
    }

    private Integer calculateDiscountRate(Integer regularPrice, Integer salePrice) {
        if (regularPrice == null || salePrice == null || regularPrice <= 0) {
            return 0;
        }
        double rate = ((double) (regularPrice - salePrice) / regularPrice) * 100.0;
        int rounded = (int) Math.round(rate);
        return Math.max(0, Math.min(rounded, 100));
    }

    @Transactional(readOnly = true)
    public PageResponse<PostDto.FeedItemResponse> getPostList(String username, PagingRequest pagingRequest, Long showroomId) {
        Pageable pageable = pagingRequest.toPageable();

        // 2. Post 목록 조회
        Page<Post> postPage;
        if (showroomId != null) {
            postPage = postRepository.findDisplayedPostsByMarketId(showroomId, pageable);
        } else {
            postPage = postRepository.findDisplayedPosts(pageable);
        }

        // 3. 위시리스트 여부 확인 (로그인 사용자만)
        Map<Long, Boolean> wishlistMap = Map.of();
        Users user = null;
        if (username != null) {
            user = userRepository.findByUsername(username).orElse(null);
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

        // 4. 상품 데이터 일괄 조회 (N+1 최적화)
        List<Long> allProductIds = extractAllProductIds(postPage.getContent());
        Map<Long, Long> globalWishlistCountMap = toWishlistCountMap(allProductIds);
        Map<Long, Long> globalReviewCountMap = toReviewCountMap(allProductIds);
        Set<Long> globalWishedProductIds = user != null && !allProductIds.isEmpty()
                ? wishlistRepository.findProductIdsWishedByUserAndProductIdIn(user.getId(), allProductIds)
                : Collections.emptySet();

        // 5. DTO 변환
        final Map<Long, Boolean> finalWishlistMap = wishlistMap;
        Page<PostDto.FeedItemResponse> dtoPage = postPage.map(post -> {
            Market market = post.getMarket();
            List<PostDto.PostProductResponse> registeredProducts = buildRegisteredProducts(
                    post, globalWishlistCountMap, globalReviewCountMap, globalWishedProductIds);
            PostDto.PostListItem postItem = PostDto.PostListItem.builder()
                    .postId(post.getId())
                    .showroomId(market.getId())
                    .showroomName(market.getMarketName())
                    .showroomImageUrl(market.getMarketImageUrl())
                    .title(post.getTitle())
                    .imageUrls(post.getImageUrls())
                    .viewCount(post.getViewCount())
                    .isWishlisted(finalWishlistMap.getOrDefault(post.getId(), false))
                    .wishlistCount(post.getWishlistCount())
                    .registeredProducts(registeredProducts)
                    .createdAt(post.getCreatedAt())
                    .build();
            return PostDto.FeedItemResponse.builder()
                    .contentType("POST")
                    .post(postItem)
                    .build();
        });

        return new PageResponse<>(dtoPage);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostDto.FeedItemResponse> getFollowingFeed(String username, PagingRequest pagingRequest) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<Long> followingMarketIds = marketFollowRepository.findMarketIdsByUserId(user.getId());
        Pageable pageable = pagingRequest.toPageable();

        if (followingMarketIds.isEmpty()) {
            return new PageResponse<>(Page.empty(pageable));
        }

        Page<Post> postPage = postRepository.findDisplayedPostsByMarketIds(followingMarketIds, pageable);

        List<Long> postIds = postPage.getContent().stream()
                .map(Post::getId)
                .collect(Collectors.toList());

        Map<Long, Boolean> wishlistMap = Map.of();
        if (!postIds.isEmpty()) {
            List<Long> wishlistedPostIds = postWishlistRepository
                    .findWishlistedPostIdsByUserIdAndPostIds(user.getId(), postIds);

            wishlistMap = wishlistedPostIds.stream()
                    .collect(Collectors.toMap(id -> id, id -> true));
        }

        // 3. 상품 데이터 일괄 조회 (N+1 최적화)
        List<Long> allProductIds = extractAllProductIds(postPage.getContent());
        Map<Long, Long> globalWishlistCountMap = toWishlistCountMap(allProductIds);
        Map<Long, Long> globalReviewCountMap = toReviewCountMap(allProductIds);
        Set<Long> globalWishedProductIds = !allProductIds.isEmpty()
                ? wishlistRepository.findProductIdsWishedByUserAndProductIdIn(user.getId(), allProductIds)
                : Collections.emptySet();

        final Map<Long, Boolean> finalWishlistMap = wishlistMap;
        Page<PostDto.FeedItemResponse> dtoPage = postPage.map(post -> {
            Market market = post.getMarket();
            List<PostDto.PostProductResponse> registeredProducts = buildRegisteredProducts(
                    post, globalWishlistCountMap, globalReviewCountMap, globalWishedProductIds);
            PostDto.PostListItem postItem = PostDto.PostListItem.builder()
                    .postId(post.getId())
                    .showroomId(market.getId())
                    .showroomName(market.getMarketName())
                    .showroomImageUrl(market.getMarketImageUrl())
                    .title(post.getTitle())
                    .imageUrls(post.getImageUrls())
                    .viewCount(post.getViewCount())
                    .isWishlisted(finalWishlistMap.getOrDefault(post.getId(), false))
                    .wishlistCount(post.getWishlistCount())
                    .registeredProducts(registeredProducts)
                    .createdAt(post.getCreatedAt())
                    .build();

            return PostDto.FeedItemResponse.builder()
                    .contentType("POST")
                    .post(postItem)
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
        post.incrementWishlistCount();
    }

    public void removePostFromWishlist(String username, Long postId) {
        // 1. User 조회
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. Post 조회
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // 3. 위시리스트에서 삭제
        postWishlistRepository.deleteByUserIdAndPostId(user.getId(), postId);
        post.decrementWishlistCount();
    }

    @Transactional(readOnly = true)
    public PageResponse<PostDto.FeedItemResponse> getWishlistedPosts(String username, PagingRequest pagingRequest) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. Pageable 생성
        Pageable pageable = pagingRequest.toPageable();

        // 3. 위시리스트 조회
        Page<Post> postPage = postWishlistRepository.findWishlistedPostsByUserId(user.getId(), pageable);

        // 4. 상품 데이터 일괄 조회 (N+1 최적화)
        List<Long> allProductIds = extractAllProductIds(postPage.getContent());
        Map<Long, Long> globalWishlistCountMap = toWishlistCountMap(allProductIds);
        Map<Long, Long> globalReviewCountMap = toReviewCountMap(allProductIds);
        Set<Long> globalWishedProductIds = !allProductIds.isEmpty()
                ? wishlistRepository.findProductIdsWishedByUserAndProductIdIn(user.getId(), allProductIds)
                : Collections.emptySet();

        Page<PostDto.FeedItemResponse> dtoPage = postPage.map(post -> {
            Market market = post.getMarket();
            List<PostDto.PostProductResponse> registeredProducts = buildRegisteredProducts(
                    post, globalWishlistCountMap, globalReviewCountMap, globalWishedProductIds);
            PostDto.PostListItem postItem = PostDto.PostListItem.builder()
                    .postId(post.getId())
                    .showroomId(market.getId())
                    .showroomName(market.getMarketName())
                    .showroomImageUrl(market.getMarketImageUrl())
                    .title(post.getTitle())
                    .imageUrls(post.getImageUrls())
                    .viewCount(post.getViewCount())
                    .isWishlisted(true)
                    .wishlistCount(post.getWishlistCount())
                    .registeredProducts(registeredProducts)
                    .createdAt(post.getCreatedAt())
                    .build();
            return PostDto.FeedItemResponse.builder()
                    .contentType("POST")
                    .post(postItem)
                    .build();
        });

        return new PageResponse<>(dtoPage);
    }

    /**
     * 조회된 여러 Post 엔티티 내부의 상품 ID를 중복 없이 수집합니다.
     */
    private List<Long> extractAllProductIds(List<Post> posts) {
        return posts.stream()
                .filter(post -> post.getPostProducts() != null)
                .flatMap(post -> post.getPostProducts().stream())
                .map(pp -> pp.getProduct().getProductId())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 미리 조회한 집계 맵과 Set을 사용해 포스트 상품 목록을 DTO로 변환합니다.
     */
    private List<PostDto.PostProductResponse> buildRegisteredProducts(
            Post post,
            Map<Long, Long> wishlistCountMap,
            Map<Long, Long> reviewCountMap,
            Set<Long> wishedProductIds) {

        List<PostProduct> postProducts = post.getPostProducts();
        if (postProducts == null || postProducts.isEmpty()) {
            return Collections.emptyList();
        }

        List<PostDto.PostProductResponse> result = new ArrayList<>();
        for (PostProduct pp : postProducts) {
            Product product = pp.getProduct();
            Long pid = product.getProductId();
            Integer regularPrice = product.getRegularPrice();
            Integer salePrice = product.getSalePrice();
            Integer discountRate = calculateDiscountRate(regularPrice, salePrice);

            result.add(PostDto.PostProductResponse.builder()
                    .productId(pid)
                    .productImageUrl(product.getThumbnailUrl())
                    .marketName(product.getMarket() != null ? product.getMarket().getMarketName() : null)
                    .productName(product.getName())
                    .discountRate(discountRate)
                    .price(salePrice != null ? salePrice : product.getRegularPrice())
                    .wishlistCount(wishlistCountMap.getOrDefault(pid, 0L))
                    .reviewCount(reviewCountMap.getOrDefault(pid, 0L))
                    .isWishlisted(wishedProductIds.contains(pid))
                    .build());
        }
        return result;
    }
}
