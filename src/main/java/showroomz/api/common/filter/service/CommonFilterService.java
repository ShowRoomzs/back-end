package showroomz.api.common.filter.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.filter.DTO.FilterDto;
import showroomz.domain.filter.entity.CategoryFilter;
import showroomz.domain.filter.entity.CategoryFilterValue;
import showroomz.domain.filter.entity.Filter;
import showroomz.domain.filter.entity.FilterValue;
import showroomz.domain.filter.repository.CategoryFilterRepository;
import showroomz.domain.filter.repository.CategoryFilterValueRepository;
import showroomz.domain.filter.repository.FilterRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommonFilterService {

    private final FilterRepository filterRepository;
    private final CategoryFilterRepository categoryFilterRepository;
    private final CategoryFilterValueRepository categoryFilterValueRepository;

    public List<FilterDto.FilterResponse> getFilters(String filterKey, Long categoryId) {
        List<Filter> filters;
        if (filterKey != null && !filterKey.isBlank()) {
            filters = filterRepository.findByFilterKey(filterKey).stream()
                    .filter(Filter::getIsActive)
                    .sorted(java.util.Comparator.comparingInt(filter -> filter.getSortOrder() != null ? filter.getSortOrder() : 0))
                    .toList();
        } else if (categoryId != null) {
            List<CategoryFilter> mappings = categoryFilterRepository.findByCategory_CategoryId(categoryId);
            filters = mappings.stream()
                    .map(CategoryFilter::getFilter)
                    .filter(Filter::getIsActive)
                    .distinct()
                    .sorted(java.util.Comparator.comparingInt(filter -> filter.getSortOrder() != null ? filter.getSortOrder() : 0))
                    .collect(Collectors.toList());
        } else {
            filters = filterRepository.findByIsActiveTrueOrderBySortOrderAsc();
        }

        return filters.stream()
                .map(filter -> toResponse(filter, categoryId))
                .toList();
    }

    private FilterDto.FilterResponse toResponse(Filter filter, Long categoryId) {
        List<Long> selectedValueIds = List.of();
        if (categoryId != null) {
            selectedValueIds = categoryFilterValueRepository
                    .findByCategoryFilter_Category_CategoryId(categoryId).stream()
                    .filter(item -> item.getCategoryFilter().getFilter().getId().equals(filter.getId()))
                    .map(CategoryFilterValue::getFilterValue)
                    .map(FilterValue::getId)
                    .toList();
        }
        final List<Long> effectiveSelectedIds = selectedValueIds;

        List<FilterDto.FilterValueResponse> values = new ArrayList<>();
        filter.getValues().stream()
                .filter(FilterValue::getIsActive)
                .filter(value -> effectiveSelectedIds.isEmpty() || effectiveSelectedIds.contains(value.getId()))
                .sorted(java.util.Comparator.comparingInt(value -> value.getSortOrder() != null ? value.getSortOrder() : 0))
                .forEach(value -> values.add(FilterDto.FilterValueResponse.builder()
                        .id(value.getId())
                        .value(value.getValue())
                        .label(value.getLabel())
                        .extra(value.getExtra())
                        .sortOrder(value.getSortOrder())
                        .isActive(value.getIsActive())
                        .build()));

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
