package showroomz.api.admin.product.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.docs.AdminProductControllerDocs;
import showroomz.api.admin.product.DTO.AdminProductDto;
import showroomz.api.admin.product.service.AdminProductService;

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
}
