package showroomz.api.admin.filter.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.admin.filter.DTO.CategoryFilterDto;
import showroomz.api.admin.filter.docs.AdminCategoryFilterSyncControllerDocs;
import showroomz.api.admin.filter.service.FilterService;

@RestController
@RequestMapping("/v1/admin/categories")
@RequiredArgsConstructor
public class CategoryFilterSyncController implements AdminCategoryFilterSyncControllerDocs {

    private final FilterService filterService;

    @Override
    @PostMapping("/{categoryId}/filters")
    public ResponseEntity<?> syncCategoryFilters(
            @PathVariable Long categoryId,
            @RequestBody CategoryFilterDto.SyncRequest request
    ) {
        filterService.syncCategoryFilters(categoryId, request);
        return ResponseEntity.ok(java.util.Map.of("message", "카테고리 필터가 동기화되었습니다."));
    }
}
