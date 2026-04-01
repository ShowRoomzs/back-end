package showroomz.api.seller.inquiry.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.inquiry.docs.AnswerTemplateControllerDocs;
import showroomz.api.seller.inquiry.dto.AnswerTemplateRegisterRequest;
import showroomz.api.seller.inquiry.dto.AnswerTemplateRegisterResponse;
import showroomz.api.seller.inquiry.service.AnswerTemplateService;
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

    private String getCurrentSellerEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ((UserPrincipal) principal).getUsername();
    }
}
