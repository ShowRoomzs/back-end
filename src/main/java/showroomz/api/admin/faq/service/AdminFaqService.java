package showroomz.api.admin.faq.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.faq.dto.AdminFaqCategoryCount;
import showroomz.api.admin.faq.dto.AdminFaqListRequest;
import showroomz.api.admin.faq.dto.AdminFaqListResponse;
import showroomz.api.admin.faq.dto.AdminFaqPageResponse;
import showroomz.api.admin.faq.dto.AdminFaqRegisterRequest;
import showroomz.api.admin.faq.dto.AdminFaqUpdateRequest;
import showroomz.api.admin.faq.dto.FaqReorderRequest;
import showroomz.global.dto.PagingRequest;
import showroomz.global.dto.PaginationInfo;
import showroomz.global.error.exception.BusinessException;
import showroomz.domain.cs.type.CsCategory;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.faq.repository.FaqRepository;
import showroomz.global.error.exception.ErrorCode;

import java.util.ArrayList;
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

    /** 신규 등록·카테고리 이동 시 배치되는 자리 — 해당 카테고리 맨 위 (기획 §19-4) */
    private static final int TOP_DISPLAY_ORDER = 1;

    private final FaqRepository faqRepository;

    @Transactional
    public Long registerFaq(AdminFaqRegisterRequest request) {
        CsCategory category = requireCategory(request.getCategory());

        // 등록은 해당 카테고리 맨 위에 배치한다 (기획 §19-4)
        faqRepository.shiftOrderUpForInsert(category);

        Faq faq = Faq.builder()
                .category(category)
                .question(request.getQuestion())
                .answer(request.getAnswer())
                .displayOrder(TOP_DISPLAY_ORDER)
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

        List<Faq> existingFaqs = faqRepository.findAllByIdIn(requestedFaqIds);
        if (existingFaqs.size() != requestedFaqIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_DATA, "존재하지 않는 FAQ ID가 포함되어 있습니다.");
        }

        // 순서는 카테고리 안에서만 유효하다 (기획 §19-4)
        CsCategory category = validateSameCategory(existingFaqs);

        validateOrderRange(requestedOrders, category);
        validateOrderConflictWithDatabase(category, requestedOrders, requestedFaqIds);

        Map<Long, Faq> faqMap = existingFaqs.stream()
                .collect(Collectors.toMap(Faq::getId, Function.identity()));

        for (FaqReorderRequest.FaqOrderDto orderDto : reorderList) {
            Faq faq = faqMap.get(orderDto.getFaqId());
            faq.updateDisplayOrder(orderDto.getDisplayOrder());
        }
    }

    public AdminFaqListResponse getFaq(Long faqId) {
        return AdminFaqListResponse.from(findFaq(faqId));
    }

    public AdminFaqPageResponse getFaqs(AdminFaqListRequest request, PagingRequest pagingRequest) {
        Pageable pageable = pagingRequest.toPageable();
        CsCategory category = request.toCategory();
        String keyword = request.getKeyword();

        Page<Faq> faqPage = faqRepository.findAdminFaqList(category, keyword, pageable);

        return AdminFaqPageResponse.builder()
                .content(faqPage.getContent().stream().map(AdminFaqListResponse::from).toList())
                .pageInfo(new PaginationInfo(faqPage))
                .categoryCounts(buildCategoryCounts(keyword))
                .build();
    }

    @Transactional
    public void updateFaq(Long faqId, AdminFaqUpdateRequest request) {
        Faq faq = findFaq(faqId);
        CsCategory newCategory = requireCategory(request.getCategory());

        CsCategory currentCategory = faq.getCategory();
        if (currentCategory == newCategory) {
            faq.update(newCategory, request.getQuestion(), request.getAnswer());
            return;
        }

        // 카테고리를 옮기면 순서도 옮겨간다 — 기존 카테고리는 당기고, 새 카테고리에서는 맨 위에 놓는다
        faqRepository.shiftOrderDownAfterDelete(currentCategory, faq.getDisplayOrder());
        faqRepository.shiftOrderUpForInsert(newCategory);

        // 순서 갱신은 벌크 연산이라 영속성 컨텍스트가 비워진 상태다
        Faq reloadedFaq = findFaq(faqId);
        reloadedFaq.update(newCategory, request.getQuestion(), request.getAnswer());
        reloadedFaq.updateDisplayOrder(TOP_DISPLAY_ORDER);
    }

    @Transactional
    public void deleteFaq(Long faqId) {
        Faq faq = findFaq(faqId);

        CsCategory category = faq.getCategory();
        Integer deletedOrder = faq.getDisplayOrder();

        faqRepository.delete(faq);
        // 같은 카테고리에 남은 항목의 순서를 당긴다 (기획 §19-6)
        faqRepository.shiftOrderDownAfterDelete(category, deletedOrder);
    }

    private Faq findFaq(Long faqId) {
        return faqRepository.findById(faqId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA, "존재하지 않는 FAQ입니다."));
    }

    private List<AdminFaqCategoryCount> buildCategoryCounts(String keyword) {
        Map<CsCategory, Long> counts = faqRepository.countByCategoryGroup(keyword);

        List<AdminFaqCategoryCount> categoryCounts = new ArrayList<>();
        long total = 0L;
        for (CsCategory category : CsCategory.values()) {
            long count = counts.getOrDefault(category, 0L);
            total += count;
            categoryCounts.add(AdminFaqCategoryCount.of(category, count));
        }
        // 전체 탭은 맨 앞 (기획 §19-2)
        categoryCounts.add(0, AdminFaqCategoryCount.all(total));

        return categoryCounts;
    }

    private CsCategory requireCategory(CsCategory category) {
        if (category == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "카테고리를 선택해주세요.");
        }
        return category;
    }

    private CsCategory validateSameCategory(List<Faq> faqs) {
        Set<CsCategory> categories = faqs.stream()
                .map(Faq::getCategory)
                .collect(Collectors.toSet());
        if (categories.size() > 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "순서 변경은 같은 카테고리 안에서만 가능합니다.");
        }
        return categories.iterator().next();
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

    private void validateOrderRange(List<Integer> requestedOrders, CsCategory category) {
        long categoryFaqCount = faqRepository.countByCategory(category);
        for (Integer order : requestedOrders) {
            if (order < 1 || order > categoryFaqCount) {
                throw new BusinessException(
                        ErrorCode.INVALID_INPUT_VALUE,
                        String.format("변경할 순서는 1부터 해당 카테고리(%s)의 FAQ 개수(%d) 사이여야 합니다.",
                                category.getDescription(), categoryFaqCount)
                );
            }
        }
    }

    private void validateOrderConflictWithDatabase(CsCategory category, List<Integer> requestedOrders, List<Long> requestedFaqIds) {
        boolean hasDuplicate = faqRepository.existsByCategoryAndDisplayOrderInAndIdNotIn(category, requestedOrders, requestedFaqIds);
        if (hasDuplicate) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "요청한 정렬 순서가 같은 카테고리의 다른 FAQ에서 사용 중입니다. 화면을 새로고침한 후 다시 시도해주세요.");
        }
    }
}
