package showroomz.api.admin.faq.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.faq.dto.AdminFaqListRequest;
import showroomz.api.admin.faq.dto.AdminFaqListResponse;
import showroomz.api.admin.faq.dto.AdminFaqRegisterRequest;
import showroomz.api.admin.faq.dto.AdminFaqUpdateRequest;
import showroomz.api.admin.faq.dto.FaqReorderRequest;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.faq.repository.FaqRepository;
import showroomz.domain.faq.type.FaqCategory;
import showroomz.global.error.exception.ErrorCode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminFaqService {

    private final FaqRepository faqRepository;

    @Transactional
    public Long registerFaq(AdminFaqRegisterRequest request) {
        FaqCategory category = request.getCategory();
        if (category == null || !category.isPersistable()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "카테고리는 전체(ALL)를 제외한 값이어야 합니다.");
        }
        Faq faq = Faq.builder()
                .category(category)
                .question(request.getQuestion())
                .answer(request.getAnswer())
                .displayOrder(getNextDisplayOrder())
                .build();

        @SuppressWarnings("null")
        Faq savedFaq = faqRepository.save(faq);

        return savedFaq.getId();
    }

    @Transactional
    public void reorderFaqs(FaqReorderRequest request) {
        List<FaqReorderRequest.FaqOrderDto> reorderList = request.getReorderList();

        List<Long> requestedFaqIds = reorderList.stream()
                .map(FaqReorderRequest.FaqOrderDto::getFaqId)
                .collect(Collectors.toList());

        List<Integer> requestedOrders = reorderList.stream()
                .map(FaqReorderRequest.FaqOrderDto::getDisplayOrder)
                .collect(Collectors.toList());

        validateDuplicateIds(requestedFaqIds);
        validateDuplicateOrdersInRequest(requestedOrders);
        validateOrderConflictWithDatabase(requestedOrders, requestedFaqIds);

        List<Faq> existingFaqs = faqRepository.findAllByIdIn(requestedFaqIds);
        if (existingFaqs.size() != requestedFaqIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_DATA, "존재하지 않는 FAQ ID가 포함되어 있습니다.");
        }

        Map<Long, Faq> faqMap = existingFaqs.stream()
                .collect(Collectors.toMap(Faq::getId, Function.identity()));

        for (FaqReorderRequest.FaqOrderDto orderDto : reorderList) {
            Faq faq = faqMap.get(orderDto.getFaqId());
            faq.updateDisplayOrder(orderDto.getDisplayOrder());
        }
    }

    private Integer getNextDisplayOrder() {
        return faqRepository.findTopByOrderByDisplayOrderDescIdDesc()
                .map(faq -> faq.getDisplayOrder() + 1)
                .orElse(1);
    }

    public AdminFaqListResponse getFaq(Long faqId) {
        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA, "존재하지 않는 FAQ입니다."));
        return AdminFaqListResponse.from(faq);
    }

    public PageResponse<AdminFaqListResponse> getFaqs(AdminFaqListRequest request, PagingRequest pagingRequest) {
        Pageable pageable = pagingRequest.toPageable();
        FaqCategory category = request.getCategory();
        if (category == FaqCategory.ALL) {
            category = null;
        }
        String keyword = request.getKeyword();
        Page<Faq> faqPage = faqRepository.findAdminFaqList(category, keyword, pageable);

        return new PageResponse<>(faqPage.map(AdminFaqListResponse::from));
    }

    @Transactional
    public void updateFaq(Long faqId, AdminFaqUpdateRequest request) {
        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA, "존재하지 않는 FAQ입니다."));

        FaqCategory category = request.getCategory();
        if (category == null || !category.isPersistable()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "카테고리는 전체(ALL)를 제외한 값이어야 합니다.");
        }

        faq.update(category, request.getQuestion(), request.getAnswer());
    }

    @Transactional
    public void deleteFaq(Long faqId) {
        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA, "존재하지 않는 FAQ입니다."));

        Integer deletedOrder = faq.getDisplayOrder();
        faqRepository.delete(faq);
        faqRepository.shiftOrderDownAfterDelete(deletedOrder);
    }

    private void validateDuplicateIds(List<Long> requestedFaqIds) {
        Set<Long> uniqueFaqIds = new HashSet<>(requestedFaqIds);
        if (uniqueFaqIds.size() != requestedFaqIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "중복된 FAQ ID는 허용되지 않습니다.");
        }
    }

    private void validateDuplicateOrdersInRequest(List<Integer> requestedOrders) {
        Set<Integer> uniqueOrders = new HashSet<>(requestedOrders);
        if (uniqueOrders.size() != requestedOrders.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "요청 데이터 내에 중복된 정렬 순서(displayOrder)가 존재합니다.");
        }
    }

    private void validateOrderConflictWithDatabase(List<Integer> requestedOrders, List<Long> requestedFaqIds) {
        boolean hasDuplicate = faqRepository.existsByDisplayOrderInAndIdNotIn(requestedOrders, requestedFaqIds);
        if (hasDuplicate) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "요청한 정렬 순서가 이미 다른 FAQ 데이터에서 사용 중입니다. 화면을 새로고침한 후 다시 시도해주세요.");
        }
    }
}

