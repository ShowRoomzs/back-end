package showroomz.api.app.inquiry.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.inquiry.dto.ProductInquiryResponse;
import showroomz.api.app.inquiry.docs.ProductInquiryControllerDocs;
import showroomz.api.app.inquiry.dto.ProductInquiryRegisterRequest;
import showroomz.api.app.inquiry.dto.ProductInquiryRegisterResponse;
import showroomz.api.app.inquiry.dto.ProductInquiryUpdateRequest;
import showroomz.api.app.inquiry.service.ProductInquiryService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@RestController
@RequiredArgsConstructor
public class ProductInquiryController implements ProductInquiryControllerDocs {

    private final ProductInquiryService productInquiryService;

    @PostMapping("/v1/user/products/{productId}/inquiries")
    @Override
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
    @Override
    public PageResponse<ProductInquiryResponse> getMyInquiries(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid PagingRequest pagingRequest) {
        return productInquiryService.getMyInquiries(
                userPrincipal.getUserId(), pagingRequest.toPageable());
    }

    @GetMapping("/v1/user/product-inquiries/{inquiryId}")
    @Override
    public ProductInquiryResponse getInquiryDetail(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("inquiryId") Long inquiryId) {
        return productInquiryService.getInquiryDetail(userPrincipal.getUserId(), inquiryId);
    }

    @PutMapping("/v1/user/product-inquiries/{inquiryId}")
    @Override
    public ResponseEntity<Void> updateInquiry(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody ProductInquiryUpdateRequest request) {
        productInquiryService.updateInquiry(userPrincipal.getUserId(), inquiryId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/v1/user/product-inquiries/{inquiryId}")
    @Override
    public ResponseEntity<Void> deleteInquiry(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("inquiryId") Long inquiryId) {
        productInquiryService.deleteInquiry(userPrincipal.getUserId(), inquiryId);
        return ResponseEntity.noContent().build();
    }
}
