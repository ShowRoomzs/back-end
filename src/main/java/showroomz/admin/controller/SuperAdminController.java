package showroomz.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.Market.DTO.MarketDto;
import showroomz.Market.service.MarketService;
import showroomz.Market.type.MarketImageStatus;
import showroomz.auth.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.product.DTO.CategoryDto;
import showroomz.product.service.CategoryService;
import showroomz.swaggerDocs.SuperAdminControllerDocs;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class SuperAdminController implements SuperAdminControllerDocs {

    private final MarketService marketService;
    private final CategoryService categoryService;

    @Override
    @PatchMapping("/markets/{marketId}/image-status")
    public ResponseEntity<Void> updateMarketImageStatus(
            @PathVariable Long marketId,
            @RequestBody MarketDto.UpdateImageStatusRequest request) {
        
        MarketImageStatus status;
        try {
            status = MarketImageStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        marketService.updateMarketImageStatus(marketId, status);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/categories")
    public ResponseEntity<CategoryDto.CreateCategoryResponse> createCategory(
            @Valid @RequestBody CategoryDto.CreateCategoryRequest request) {
        CategoryDto.CreateCategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping("/categories/{categoryId}")
    public ResponseEntity<CategoryDto.CategoryResponse> getCategory(@PathVariable("categoryId") Long categoryId) {
        CategoryDto.CategoryResponse response = categoryService.getCategory(categoryId);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/categories")
    public ResponseEntity<java.util.List<CategoryDto.CategoryResponse>> getAllCategories() {
        java.util.List<CategoryDto.CategoryResponse> response = categoryService.getAllCategories();
        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/categories/{categoryId}")
    public ResponseEntity<CategoryDto.UpdateCategoryResponse> updateCategory(
            @PathVariable("categoryId") Long categoryId,
            @Valid @RequestBody CategoryDto.UpdateCategoryRequest request) {
        CategoryDto.UpdateCategoryResponse response = categoryService.updateCategory(categoryId, request);
        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<?> deleteCategory(@PathVariable("categoryId") Long categoryId) {
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.ok(java.util.Map.of("message", "카테고리가 성공적으로 삭제되었습니다."));
    }
}

