package showroomz.api.admin.filter.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.docs.AdminFilterControllerDocs;
import showroomz.api.admin.filter.DTO.FilterDto;
import showroomz.api.admin.filter.service.FilterService;

@RestController
@RequestMapping("/v1/admin/filters")
@RequiredArgsConstructor
public class FilterController implements AdminFilterControllerDocs {

    private final FilterService filterService;

    @Override
    @PostMapping
    public ResponseEntity<FilterDto.FilterResponse> createFilter(@RequestBody FilterDto.CreateFilterRequest request) {
        FilterDto.FilterResponse response = filterService.createFilter(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @PatchMapping("/{filterId}")
    public ResponseEntity<FilterDto.FilterResponse> updateFilter(
            @PathVariable Long filterId,
            @RequestBody FilterDto.UpdateFilterRequest request
    ) {
        FilterDto.FilterResponse response = filterService.updateFilter(filterId, request);
        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping("/{filterId}")
    public ResponseEntity<?> deleteFilter(@PathVariable Long filterId) {
        filterService.deleteFilter(filterId);
        return ResponseEntity.ok(java.util.Map.of("message", "필터가 성공적으로 삭제되었습니다."));
    }
}
