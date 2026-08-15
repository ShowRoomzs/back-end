package showroomz.api.app.inquiry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.inquiry.dto.InquiryCategoryResponse;
import showroomz.api.app.inquiry.dto.ProductInquiryResponse;
import showroomz.api.app.inquiry.dto.ProductInquiryRegisterRequest;
import showroomz.api.app.inquiry.dto.ProductInquiryUpdateRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.entity.ProductInquiryHistory;
import showroomz.domain.inquiry.repository.ProductInquiryHistoryRepository;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.type.ProductInquiryHistoryType;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductImage;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductInquiryService {

    private final ProductInquiryRepository productInquiryRepository;
    private final ProductInquiryHistoryRepository productInquiryHistoryRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Long registerInquiry(Long userId, Long productId, ProductInquiryRegisterRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        ProductInquiry inquiry = ProductInquiry.builder()
                .user(user)
                .product(product)
                .type(request.getType())
                .content(request.getContent())
                .secret(request.isSecret())
                .imageUrls(request.getImageUrls())
                .build();

        productInquiryRepository.save(inquiry);
        productInquiryHistoryRepository.save(new ProductInquiryHistory(
                inquiry, ProductInquiryHistoryType.REGISTERED, inquiry.isSecret() ? "비밀글" : null));
        return inquiry.getId();
    }

    // status가 있으면 해당 상태만 조회한다 ([답변 대기만] 필터)
    public PageResponse<ProductInquiryResponse> getMyInquiries(Long userId, InquiryStatus status, Pageable pageable) {
        Page<ProductInquiry> page = (status == null)
                ? productInquiryRepository.findByUserId(userId, pageable)
                : productInquiryRepository.findByUserIdAndStatus(userId, status, pageable);
        return PageResponse.of(page.map(inquiry ->
                ProductInquiryResponse.of(inquiry, resolveImageUrl(inquiry))));
    }

    public ProductInquiryResponse getInquiryDetail(Long userId, Long inquiryId) {
        ProductInquiry inquiry = getMyInquiry(userId, inquiryId);

        // 삭제 집행된 문의는 질문·답변이 함께 소비자 화면에서 내려간다 (§23-5)
        if (inquiry.isDeleted()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_DATA);
        }

        String imageUrl = resolveImageUrl(inquiry);
        return ProductInquiryResponse.of(inquiry, imageUrl);
    }

    @Transactional
    public void updateInquiry(Long userId, Long inquiryId, ProductInquiryUpdateRequest request) {
        ProductInquiry inquiry = getMyInquiry(userId, inquiryId);

        if (inquiry.getStatus() == InquiryStatus.ANSWERED) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }
        requireExposureNormal(inquiry);

        inquiry.update(
                request.getType(),
                request.getContent(),
                request.getImageUrls()
        );
    }

    @Transactional
    public void deleteInquiry(Long userId, Long inquiryId) {
        ProductInquiry inquiry = getMyInquiry(userId, inquiryId);

        if (inquiry.getStatus() == InquiryStatus.ANSWERED) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }
        requireExposureNormal(inquiry);

        productInquiryRepository.delete(inquiry);
    }

    private ProductInquiry getMyInquiry(Long userId, Long inquiryId) {
        ProductInquiry inquiry = productInquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA));

        if (!inquiry.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return inquiry;
    }

    /** 삭제 요청 검토 중이거나 삭제 집행된 문의는 작성자도 손댈 수 없다 (§23-5) */
    private void requireExposureNormal(ProductInquiry inquiry) {
        if (inquiry.isDeleteRequested()) {
            throw new BusinessException(ErrorCode.INQUIRY_UNDER_DELETE_REVIEW);
        }
        if (inquiry.isDeleted()) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_DELETED);
        }
    }

    /** 상품 문의 타입 목록 조회 (1:1 문의 유형 API와 동일한 형식: key, description) */
    public java.util.List<InquiryCategoryResponse> getProductInquiryCategories() {
        return java.util.Arrays.stream(ProductInquiryType.values())
                .map(type -> new InquiryCategoryResponse(type.name(), type.getDescription()))
                .toList();
    }

    private String resolveImageUrl(ProductInquiry inquiry) {
        String imageUrl = inquiry.getProduct().getThumbnailUrl();
        if (imageUrl == null && inquiry.getProduct().getProductImages() != null) {
            imageUrl = inquiry.getProduct().getProductImages().stream()
                    .min(java.util.Comparator.comparing(ProductImage::getOrder))
                    .map(ProductImage::getUrl)
                    .orElse(null);
        }
        return imageUrl;
    }
}
