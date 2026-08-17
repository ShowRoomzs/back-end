package showroomz.api.seller.inquiry.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.inquiry.docs.SellerInquiryControllerDocs;
import showroomz.api.seller.inquiry.dto.ProductInquiryDetailResponse;
import showroomz.api.seller.inquiry.dto.SellerInquiryAnswerRequest;
import showroomz.api.seller.inquiry.dto.SellerInquiryDeleteRequest;
import showroomz.api.seller.inquiry.dto.SellerInquiryListResponse;
import showroomz.api.seller.inquiry.dto.SellerInquirySearchCondition;
import showroomz.api.seller.inquiry.service.SellerInquiryService;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/seller/inquiries")
@RequiredArgsConstructor
public class SellerInquiryController implements SellerInquiryControllerDocs {

    private final SellerInquiryService sellerInquiryService;

    @Override
    @GetMapping
    public ResponseEntity<SellerInquiryListResponse> getInquiries(
            @ModelAttribute SellerInquirySearchCondition condition,
            @ModelAttribute PagingRequest pagingRequest) {
        String sellerEmail = getCurrentSellerEmail();
        SellerInquiryListResponse response = sellerInquiryService.getMarketInquiries(sellerEmail, condition, pagingRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{inquiryId}")
    public ResponseEntity<ProductInquiryDetailResponse> getInquiryDetail(
            @PathVariable("inquiryId") Long inquiryId,
            @ModelAttribute SellerInquirySearchCondition condition) {
        String sellerEmail = getCurrentSellerEmail();
        ProductInquiryDetailResponse response = sellerInquiryService.getInquiryDetail(sellerEmail, inquiryId, condition);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{inquiryId}/answer")
    public ResponseEntity<Void> registerAnswer(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody SellerInquiryAnswerRequest request) {
        String sellerEmail = getCurrentSellerEmail();
        sellerInquiryService.registerAnswer(sellerEmail, inquiryId, request.getAnswerContent());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/{inquiryId}/answer")
    public ResponseEntity<Void> modifyAnswer(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody SellerInquiryAnswerRequest request) {
        String sellerEmail = getCurrentSellerEmail();
        sellerInquiryService.modifyAnswer(sellerEmail, inquiryId, request.getAnswerContent());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{inquiryId}/delete-request")
    public ResponseEntity<Void> requestDelete(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody SellerInquiryDeleteRequest request) {
        String sellerEmail = getCurrentSellerEmail();
        sellerInquiryService.requestDelete(sellerEmail, inquiryId, request);
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
