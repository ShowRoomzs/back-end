package showroomz.api.app.faq.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.faq.dto.FaqResponse;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.faq.repository.FaqRepository;
import showroomz.domain.faq.type.FaqCategory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;

    // FAQ 목록 조회 (노출=true만). category=전체/null이면 전체, keyword 있으면 질문 검색
    public List<FaqResponse> getFaqList(String keyword, FaqCategory category) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        String trimmedKeyword = (keyword == null) ? null : keyword.trim();
        boolean filterByCategory = category != null && category.isPersistable();

        List<Faq> faqs;
        if (filterByCategory && hasKeyword) {
            faqs = faqRepository.findAllByIsVisibleTrueAndCategoryAndQuestionContainingIgnoreCase(category, trimmedKeyword);
        } else if (filterByCategory) {
            faqs = faqRepository.findAllByIsVisibleTrueAndCategory(category);
        } else if (hasKeyword) {
            faqs = faqRepository.findAllByIsVisibleTrueAndQuestionContainingIgnoreCase(trimmedKeyword);
        } else {
            faqs = faqRepository.findAllByIsVisibleTrue();
        }
        return faqs.stream().map(FaqResponse::from).collect(Collectors.toList());
    }

    // FAQ 카테고리 고정 목록 (enum 이름 순서: ALL, DELIVERY, ...)
    public List<String> getFaqCategories() {
        return Arrays.stream(FaqCategory.values())
                .map(FaqCategory::name)
                .toList();
    }
}

