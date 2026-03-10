package showroomz.api.creator.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.creator.post.DTO.PostDto;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
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
    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final ProductRepository productRepository;
    private final WishlistRepository wishlistRepository;
    private final ReviewRepository reviewRepository;

    public PostDto.CreatePostResponse createPost(String sellerEmail, PostDto.CreatePostRequest request) {
        // 1. 다중 이미지 컬렉션 검증 및 상품 등록 중복 검증 (둘 중 하나만 가능)
        boolean hasImage = request.getImageUrls() != null && !request.getImageUrls().isEmpty();
        boolean hasProducts = request.getProductIds() != null && !request.getProductIds().isEmpty();

        if (hasImage && hasProducts) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 2. Seller 조회
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 3. Market 조회
        Market market = marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        // 4. Post 생성 (다중 이미지 리스트 전달)
        Post post = new Post(market, request.getTitle(), request.getContent(), request.getImageUrls());

        // 5. 상품이 입력된 경우 검증 및 추가
        if (hasProducts) {
            List<Long> productIds = Objects.requireNonNull(request.getProductIds());
            List<Product> products = productRepository.findAllById(productIds);
            if (products.size() != productIds.size()) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            for (Product product : products) {
                if (!product.getMarket().getId().equals(market.getId())) {
                    throw new BusinessException(ErrorCode.FORBIDDEN, "해당 쇼룸에 등록되지 않은 상품입니다.");
                }
                post.addProduct(product);
            }
        }

        Post savedPost = postRepository.save(post);

        // 6. Response 생성 (다중 이미지 리스트 맵핑)
        return PostDto.CreatePostResponse.builder()
                .postId(savedPost.getId())
                .title(savedPost.getTitle())
                .content(savedPost.getContent())
                .imageUrls(savedPost.getImageUrls())
                .createdAt(savedPost.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public PostDto.PostDetailResponse getPostById(String sellerEmail, Long postId) {
        // 1. Seller 조회
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. Market 조회
        Market market = marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        // 3. Post 조회 (등록 상품 목록 포함, N+1 방지) 및 권한 확인
        Post post = postRepository.findByIdWithPostProductsAndProducts(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!post.getMarket().getId().equals(market.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 4. 단일 포스트에 대한 상품 ID 목록 추출 및 일괄 집계 조회
        List<Long> productIds = post.getPostProducts() == null ? Collections.emptyList()
                : post.getPostProducts().stream()
                .map(pp -> pp.getProduct().getProductId())
                .collect(Collectors.toList());

        Map<Long, Long> wishlistCountMap = toWishlistCountMap(productIds);
        Map<Long, Long> reviewCountMap = toReviewCountMap(productIds);

        // 크리에이터 컨텍스트에서는 "내 위시 여부"가 의미 없으므로 항상 false 처리
        Set<Long> wishedProductIds = Collections.emptySet();

        List<PostDto.PostProductResponse> registeredProducts = buildRegisteredProducts(
                post, wishlistCountMap, reviewCountMap, wishedProductIds);

        // 5. Response 생성
        return PostDto.PostDetailResponse.builder()
                .postId(post.getId())
                .marketId(market.getId())
                .marketName(market.getMarketName())
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
    public PageResponse<PostDto.PostListItem> getPostList(String sellerEmail, PagingRequest pagingRequest) {
        // 1. Seller 조회
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. Market 조회
        Market market = marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        // 3. Pageable 생성 (PagingRequest는 page 1-based, size 기본값 적용)
        Pageable pageable = pagingRequest.toPageable();

        // 4. Post 목록 조회
        Page<Post> postPage = postRepository.findByMarketId(market.getId(), pageable);

        // 5. DTO 변환 (post.getImageUrls() 전체 리스트 전달)
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

    public PostDto.UpdatePostResponse updatePost(String sellerEmail, Long postId, PostDto.UpdatePostRequest request) {
        // 1. Seller 조회
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. Market 조회
        Market market = marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        // 3. Post 조회 및 권한 확인
        Long safePostId = Objects.requireNonNull(postId);
        Post post = postRepository.findById(safePostId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!post.getMarket().getId().equals(market.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 4. 다중 이미지 배열 컬렉션 검증
        boolean hasImage = request.getImageUrls() != null && !request.getImageUrls().isEmpty();
        boolean hasProducts = request.getProductIds() != null && !request.getProductIds().isEmpty();
        if (hasImage && hasProducts) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 5. Post 기본 정보 업데이트 (List 파라미터 전달)
        post.update(request.getTitle(), request.getContent(), request.getImageUrls(), request.getIsDisplay());

        // 6. 상품 목록 수정 요청이 있으면 기존 매핑 제거 후 재등록
        if (request.getProductIds() != null) {
            post.clearProducts();
            if (!request.getProductIds().isEmpty()) {
                List<Long> productIds = Objects.requireNonNull(request.getProductIds());
                List<Product> products = productRepository.findAllById(productIds);
                if (products.size() != productIds.size()) {
                    throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
                }
                for (Product product : products) {
                    if (!product.getMarket().getId().equals(market.getId())) {
                        throw new BusinessException(ErrorCode.FORBIDDEN, "해당 쇼룸에 등록되지 않은 상품입니다.");
                    }
                    post.addProduct(product);
                }
            }
        }

        // 7. Response 생성
        return PostDto.UpdatePostResponse.builder()
                .postId(post.getId())
                .build();
    }

    public void deletePost(String sellerEmail, Long postId) {
        // 1. Seller 조회
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. Market 조회
        Market market = marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        // 3. Post 조회 및 권한 확인
        Long safePostId = Objects.requireNonNull(postId);
        Post post = postRepository.findById(safePostId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!post.getMarket().getId().equals(market.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 4. Post 삭제
        postRepository.delete(post);
    }
}
