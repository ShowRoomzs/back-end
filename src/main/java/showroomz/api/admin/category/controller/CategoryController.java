package showroomz.api.admin.category.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import showroomz.api.admin.category.DTO.CategoryDto;
import showroomz.api.admin.category.service.CategoryService;
import showroomz.api.admin.docs.AdminCategoryControllerDocs;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class CategoryController implements AdminCategoryControllerDocs {

    private final CategoryService categoryService;

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

