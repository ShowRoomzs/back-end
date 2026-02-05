package showroomz.api.admin.filter.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.filter.DTO.FilterDto;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.api.admin.filter.DTO.CategoryFilterDto;
import showroomz.domain.filter.entity.CategoryFilter;
import showroomz.domain.filter.entity.CategoryFilterValue;
import showroomz.domain.filter.entity.Filter;
import showroomz.domain.filter.entity.FilterValue;
import showroomz.domain.filter.repository.CategoryFilterRepository;
import showroomz.domain.filter.repository.CategoryFilterValueRepository;
import showroomz.domain.filter.repository.FilterRepository;
import showroomz.domain.filter.repository.FilterValueRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FilterService {

    private final FilterRepository filterRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryFilterRepository categoryFilterRepository;
    private final CategoryFilterValueRepository categoryFilterValueRepository;
    private final FilterValueRepository filterValueRepository;

    public FilterDto.FilterResponse createFilter(FilterDto.CreateFilterRequest request) {
        if (filterRepository.findByFilterKey(request.getFilterKey()).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_NAME, "이미 존재하는 filterKey입니다.");
        }

        Filter filter = new Filter();
        filter.setFilterKey(request.getFilterKey());
        filter.setLabel(request.getLabel());
        filter.setFilterType(request.getFilterType());
        filter.setCondition(request.getCondition());
        filter.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        filter.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        if (request.getValues() != null) {
            List<FilterValue> values = new ArrayList<>();
            for (FilterDto.FilterValueRequest valueRequest : request.getValues()) {
                FilterValue value = new FilterValue();
                value.setFilter(filter);
                value.setValue(valueRequest.getValue());
                value.setLabel(valueRequest.getLabel());
                value.setExtra(valueRequest.getExtra());
                value.setSortOrder(valueRequest.getSortOrder() != null ? valueRequest.getSortOrder() : 0);
                value.setIsActive(valueRequest.getIsActive() != null ? valueRequest.getIsActive() : true);
                values.add(value);
            }
            filter.setValues(values);
        }

        @SuppressWarnings("null")
        Filter savedFilter = filterRepository.save(filter);

        return toResponse(savedFilter);
    }

    @SuppressWarnings("null")
    public FilterDto.FilterResponse updateFilter(Long filterId, FilterDto.UpdateFilterRequest request) {
        @SuppressWarnings("null")
        Long safeFilterId = filterId;
        Filter filter = filterRepository.findById(safeFilterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "필터를 찾을 수 없습니다."));

        if (request.getLabel() != null) {
            filter.setLabel(request.getLabel());
        }
        if (request.getFilterType() != null) {
            filter.setFilterType(request.getFilterType());
        }
        if (request.getCondition() != null) {
            filter.setCondition(request.getCondition());
        }
        if (request.getSortOrder() != null) {
            filter.setSortOrder(request.getSortOrder());
        }
        if (request.getIsActive() != null) {
            filter.setIsActive(request.getIsActive());
        }

        if (request.getValues() != null) {
            filter.getValues().clear();
            for (FilterDto.FilterValueRequest valueRequest : request.getValues()) {
                FilterValue value = new FilterValue();
                value.setFilter(filter);
                value.setValue(valueRequest.getValue());
                value.setLabel(valueRequest.getLabel());
                value.setExtra(valueRequest.getExtra());
                value.setSortOrder(valueRequest.getSortOrder() != null ? valueRequest.getSortOrder() : 0);
                value.setIsActive(valueRequest.getIsActive() != null ? valueRequest.getIsActive() : true);
                filter.getValues().add(value);
            }
        }

        @SuppressWarnings("null")
        Filter savedFilter = filterRepository.save(filter);
        return toResponse(savedFilter);
    }

    @SuppressWarnings("null")
    public void deleteFilter(Long filterId) {
        @SuppressWarnings("null")
        Long safeFilterId = filterId;
        Filter filter = filterRepository.findById(safeFilterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "필터를 찾을 수 없습니다."));
        @SuppressWarnings("null")
        Long deleteId = safeFilterId;
        categoryFilterRepository.deleteByFilter_Id(deleteId);
        @SuppressWarnings("null")
        Filter deleteTarget = filter;
        filterRepository.delete(deleteTarget);
    }

    @SuppressWarnings("null")
    public void syncCategoryFilters(Long categoryId, CategoryFilterDto.SyncRequest request) {
        Category category = categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        categoryFilterValueRepository.deleteByCategoryFilter_Category_CategoryId(categoryId);
        categoryFilterRepository.deleteByCategory_CategoryId(categoryId);

        if (request == null || request.getFilters() == null || request.getFilters().isEmpty()) {
            return;
        }

        for (CategoryFilterDto.FilterMapping mapping : request.getFilters()) {
            @SuppressWarnings("null")
            Long filterId = mapping.getFilterId();
            Filter filter = filterRepository.findById(filterId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "필터를 찾을 수 없습니다."));

            CategoryFilter categoryFilter = new CategoryFilter();
            categoryFilter.setCategory(category);
            categoryFilter.setFilter(filter);
            CategoryFilter savedMapping = categoryFilterRepository.save(categoryFilter);

            if (mapping.getSelectedValueIds() != null && !mapping.getSelectedValueIds().isEmpty()) {
                @SuppressWarnings("null")
                List<Long> selectedValueIds = mapping.getSelectedValueIds();
                List<FilterValue> values = filterValueRepository.findAllById(selectedValueIds);
                for (FilterValue value : values) {
                    if (value.getFilter() == null || !value.getFilter().getId().equals(filter.getId())) {
                        continue;
                    }
                    CategoryFilterValue selected = new CategoryFilterValue();
                    selected.setCategoryFilter(savedMapping);
                    selected.setFilterValue(value);
                    categoryFilterValueRepository.save(selected);
                }
            }
        }
    }

    private FilterDto.FilterResponse toResponse(Filter filter) {
        List<FilterDto.FilterValueResponse> values = filter.getValues().stream()
                .map(value -> FilterDto.FilterValueResponse.builder()
                        .id(value.getId())
                        .value(value.getValue())
                        .label(value.getLabel())
                        .extra(value.getExtra())
                        .sortOrder(value.getSortOrder())
                        .isActive(value.getIsActive())
                        .build())
                .sorted(java.util.Comparator.comparingInt(item -> item.getSortOrder() != null ? item.getSortOrder() : 0))
                .toList();

        return FilterDto.FilterResponse.builder()
                .id(filter.getId())
                .filterKey(filter.getFilterKey())
                .label(filter.getLabel())
                .filterType(filter.getFilterType())
                .condition(filter.getCondition())
                .sortOrder(filter.getSortOrder())
                .isActive(filter.getIsActive())
                .values(values)
                .build();
    }
}
