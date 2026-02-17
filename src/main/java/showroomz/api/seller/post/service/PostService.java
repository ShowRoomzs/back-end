package showroomz.api.seller.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.post.DTO.PostDto;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.repository.PostRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PostService {

    private final PostRepository postRepository;
    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;

    public PostDto.CreatePostResponse createPost(String sellerEmail, PostDto.CreatePostRequest request) {
        // 1. Seller 조회
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. Market 조회
        Market market = marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        // 3. Post 생성
        Post post = new Post(market, request.getTitle(), request.getContent(), request.getImageUrl());
        Post savedPost = postRepository.save(post);

        // 4. Response 생성
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

        // 3. Pageable 생성
        int page = pagingRequest.getPage() != null ? pagingRequest.getPage() : 0;
        int limit = pagingRequest.getLimit() != null ? pagingRequest.getLimit() : 20;
        Pageable pageable = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 4. Post 목록 조회
        Page<Post> postPage = postRepository.findByMarketId(market.getId(), pageable);

        // 5. DTO 변환
        Page<PostDto.PostListItem> dtoPage = postPage.map(post ->
                PostDto.PostListItem.builder()
                        .postId(post.getId())
                        .title(post.getTitle())
                        .imageUrl(post.getImageUrl())
                        .viewCount(post.getViewCount())
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

        // 4. Post 업데이트
        post.update(request.getTitle(), request.getContent(), request.getImageUrl(), request.getIsDisplay());

        // 5. Response 생성
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
