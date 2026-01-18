package showroomz.api.common.category.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.category.DTO.CategoryDto;
import showroomz.api.admin.category.service.CategoryService;
import showroomz.api.common.docs.CommonCategoryControllerDocs;

import java.util.List;

@RestController
@RequestMapping("/v1/common/categories")
@RequiredArgsConstructor
public class CommonCategoryController implements CommonCategoryControllerDocs {

    private final CategoryService categoryService;

    @Override
    @GetMapping
    public ResponseEntity<List<CategoryDto.CategoryResponse>> getAllCategories() {
        List<CategoryDto.CategoryResponse> response = categoryService.getAllCategories();
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{categoryId}")
    public ResponseEntity<CategoryDto.CategoryResponse> getCategory(@PathVariable("categoryId") Long categoryId) {
        CategoryDto.CategoryResponse response = categoryService.getCategory(categoryId);
        return ResponseEntity.ok(response);
    }
}
