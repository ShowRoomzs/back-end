package showroomz.api.seller.inquiry.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.inquiry.docs.SellerInquiryControllerDocs;
import showroomz.api.seller.inquiry.dto.SellerInquiryAnswerRequest;
import showroomz.api.seller.inquiry.service.SellerInquiryService;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/seller/inquiries")
@RequiredArgsConstructor
@Hidden
public class SellerInquiryController implements SellerInquiryControllerDocs {

    private final SellerInquiryService sellerInquiryService;

    @Override
    @PatchMapping("/{inquiryId}/answer")
    public ResponseEntity<Void> registerAnswer(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody SellerInquiryAnswerRequest request) {
        String sellerEmail = getCurrentSellerEmail();
        sellerInquiryService.registerAnswer(sellerEmail, inquiryId, request.getAnswerContent());
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
