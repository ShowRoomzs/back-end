package showroomz.api.seller.inquiry.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.inquiry.docs.AnswerTemplateControllerDocs;
import showroomz.api.seller.inquiry.dto.AnswerTemplateDeleteRequest;
import showroomz.api.seller.inquiry.dto.AnswerTemplateDto;
import showroomz.api.seller.inquiry.dto.AnswerTemplateRegisterRequest;
import showroomz.api.seller.inquiry.dto.AnswerTemplateRegisterResponse;
import showroomz.api.seller.inquiry.dto.AnswerTemplateUpdateRequest;
import showroomz.api.seller.inquiry.service.AnswerTemplateService;
import showroomz.api.seller.inquiry.type.MarketInquiryFilterType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/seller/answer-templates")
@RequiredArgsConstructor
public class AnswerTemplateController implements AnswerTemplateControllerDocs {

    private final AnswerTemplateService answerTemplateService;

    @Override
    @PostMapping
    public ResponseEntity<AnswerTemplateRegisterResponse> registerTemplate(
            @Valid @RequestBody AnswerTemplateRegisterRequest request) {
        String sellerEmail = getCurrentSellerEmail();
        AnswerTemplateRegisterResponse response = answerTemplateService.registerTemplate(sellerEmail, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<AnswerTemplateDto>> getTemplates(
            @RequestParam(value = "includeInactive", required = false, defaultValue = "false") Boolean includeInactive,
            @RequestParam(value = "category", required = false) MarketInquiryFilterType category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest) {
        String sellerEmail = getCurrentSellerEmail();
        PageResponse<AnswerTemplateDto> response = answerTemplateService.getTemplates(sellerEmail, includeInactive, category, keyword, pagingRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{templateId}")
    public ResponseEntity<AnswerTemplateDto> getTemplate(
            @PathVariable("templateId") Long templateId) {
        String sellerEmail = getCurrentSellerEmail();
        return ResponseEntity.ok(answerTemplateService.getTemplate(sellerEmail, templateId));
    }

    @Override
    @PutMapping("/{templateId}")
    public ResponseEntity<Void> updateTemplate(
            @PathVariable("templateId") Long templateId,
            @Valid @RequestBody AnswerTemplateUpdateRequest request) {
        String sellerEmail = getCurrentSellerEmail();
        answerTemplateService.updateTemplate(sellerEmail, templateId, request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping
    public ResponseEntity<Void> deleteTemplates(
            @Valid @RequestBody AnswerTemplateDeleteRequest request) {
        String sellerEmail = getCurrentSellerEmail();
        answerTemplateService.deleteTemplates(sellerEmail, request);
        return ResponseEntity.noContent().build();
    }

    private String getCurrentSellerEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ((UserPrincipal) principal).getUsername();
    }
}
