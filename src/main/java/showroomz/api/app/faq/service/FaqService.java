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

    // FAQ 전체 목록 조회 (노출=true만)
    public List<FaqResponse> getFaqList() {
        List<Faq> faqs = faqRepository.findAllByIsVisibleTrue();
        return faqs.stream()
                .map(FaqResponse::from)
                .collect(Collectors.toList());
    }
}

