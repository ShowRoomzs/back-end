package showroomz.api.showroom.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.showroom.post.DTO.PostDto;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ShowroomPostService {

    private final PostRepository postRepository;
    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final ProductRepository productRepository;

    public PostDto.CreatePostResponse createPost(String sellerEmail, PostDto.CreatePostRequest request) {
        // 1. 이미지와 상품 등록 중복 및 누락 검증 (둘 중 하나만 가능)
        boolean hasImage = request.getImageUrl() != null && !request.getImageUrl().isBlank();
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

        // 4. Post 생성
        Post post = new Post(market, request.getTitle(), request.getContent(), request.getImageUrl());

        // 5. 상품이 입력된 경우 검증 및 추가
        if (hasProducts) {
            List<Product> products = productRepository.findAllById(request.getProductIds());
            if (products.size() != request.getProductIds().size()) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            for (Product product : products) {
                if (!product.getMarket().getId().equals(market.getId())) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }
                post.addProduct(product);
            }
        }

        Post savedPost = postRepository.save(post);

        // 6. Response 생성
        return PostDto.CreatePostResponse.builder()
                .postId(savedPost.getId())
                .title(savedPost.getTitle())
                .content(savedPost.getContent())
                .imageUrl(savedPost.getImageUrl())
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

        // 3. Post 조회 및 권한 확인
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!post.getMarket().getId().equals(market.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 4. Response 생성
        return PostDto.PostDetailResponse.builder()
                .postId(post.getId())
                .marketId(market.getId())
                .marketName(market.getMarketName())
                .title(post.getTitle())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .viewCount(post.getViewCount())
                .wishlistCount(post.getWishlistCount())
                .isDisplay(post.getIsDisplay())
                .createdAt(post.getCreatedAt())
                .modifiedAt(post.getModifiedAt())
                .build();
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

        // 5. DTO 변환
        Page<PostDto.PostListItem> dtoPage = postPage.map(post ->
                PostDto.PostListItem.builder()
                        .postId(post.getId())
                        .title(post.getTitle())
                        .imageUrl(post.getImageUrl())
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
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!post.getMarket().getId().equals(market.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 4. 이미지·상품 둘 다 넘어온 경우 검증
        boolean hasImage = request.getImageUrl() != null && !request.getImageUrl().isBlank();
        boolean hasProducts = request.getProductIds() != null && !request.getProductIds().isEmpty();
        if (hasImage && hasProducts) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 5. Post 기본 정보 업데이트
        post.update(request.getTitle(), request.getContent(), request.getImageUrl(), request.getIsDisplay());

        // 6. 상품 목록 수정 요청이 있으면 기존 매핑 제거 후 재등록
        if (request.getProductIds() != null) {
            post.clearProducts();
            if (!request.getProductIds().isEmpty()) {
                List<Product> products = productRepository.findAllById(request.getProductIds());
                if (products.size() != request.getProductIds().size()) {
                    throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
                }
                for (Product product : products) {
                    if (!product.getMarket().getId().equals(market.getId())) {
                        throw new BusinessException(ErrorCode.FORBIDDEN);
                    }
                    post.addProduct(product);
                }
            }
        }

        // 7. Response 생성
        return PostDto.UpdatePostResponse.builder()
                .postId(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .isDisplay(post.getIsDisplay())
                .modifiedAt(post.getModifiedAt())
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
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!post.getMarket().getId().equals(market.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 4. Post 삭제
        postRepository.delete(post);
    }
}
