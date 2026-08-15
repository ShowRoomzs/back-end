package showroomz.api.app.faq.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.faq.dto.FaqCategoryItem;
import showroomz.api.app.faq.dto.FaqResponse;
import showroomz.domain.cs.type.CsCategory;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.faq.repository.FaqRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;

    // FAQ 목록 조회. category=전체/null이면 전체, keyword 있으면 질문 검색
    // 정렬은 운영자가 정한 카테고리 내 노출 순서를 그대로 따른다 (기획 §19-4)
    public List<FaqResponse> getFaqList(String keyword, CsCategory category) {
        List<Faq> faqs = faqRepository.findAppFaqList(category, keyword);
        return faqs.stream().map(FaqResponse::from).toList();
    }

    // FAQ 카테고리 고정 목록 (key: enum 이름, description: 한글 표시명) — 전체 칩 + 5종
    public List<FaqCategoryItem> getFaqCategories() {
        List<FaqCategoryItem> categories = new ArrayList<>();
        categories.add(FaqCategoryItem.all());
        for (CsCategory category : CsCategory.values()) {
            categories.add(FaqCategoryItem.from(category));
        }
        return categories;
    }
}
