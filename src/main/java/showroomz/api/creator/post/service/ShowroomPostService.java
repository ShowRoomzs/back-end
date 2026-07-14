package showroomz.api.creator.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.creator.post.DTO.PostDto;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostProduct;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ShowroomPostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final ProductRepository productRepository;
    private final WishlistRepository wishlistRepository;
    private final ReviewRepository reviewRepository;

    public PostDto.CreatePostResponse createPost(String userEmail, PostDto.CreatePostRequest request) {
        boolean hasImage = request.getImageUrls() != null && !request.getImageUrls().isEmpty();
        boolean hasProducts = request.getProductIds() != null && !request.getProductIds().isEmpty();

        if (hasImage && hasProducts) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Users user = userRepository.findByUsername(userEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Creator creator = creatorRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));

        Post post = new Post(creator, request.getTitle(), request.getContent(), request.getImageUrls());

        if (hasProducts) {
            List<Long> productIds = Objects.requireNonNull(request.getProductIds());
            List<Product> products = productRepository.findAllById(productIds);
            if (products.size() != productIds.size()) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            for (Product product : products) {
                // if (!product.getMarket().getId().equals(market.getId())) {
                //     throw new BusinessException(ErrorCode.FORBIDDEN, "해당 쇼룸에 등록되지 않은 상품입니다.");
                // }
                post.addProduct(product);
            }
        }

        Post savedPost = postRepository.save(post);

        return PostDto.CreatePostResponse.builder()
                .postId(savedPost.getId())
                .build();
    }

    @Transactional(readOnly = true)
    public PostDto.PostDetailResponse getPostById(String userEmail, Long postId) {
        Users user = userRepository.findByUsername(userEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Creator creator = creatorRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));

        Post post = postRepository.findByIdWithPostProductsAndProducts(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!post.getCreator().getId().equals(creator.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<Long> productIds = post.getPostProducts() == null ? Collections.emptyList()
                : post.getPostProducts().stream()
                .map(pp -> pp.getProduct().getProductId())
                .collect(Collectors.toList());

        Map<Long, Long> wishlistCountMap = toWishlistCountMap(productIds);
        Map<Long, Long> reviewCountMap = toReviewCountMap(productIds);

        Set<Long> wishedProductIds = Collections.emptySet();

        List<PostDto.PostProductResponse> registeredProducts = buildRegisteredProducts(
                post, wishlistCountMap, reviewCountMap, wishedProductIds);

        return PostDto.PostDetailResponse.builder()
                .postId(post.getId())
                .creatorId(creator.getId())
                .creatorName(creator.getUser().getNickname())
                .title(post.getTitle())
                .content(post.getContent())
                .imageUrls(post.getImageUrls())
                .viewCount(post.getViewCount())
                .wishlistCount(post.getWishlistCount())
                .isDisplay(post.getIsDisplay())
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
                .collect(Collectors.toMap(
                        row -> Long.valueOf(((Number) row[0]).longValue()),
                        row -> ((Number) row[1]).longValue()
                ));
    }

    private Map<Long, Long> toReviewCountMap(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Object[]> rows = reviewRepository.countByProductIds(productIds);
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> Long.valueOf(((Number) row[0]).longValue()),
                        row -> ((Number) row[1]).longValue()
                ));
    }

    private Integer calculateDiscountRate(Integer regularPrice, Integer salePrice) {
        if (regularPrice == null || salePrice == null || regularPrice <= 0) {
            return 0;
        }
        double rate = ((double) (regularPrice - salePrice) / regularPrice) * 100.0;
        int rounded = (int) Math.round(rate);
        return Math.max(0, Math.min(rounded, 100));
    }

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

    @Transactional(readOnly = true)
    public PageResponse<PostDto.PostListItem> getPostList(String userEmail, PagingRequest pagingRequest) {
        Users user = userRepository.findByUsername(userEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Creator creator = creatorRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));

        Pageable pageable = pagingRequest.toPageable();

        Page<Post> postPage = postRepository.findByCreatorId(creator.getId(), pageable);

        Page<PostDto.PostListItem> dtoPage = postPage.map(post ->
                PostDto.PostListItem.builder()
                        .postId(post.getId())
                        .title(post.getTitle())
                        .imageUrls(post.getImageUrls())
                        .viewCount(post.getViewCount())
                        .wishlistCount(post.getWishlistCount())
                        .isDisplay(post.getIsDisplay())
                        .createdAt(post.getCreatedAt())
                        .build()
        );

        return new PageResponse<>(dtoPage);
    }

    public PostDto.UpdatePostResponse updatePost(String userEmail, Long postId, PostDto.UpdatePostRequest request) {
        Users user = userRepository.findByUsername(userEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Creator creator = creatorRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));

        Long safePostId = Objects.requireNonNull(postId);
        Post post = postRepository.findById(safePostId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!post.getCreator().getId().equals(creator.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        boolean hasImage = request.getImageUrls() != null && !request.getImageUrls().isEmpty();
        boolean hasProducts = request.getProductIds() != null && !request.getProductIds().isEmpty();
        if (hasImage && hasProducts) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 5. 업데이트 규칙
        // - productIds가 제공되면: 기존 이미지 삭제 + 상품 매핑을 해당 목록으로 교체
        // - imageUrls가 제공되면: 기존 상품 매핑 삭제 + 이미지 목록을 해당 목록으로 교체
        // - 둘 다 미제공이면: 제목/본문/전시여부만 부분 수정
        if (request.getProductIds() != null) {
            // 상품 모드로 전환/수정: 기존 이미지 삭제
            post.update(request.getTitle(), request.getContent(), Collections.emptyList(), request.getIsDisplay());

            // 상품 매핑 교체
            post.clearProducts();
            if (!request.getProductIds().isEmpty()) {
                List<Long> productIds = Objects.requireNonNull(request.getProductIds());
                List<Product> products = productRepository.findAllById(productIds);
                if (products.size() != productIds.size()) {
                    throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
                }
                for (Product product : products) {
                    // if (!product.getMarket().getId().equals(market.getId())) {
                    //     throw new BusinessException(ErrorCode.FORBIDDEN, "해당 쇼룸에 등록되지 않은 상품입니다.");
                    // }
                    post.addProduct(product);
                }
            }
        } else if (request.getImageUrls() != null) {
            post.clearProducts();
            post.update(request.getTitle(), request.getContent(), request.getImageUrls(), request.getIsDisplay());
        } else {
            post.update(request.getTitle(), request.getContent(), null, request.getIsDisplay());
        }

        return PostDto.UpdatePostResponse.builder()
                .postId(post.getId())
                .build();
    }

    public void deletePost(String userEmail, Long postId) {
        Users user = userRepository.findByUsername(userEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Creator creator = creatorRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));

        Long safePostId = Objects.requireNonNull(postId);
        Post post = postRepository.findById(safePostId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!post.getCreator().getId().equals(creator.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        postRepository.delete(post);
    }
}
