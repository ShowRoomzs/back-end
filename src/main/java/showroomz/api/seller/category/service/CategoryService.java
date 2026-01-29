package showroomz.api.seller.category.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.category.DTO.CategoryDto;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.domain.category.service.CategoryHierarchyService;

import java.util.List;

@Service("sellerCategoryService")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryHierarchyService categoryHierarchyService;

    /**
     * 전체 카테고리 목록 조회
     * 판매자가 상품 등록 시 사용할 수 있는 모든 카테고리를 반환합니다.
     */
    public List<CategoryDto.CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::convertToResponse)
                .toList();
    }

    /**
     * Category 엔티티를 CategoryResponse DTO로 변환
     */
    private CategoryDto.CategoryResponse convertToResponse(Category category) {
        return CategoryDto.CategoryResponse.builder()
                .categoryId(category.getCategoryId())
                .name(category.getName())
                .order(category.getOrder())
                .iconUrl(category.getIconUrl())
                .parentId(category.getParent() != null ? category.getParent().getCategoryId() : null)
                .build();
    }
    
    /**
     * 특정 카테고리 ID의 모든 하위 카테고리 ID 리스트를 재귀적으로 조회
     * (자신 포함)
     * @param categoryId 카테고리 ID
     * @return 자신과 모든 하위 카테고리 ID 리스트
     */
    public List<Long> getAllSubCategoryIds(Long categoryId) {
        return categoryHierarchyService.getAllSubCategoryIds(categoryId);
    }
}
