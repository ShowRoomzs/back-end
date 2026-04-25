package showroomz.api.admin.faq.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.faq.dto.AdminFaqRegisterRequest;
import showroomz.api.admin.faq.dto.FaqReorderRequest;
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

    /**
     * FAQ 등록
     *
     * 주의: 현재 {@link Faq}의 {@code @Builder} 생성자는 {@code isVisible}을 기본 {@code true}로 설정합니다.
     * 따라서 관리자가 비공개(false)로 등록하려면 저장 후 update로 노출 여부를 변경합니다.
     */
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

        Boolean requestedVisible = request.getIsVisible();
        if (requestedVisible != null && !requestedVisible) {
            savedFaq.update(category, request.getQuestion(), request.getAnswer(), false);
        }

        return savedFaq.getId();
    }

    @Transactional
    public void reorderFaqs(FaqReorderRequest request) {
        List<Long> requestedFaqIds = request.getFaqIds();
        validateDuplicateIds(requestedFaqIds);

        List<Faq> existingFaqs = faqRepository.findAllByIdIn(requestedFaqIds);
        if (existingFaqs.size() != requestedFaqIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_DATA, "존재하지 않는 FAQ ID가 포함되어 있습니다.");
        }

        Map<Long, Faq> faqMap = existingFaqs.stream()
                .collect(Collectors.toMap(Faq::getId, Function.identity()));

        for (int i = 0; i < requestedFaqIds.size(); i++) {
            Long faqId = requestedFaqIds.get(i);
            Faq faq = faqMap.get(faqId);
            faq.updateDisplayOrder(i + 1);
        }
    }

    private Integer getNextDisplayOrder() {
        return faqRepository.findTopByOrderByDisplayOrderDescIdDesc()
                .map(faq -> faq.getDisplayOrder() + 1)
                .orElse(1);
    }

    private void validateDuplicateIds(List<Long> requestedFaqIds) {
        Set<Long> uniqueFaqIds = new HashSet<>(requestedFaqIds);
        if (uniqueFaqIds.size() != requestedFaqIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "중복된 FAQ ID는 허용되지 않습니다.");
        }
    }
}

