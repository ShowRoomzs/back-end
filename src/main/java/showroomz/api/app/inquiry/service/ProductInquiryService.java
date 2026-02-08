package showroomz.api.app.inquiry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.inquiry.dto.ProductInquiryListResponse;
import showroomz.api.app.inquiry.dto.ProductInquiryRegisterRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductInquiryService {

    private final ProductInquiryRepository productInquiryRepository;
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
                .content(request.getContent())
                .secret(request.isSecret())
                .build();

        productInquiryRepository.save(inquiry);
        return inquiry.getId();
    }

    public PageResponse<ProductInquiryListResponse> getMyInquiries(Long userId, Pageable pageable) {
        Page<ProductInquiry> page = productInquiryRepository.findByUserId(userId, pageable);
        return PageResponse.of(page.map(ProductInquiryListResponse::from));
    }
}
