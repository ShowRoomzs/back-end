package showroomz.api.admin.faq.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.faq.dto.AdminFaqRegisterRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.faq.repository.FaqRepository;
import showroomz.domain.faq.type.FaqCategory;
import showroomz.global.error.exception.ErrorCode;

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
                .build();

        @SuppressWarnings("null")
        Faq savedFaq = faqRepository.save(faq);

        Boolean requestedVisible = request.getIsVisible();
        if (requestedVisible != null && !requestedVisible) {
            savedFaq.update(category, request.getQuestion(), request.getAnswer(), false);
        }

        return savedFaq.getId();
    }
}

