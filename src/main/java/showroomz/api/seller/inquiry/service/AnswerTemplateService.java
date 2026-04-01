package showroomz.api.seller.inquiry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.inquiry.dto.AnswerTemplateDeleteRequest;
import showroomz.api.seller.inquiry.dto.AnswerTemplateDto;
import showroomz.api.seller.inquiry.dto.AnswerTemplateRegisterRequest;
import showroomz.api.seller.inquiry.dto.AnswerTemplateRegisterResponse;
import showroomz.api.seller.inquiry.dto.AnswerTemplateUpdateRequest;
import showroomz.api.seller.inquiry.type.MarketInquiryFilterType;
import showroomz.domain.inquiry.entity.AnswerTemplate;
import showroomz.domain.inquiry.repository.AnswerTemplateRepository;
import showroomz.domain.member.seller.entity.Seller;

import java.util.List;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional
public class AnswerTemplateService {

    private final AnswerTemplateRepository answerTemplateRepository;
    private final SellerRepository sellerRepository;

    public AnswerTemplateRegisterResponse registerTemplate(String sellerEmail,
                                                           AnswerTemplateRegisterRequest request) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_AUTH_INFO));

        AnswerTemplate template = AnswerTemplate.builder()
                .seller(seller)
                .title(request.getTitle())
                .category(request.getCategory())
                .content(request.getContent())
                .isActive(request.resolveIsActive())
                .build();

        AnswerTemplate saved = Objects.requireNonNull(answerTemplateRepository.save(template));
        return new AnswerTemplateRegisterResponse(saved.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<AnswerTemplateDto> getTemplates(String sellerEmail,
                                                        MarketInquiryFilterType category,
                                                        String keyword,
                                                        PagingRequest pagingRequest) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_AUTH_INFO));

        String trimmedKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        Page<AnswerTemplate> page = answerTemplateRepository.findActiveTemplates(
                seller.getId(),
                category,
                trimmedKeyword,
                pagingRequest.toPageable(Sort.by(Sort.Direction.DESC, "modifiedAt"))
        );

        return new PageResponse<>(page.map(AnswerTemplateDto::from));
    }

    @Transactional(readOnly = true)
    public AnswerTemplateDto getTemplate(String sellerEmail, Long templateId) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_AUTH_INFO));

        AnswerTemplate template = answerTemplateRepository
                .findByIdAndSellerId(templateId, seller.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA));

        return AnswerTemplateDto.from(template);
    }

    public void updateTemplate(String sellerEmail, Long templateId, AnswerTemplateUpdateRequest request) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_AUTH_INFO));

        AnswerTemplate template = answerTemplateRepository
                .findByIdAndSellerId(templateId, seller.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA));

        template.update(request.getTitle(), request.getCategory(), request.getContent(), request.getIsActive());
    }

    public void deleteTemplates(String sellerEmail, AnswerTemplateDeleteRequest request) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_AUTH_INFO));

        List<AnswerTemplate> templates = answerTemplateRepository
                .findAllByIdInAndSellerId(request.getTemplateIds(), seller.getId());

        answerTemplateRepository.deleteAll(templates);
    }
}
