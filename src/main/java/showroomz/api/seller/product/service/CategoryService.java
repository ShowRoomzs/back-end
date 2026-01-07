package showroomz.api.seller.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.global.error.exception.ErrorCode;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.seller.product.DTO.CategoryDto;
import showroomz.domain.product.entity.Category;
import showroomz.api.seller.product.repository.CategoryRepository;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

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
                        .build())
                .toList();
    }

    @Transactional
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
    public void deleteCategory(Long categoryId) {
        Category category = categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        // TODO: 상품에서 해당 카테고리를 사용 중인지 확인 (추후 구현)
        // 현재는 바로 삭제

        categoryRepository.delete(category);
    }
}

