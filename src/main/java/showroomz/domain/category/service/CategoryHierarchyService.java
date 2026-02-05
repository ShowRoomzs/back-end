package showroomz.domain.category.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.domain.category.repository.CategoryRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryHierarchyService {

    private final CategoryRepository categoryRepository;
    private volatile Map<Long, List<Long>> descendantCategoryIds = Map.of();

    @PostConstruct
    public void loadCategoryHierarchy() {
        refreshCategoryHierarchy();
    }

    @Transactional(readOnly = true)
    public void refreshCategoryHierarchy() {
        List<CategoryRepository.CategoryIdParentId> rows = categoryRepository.findAllIdWithParentId();
        Set<Long> allIds = rows.stream()
                .map(CategoryRepository.CategoryIdParentId::getId)
                .collect(Collectors.toSet());

        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (CategoryRepository.CategoryIdParentId row : rows) {
            Long parentId = row.getParentId();
            if (parentId == null) {
                continue;
            }
            childrenMap.computeIfAbsent(parentId, key -> new ArrayList<>())
                    .add(row.getId());
        }
        for (Long id : allIds) {
            childrenMap.putIfAbsent(id, List.of());
        }

        Map<Long, List<Long>> descendants = new HashMap<>();
        for (Long id : allIds) {
            collectDescendants(id, childrenMap, descendants);
        }

        Map<Long, List<Long>> snapshot = new HashMap<>();
        for (Map.Entry<Long, List<Long>> entry : descendants.entrySet()) {
            snapshot.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        descendantCategoryIds = Map.copyOf(snapshot);
    }

    public List<Long> getAllSubCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return List.of();
        }
        List<Long> ids = descendantCategoryIds.get(categoryId);
        if (ids == null) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return ids;
    }

    private List<Long> collectDescendants(
            Long categoryId,
            Map<Long, List<Long>> childrenMap,
            Map<Long, List<Long>> memo
    ) {
        List<Long> cached = memo.get(categoryId);
        if (cached != null) {
            return cached;
        }
        List<Long> result = new ArrayList<>();
        result.add(categoryId);
        for (Long childId : childrenMap.getOrDefault(categoryId, List.of())) {
            result.addAll(collectDescendants(childId, childrenMap, memo));
        }
        memo.put(categoryId, result);
        return result;
    }
}
