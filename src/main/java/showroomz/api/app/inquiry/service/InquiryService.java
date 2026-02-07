package showroomz.api.app.inquiry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.inquiry.dto.InquiryDetailResponse;
import showroomz.api.app.inquiry.dto.InquiryListResponse;
import showroomz.api.app.inquiry.dto.InquiryRegisterRequest;
import showroomz.api.app.inquiry.dto.InquiryRegisterResponse;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {

    private final OneToOneInquiryRepository inquiryRepository;
    private final UserRepository userRepository;

    // 1:1 문의 등록
    @Transactional
    public InquiryRegisterResponse registerInquiry(Long userId, InquiryRegisterRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        OneToOneInquiry inquiry = OneToOneInquiry.builder()
                .user(user)
                .type(request.getType())          // Enum (대분류)
                .category(request.getCategory())  // String (상세 유형)
                .content(request.getContent())
                .imageUrls(request.getImageUrls())
                .build();

        inquiryRepository.save(inquiry);
        return InquiryRegisterResponse.builder()
                .inquiryId(inquiry.getId())
                .build();
    }

    // 내 문의 내역 조회 (목록)
    public PageResponse<InquiryListResponse> getMyInquiries(Long userId, Pageable pageable) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Page<OneToOneInquiry> page = inquiryRepository.findByUserOrderByCreatedAtDesc(user, pageable);

        return PageResponse.of(page.map(InquiryListResponse::from));
    }

    // 문의 상세 조회
    public InquiryDetailResponse getInquiryDetail(Long userId, Long inquiryId) {
        OneToOneInquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA));

        // 본인의 문의인지 검증
        if (!inquiry.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        return InquiryDetailResponse.from(inquiry);
    }
}
