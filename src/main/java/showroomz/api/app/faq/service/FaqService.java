package showroomz.api.app.faq.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.faq.dto.FaqRegisterRequest;
import showroomz.api.app.faq.dto.FaqResponse;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.faq.repository.FaqRepository;
import showroomz.domain.inquiry.type.InquiryType;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;

    // FAQ 전체 목록 조회
    public List<FaqResponse> getFaqList(InquiryType type) {
        List<Faq> faqs;

        if (type != null) {
            faqs = faqRepository.findAllByTypeAndIsVisibleTrue(type);
        } else {
            faqs = faqRepository.findAllByIsVisibleTrue();
        }

        return faqs.stream()
                .map(FaqResponse::from)
                .collect(Collectors.toList());
    }

    // FAQ 등록 (주로 어드민 기능이지만 예시로 포함)
    @Transactional
    public Long registerFaq(FaqRegisterRequest request) {
        Faq faq = Faq.builder()
                .type(request.getType())
                .category(request.getCategory())
                .question(request.getQuestion())
                .answer(request.getAnswer())
                .build();

        return faqRepository.save(faq).getId();
    }
}

