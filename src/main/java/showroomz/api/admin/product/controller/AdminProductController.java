package showroomz.api.admin.product.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import showroomz.api.admin.product.DTO.AdminProductDto;
import showroomz.api.admin.product.docs.AdminProductControllerDocs;
import showroomz.api.admin.product.service.AdminProductService;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.common.product.dto.ProductProcessingHistoryDto;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController implements AdminProductControllerDocs {

    private final AdminProductService adminProductService;

    @Override
    @PatchMapping("/{productId}/recommendation")
    public ResponseEntity<AdminProductDto.UpdateRecommendationResponse> updateRecommendation(
            @PathVariable Long productId,
            @Valid @RequestBody AdminProductDto.UpdateRecommendationRequest request
    ) {
        AdminProductDto.UpdateRecommendationResponse response = adminProductService.updateRecommendation(
                productId, request);
        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/{productId}/display-status")
    public ResponseEntity<AdminProductDto.UpdateDisplayStatusResponse> updateDisplayStatus(
            @PathVariable Long productId,
            @Valid @RequestBody AdminProductDto.UpdateDisplayStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        AdminProductDto.UpdateDisplayStatusResponse response = adminProductService.updateDisplayStatus(
                productId, request, requireAdminSellerId(principal));
        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/display-status")
    public ResponseEntity<AdminProductDto.BulkUpdateDisplayStatusResponse> bulkUpdateDisplayStatus(
            @Valid @RequestBody AdminProductDto.BulkUpdateDisplayStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        AdminProductDto.BulkUpdateDisplayStatusResponse response = adminProductService.bulkUpdateDisplayStatus(
                request, requireAdminSellerId(principal));
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{productId}/processing-history")
    public ResponseEntity<ProductProcessingHistoryDto.HistoryListResponse> getProcessingHistory(
            @PathVariable Long productId
    ) {
        return ResponseEntity.ok(adminProductService.getProcessingHistory(productId));
    }

    private Long requireAdminSellerId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return principal.getUserId();
    }
}
