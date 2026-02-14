package showroomz.api.app.faq.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.faq.dto.FaqResponse;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.faq.repository.FaqRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;

    // FAQ 목록 조회 (노출=true만). keyword가 있으면 질문 내용 기준 부분 일치 검색(대소문자 무시)
    public List<FaqResponse> getFaqList(String keyword) {
        List<Faq> faqs = (keyword == null || keyword.isBlank())
                ? faqRepository.findAllByIsVisibleTrue()
                : faqRepository.findAllByIsVisibleTrueAndQuestionContainingIgnoreCase(keyword.trim());
        return faqs.stream()
                .map(FaqResponse::from)
                .collect(Collectors.toList());
    }

    // FAQ 카테고리 목록 조회 (노출=true인 FAQ에 존재하는 카테고리만, 중복 제거, 가나다순)
    public List<String> getFaqCategories() {
        return faqRepository.findDistinctCategoriesByIsVisibleTrue();
    }
}

