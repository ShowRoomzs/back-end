package showroomz.api.seller.category.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.category.DTO.CategoryDto;
import showroomz.api.seller.category.docs.CategoryControllerDocs;
import showroomz.api.seller.category.service.CategoryService;

import java.util.List;

@RestController("sellerCategoryController")
@RequestMapping("/v1/seller/category")
@RequiredArgsConstructor
public class CategoryController implements CategoryControllerDocs {

    private final CategoryService categoryService;

    @Override
    @GetMapping
    public ResponseEntity<List<CategoryDto.CategoryResponse>> getAllCategories() {
        List<CategoryDto.CategoryResponse> response = categoryService.getAllCategories();
        return ResponseEntity.ok(response);
    }
}
