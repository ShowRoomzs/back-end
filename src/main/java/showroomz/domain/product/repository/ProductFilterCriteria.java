package showroomz.domain.product.repository;

import showroomz.domain.filter.type.FilterCondition;
import showroomz.domain.filter.type.FilterType;

import java.util.List;

public record ProductFilterCriteria(
        String key,
        FilterType filterType,
        FilterCondition condition,
        List<String> values,
        Integer minValue,
        Integer maxValue
) {
}
