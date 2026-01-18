package showroomz.api.admin.category.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.global.error.exception.ErrorCode;
import showroomz.api.admin.category.DTO.CategoryDto;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.domain.filter.entity.CategoryFilter;
import showroomz.domain.filter.entity.Filter;
import showroomz.domain.filter.entity.FilterValue;
import showroomz.domain.filter.repository.CategoryFilterRepository;
import showroomz.domain.filter.repository.CategoryFilterValueRepository;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryFilterRepository categoryFilterRepository;
    private final CategoryFilterValueRepository categoryFilterValueRepository;

    @SuppressWarnings("null")
    public CategoryDto.CreateCategoryResponse createCategory(CategoryDto.CreateCategoryRequest request) {
        // 카테고리명 중복 체크
        if (categoryRepository.existsByName(request.getName())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_NAME);
        }

        // parentId가 제공된 경우 부모 카테고리 존재 여부 확인
        Category parent = null;
        if (request.getParentId() != null) {
            parent = categoryRepository.findByCategoryId(request.getParentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        }

        // 카테고리 생성
        Category category = new Category();
        category.setName(request.getName());
        category.setOrder(request.getOrder() != null ? request.getOrder() : 0);
        category.setIconUrl(request.getIconUrl());
        category.setParent(parent);

        @SuppressWarnings("null")
        Category savedCategory = categoryRepository.save(category);

        return CategoryDto.CreateCategoryResponse.builder()
                .categoryId(savedCategory.getCategoryId())
                .name(savedCategory.getName())
                .order(savedCategory.getOrder())
                .parentId(savedCategory.getParent() != null ? savedCategory.getParent().getCategoryId() : null)
                .message("카테고리가 성공적으로 생성되었습니다.")
                .build();
    }

    @Transactional(readOnly = true)
    public CategoryDto.CategoryResponse getCategory(Long categoryId) {
        Category category = categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        return CategoryDto.CategoryResponse.builder()
                .categoryId(category.getCategoryId())
                .name(category.getName())
                .order(category.getOrder())
                .iconUrl(category.getIconUrl())
                .parentId(category.getParent() != null ? category.getParent().getCategoryId() : null)
                .filters(getFiltersForCategory(category.getCategoryId()))
                .build();
    }

    @Transactional(readOnly = true)
    public java.util.List<CategoryDto.CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(category -> CategoryDto.CategoryResponse.builder()
                        .categoryId(category.getCategoryId())
                        .name(category.getName())
                        .order(category.getOrder())
                        .iconUrl(category.getIconUrl())
                        .parentId(category.getParent() != null ? category.getParent().getCategoryId() : null)
                        .filters(getFiltersForCategory(category.getCategoryId()))
                        .build())
                .toList();
    }

    @Transactional
    @SuppressWarnings("null")
    public CategoryDto.UpdateCategoryResponse updateCategory(Long categoryId, CategoryDto.UpdateCategoryRequest request) {
        Category category = categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        // 카테고리명 변경 및 중복 체크 (이름이 변경된 경우에만)
        if (request.getName() != null && !request.getName().equals(category.getName())) {
            if (categoryRepository.existsByName(request.getName())) {
                throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_NAME);
            }
            category.setName(request.getName());
        }

        // 순서 업데이트
        if (request.getOrder() != null) {
            category.setOrder(request.getOrder());
        }

        // 아이콘 URL 업데이트
        if (request.getIconUrl() != null) {
            category.setIconUrl(request.getIconUrl());
        }

        Category savedCategory = categoryRepository.save(category);

        return CategoryDto.UpdateCategoryResponse.builder()
                .categoryId(savedCategory.getCategoryId())
                .name(savedCategory.getName())
                .order(savedCategory.getOrder())
                .iconUrl(savedCategory.getIconUrl())
                .parentId(savedCategory.getParent() != null ? savedCategory.getParent().getCategoryId() : null)
                .message("카테고리가 성공적으로 수정되었습니다.")
                .build();
    }

    @Transactional
    @SuppressWarnings("null")
    public void deleteCategory(Long categoryId) {
        Category category = categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        // 해당 카테고리와 모든 하위 카테고리를 사용하는 상품 조회
        java.util.List<Long> categoryIdsToCheck = getAllCategoryIdsIncludingChildren(category);
        
        for (Long id : categoryIdsToCheck) {
            java.util.List<Product> productsUsingCategory = productRepository.findByCategory_CategoryId(id);
            if (!productsUsingCategory.isEmpty()) {
                // 첫 번째 상품 ID를 사용하여 에러 메시지 생성
                Long firstProductId = productsUsingCategory.get(0).getProductId();
                String errorMessage = String.format("상품 ID %d와 연결되어있어 해당 카테고리를 삭제할 수 없습니다.", firstProductId);
                throw new BusinessException(ErrorCode.CATEGORY_IN_USE, errorMessage);
            }
        }

        // cascade로 인해 하위 카테고리도 자동으로 삭제됨
        @SuppressWarnings("null")
        Category deleteTarget = category;
        categoryRepository.delete(deleteTarget);
    }
    
    /**
     * 카테고리와 모든 하위 카테고리 ID를 재귀적으로 수집
     */
    private java.util.List<Long> getAllCategoryIdsIncludingChildren(Category category) {
        java.util.List<Long> categoryIds = new java.util.ArrayList<>();
        categoryIds.add(category.getCategoryId());
        
        // 하위 카테고리들을 재귀적으로 수집
        for (Category child : category.getChildren()) {
            categoryIds.addAll(getAllCategoryIdsIncludingChildren(child));
        }
        
        return categoryIds;
    }
    
    /**
     * 특정 카테고리 ID의 모든 하위 카테고리 ID 리스트를 재귀적으로 조회
     * (자신 포함)
     * @param categoryId 카테고리 ID
     * @return 자신과 모든 하위 카테고리 ID 리스트
     */
    @Transactional(readOnly = true)
    public java.util.List<Long> getAllSubCategoryIds(Long categoryId) {
        Category category = categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        
        return getAllCategoryIdsIncludingChildren(category);
    }

    private java.util.List<CategoryDto.FilterInfo> getFiltersForCategory(Long categoryId) {
        java.util.List<CategoryFilter> mappings = categoryFilterRepository.findByCategory_CategoryId(categoryId);

        return mappings.stream()
                .map(CategoryFilter::getFilter)
                .filter(filter -> Boolean.TRUE.equals(filter.getIsActive()))
                .distinct()
                .sorted(java.util.Comparator.comparingInt(filter -> filter.getSortOrder() != null ? filter.getSortOrder() : 0))
                .map(filter -> toFilterInfo(filter, categoryId))
                .toList();
    }

    private CategoryDto.FilterInfo toFilterInfo(Filter filter, Long categoryId) {
        java.util.List<Long> selectedValueIds = categoryFilterValueRepository
                .findByCategoryFilter_Category_CategoryId(categoryId).stream()
                .filter(item -> item.getCategoryFilter().getFilter().getId().equals(filter.getId()))
                .map(item -> item.getFilterValue().getId())
                .toList();

        java.util.List<FilterValue> values = filter.getValues().stream()
                .filter(FilterValue::getIsActive)
                .filter(value -> selectedValueIds.isEmpty() || selectedValueIds.contains(value.getId()))
                .sorted(java.util.Comparator.comparingInt(value -> value.getSortOrder() != null ? value.getSortOrder() : 0))
                .toList();

        java.util.List<CategoryDto.FilterValueInfo> valueInfos = values.stream()
                .map(this::toFilterValueInfo)
                .toList();

        return CategoryDto.FilterInfo.builder()
                .id(filter.getId())
                .filterKey(filter.getFilterKey())
                .label(filter.getLabel())
                .filterType(filter.getFilterType())
                .condition(filter.getCondition())
                .sortOrder(filter.getSortOrder())
                .isActive(filter.getIsActive())
                .values(valueInfos)
                .build();
    }

    private CategoryDto.FilterValueInfo toFilterValueInfo(FilterValue value) {
        return CategoryDto.FilterValueInfo.builder()
                .id(value.getId())
                .value(value.getValue())
                .label(value.getLabel())
                .extra(value.getExtra())
                .sortOrder(value.getSortOrder())
                .isActive(value.getIsActive())
                .build();
    }
}

