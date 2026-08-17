package showroomz.api.app.inquiry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.inquiry.dto.InquiryCategoryResponse;
import showroomz.api.app.inquiry.dto.InquiryDetailResponse;
import showroomz.api.app.inquiry.dto.InquiryListResponse;
import showroomz.api.app.inquiry.dto.InquiryOrderSummary;
import showroomz.api.app.inquiry.dto.InquiryRegisterRequest;
import showroomz.api.app.inquiry.dto.InquiryRegisterResponse;
import showroomz.api.app.inquiry.dto.InquirySummaryResponse;
import showroomz.api.app.inquiry.dto.InquiryUpdateRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.cs.type.CsCategory;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.repository.OrderRepository;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {

    private final OneToOneInquiryRepository inquiryRepository;
    private final ProductInquiryRepository productInquiryRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    // 1:1 문의 등록 — 답변은 어드민(운영자)만 등록한다
    @Transactional
    public InquiryRegisterResponse registerInquiry(Long userId, InquiryRegisterRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        OneToOneInquiry inquiry = OneToOneInquiry.builder()
                .user(user)
                .type(request.getType())
                .content(request.getContent())
                .imageUrls(request.getImageUrls())
                .orderId(request.getOrderId())
                .build();

        inquiryRepository.save(inquiry);
        return InquiryRegisterResponse.builder()
                .inquiryId(inquiry.getId())
                .build();
    }

    // 내 문의 내역 조회 (목록) — status가 있으면 해당 상태만 조회한다 ([답변 대기만] 필터)
    public PageResponse<InquiryListResponse> getMyInquiries(Long userId, InquiryStatus status, Pageable pageable) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Page<OneToOneInquiry> page = (status == null)
                ? inquiryRepository.findByUserOrderByCreatedAtDesc(user, pageable)
                : inquiryRepository.findByUserAndStatusOrderByCreatedAtDesc(user, status, pageable);

        Map<Long, InquiryOrderSummary> orderSummaries = loadOrderSummaries(page.getContent());

        return PageResponse.of(page.map(inquiry ->
                InquiryListResponse.from(inquiry, orderSummaries.get(inquiry.getOrderId()))));
    }

    /** 문의 내역 탭 건수 — 1:1 / 상품 문의를 한 번에 내려 탭 배지를 그린다 (C12) */
    public InquirySummaryResponse getInquirySummary(Long userId) {
        return InquirySummaryResponse.builder()
                .oneToOneTotal(inquiryRepository.countByUser_Id(userId))
                .oneToOneWaiting(inquiryRepository.countByUser_IdAndStatus(userId, InquiryStatus.WAITING))
                .productTotal(productInquiryRepository.countByUser_IdAndExposureStatusNot(userId, InquiryExposureStatus.DELETED))
                .productWaiting(productInquiryRepository.countByUser_IdAndStatusAndExposureStatusNot(userId, InquiryStatus.WAITING, InquiryExposureStatus.DELETED))
                .build();
    }

    // 문의 상세 조회
    public InquiryDetailResponse getInquiryDetail(Long userId, Long inquiryId) {
        OneToOneInquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA));

        // 본인의 문의인지 검증
        if (!inquiry.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        InquiryOrderSummary order = loadOrderSummaries(List.of(inquiry)).get(inquiry.getOrderId());
        return InquiryDetailResponse.from(inquiry, order);
    }

    // 문의 수정
    @Transactional
    public void updateInquiry(Long userId, Long inquiryId, InquiryUpdateRequest request) {
        OneToOneInquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA));

        if (!inquiry.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        if (inquiry.isAnswered()) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }

        inquiry.update(
                request.getType(),
                request.getContent(),
                request.getImageUrls(),
                request.getOrderId()
        );
    }

    // 문의 삭제 (물리 삭제)
    @Transactional
    public void deleteInquiry(Long userId, Long inquiryId) {
        OneToOneInquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA));

        if (!inquiry.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        if (inquiry.isAnswered()) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }

        inquiryRepository.delete(inquiry);
    }

    /** 1:1 문의 유형 목록 조회 (§17-2-1 — 5종 단일 레벨) */
    public List<InquiryCategoryResponse> getInquiryCategories() {
        return Arrays.stream(CsCategory.values())
                .map(type -> new InquiryCategoryResponse(type.name(), type.getDescription()))
                .toList();
    }

    /**
     * 문의에 연결된 주문을 주문 ID로 한 번에 조회해 주문 카드용 요약으로 만든다.
     * 주문을 연결하지 않은 문의(orderId = null)는 결과에 포함되지 않아 화면에서도 블록이 노출되지 않는다.
     */
    private Map<Long, InquiryOrderSummary> loadOrderSummaries(List<OneToOneInquiry> inquiries) {
        Set<Long> orderIds = inquiries.stream()
                .map(OneToOneInquiry::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (orderIds.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, InquiryOrderSummary> summaries = new HashMap<>();
        for (Order order : orderRepository.findAllByIdInWithProducts(orderIds)) {
            summaries.put(order.getId(), InquiryOrderSummary.from(order));
        }
        return summaries;
    }
}
