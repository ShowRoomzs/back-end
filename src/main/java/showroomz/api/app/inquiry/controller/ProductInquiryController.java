package showroomz.api.app.inquiry.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.inquiry.dto.ProductInquiryListResponse;
import showroomz.api.app.inquiry.dto.ProductInquiryRegisterRequest;
import showroomz.api.app.inquiry.dto.ProductInquiryRegisterResponse;
import showroomz.api.app.inquiry.dto.ProductInquiryUpdateRequest;
import showroomz.api.app.inquiry.service.ProductInquiryService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@RestController
@RequiredArgsConstructor
public class ProductInquiryController {

    private final ProductInquiryService productInquiryService;

    @PostMapping("/v1/user/products/{productId}/inquiries")
    public ResponseEntity<ProductInquiryRegisterResponse> registerInquiry(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("productId") Long productId,
            @Valid @RequestBody ProductInquiryRegisterRequest request) {
        Long inquiryId = productInquiryService.registerInquiry(
                userPrincipal.getUserId(), productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProductInquiryRegisterResponse.builder().inquiryId(inquiryId).build());
    }

    @GetMapping("/v1/user/product-inquiries")
    public PageResponse<ProductInquiryListResponse> getMyInquiries(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid PagingRequest pagingRequest) {
        return productInquiryService.getMyInquiries(
                userPrincipal.getUserId(), pagingRequest.toPageable());
    }

    @PatchMapping("/v1/user/product-inquiries/{inquiryId}")
    public ResponseEntity<Void> updateInquiry(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody ProductInquiryUpdateRequest request) {
        productInquiryService.updateInquiry(userPrincipal.getUserId(), inquiryId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/v1/user/product-inquiries/{inquiryId}")
    public ResponseEntity<Void> deleteInquiry(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("inquiryId") Long inquiryId) {
        productInquiryService.deleteInquiry(userPrincipal.getUserId(), inquiryId);
        return ResponseEntity.noContent().build();
    }
}
