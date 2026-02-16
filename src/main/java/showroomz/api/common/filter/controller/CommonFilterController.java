package showroomz.api.common.filter.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.filter.DTO.FilterDto;
import showroomz.api.common.filter.docs.CommonFilterControllerDocs;
import showroomz.api.common.filter.service.CommonFilterService;

import java.util.List;

@RestController
@RequestMapping("/v1/common/filters")
@RequiredArgsConstructor
public class CommonFilterController implements CommonFilterControllerDocs {

    private final CommonFilterService filterService;

    @Override
    @GetMapping
    public ResponseEntity<List<FilterDto.FilterResponse>> getFilters(
            @RequestParam(required = false) String filterKey,
            @RequestParam(required = false) Long categoryId
    ) {
        List<FilterDto.FilterResponse> response = filterService.getFilters(filterKey, categoryId);
        return ResponseEntity.ok(response);
    }
}
