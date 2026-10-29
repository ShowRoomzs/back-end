package showroomz.api.app.inquiry.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.inquiry.docs.CommonProductInquiryControllerDocs;
import showroomz.api.app.inquiry.dto.InquiryCategoryResponse;
import showroomz.api.app.inquiry.dto.PublicProductInquiryResponse;
import showroomz.api.app.inquiry.service.ProductInquiryService;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CommonProductInquiryController implements CommonProductInquiryControllerDocs {

    private final ProductInquiryService productInquiryService;

    @Override
    @GetMapping("/v1/common/product-inquiries/categories")
    public ResponseEntity<List<InquiryCategoryResponse>> getProductInquiryCategories() {
        return ResponseEntity.ok(productInquiryService.getProductInquiryCategories());
    }

    @Override
    @GetMapping("/v1/common/products/{productId}/inquiries")
    public PageResponse<PublicProductInquiryResponse> getProductInquiries(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("productId") Long productId,
            @RequestParam(value = "type", required = false) ProductInquiryType type,
            @RequestParam(value = "status", required = false) InquiryStatus status,
            @Valid PagingRequest pagingRequest) {
        // 비밀글 본인 열람은 소비자 로그인만 — 셀러 · 인플루언서 토큰의 id 는 소비자 id 가 아니다.
        Long viewerUserId = userPrincipal != null && userPrincipal.getRoleType() == RoleType.USER
                ? userPrincipal.getUserId() : null;
        return productInquiryService.getProductInquiries(productId, type, status, viewerUserId,
                pagingRequest.toPageable());
    }
}
