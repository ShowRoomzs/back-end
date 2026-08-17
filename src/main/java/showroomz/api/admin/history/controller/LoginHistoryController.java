package showroomz.api.admin.history.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.admin.history.DTO.LocationFilterResponse;
import showroomz.api.admin.history.DTO.LoginHistoryResponse;
import showroomz.api.admin.history.DTO.LoginHistorySearchCondition;
import showroomz.api.admin.history.docs.LoginHistoryControllerDocs;
import showroomz.api.admin.history.service.LoginHistoryService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/history")
@RequiredArgsConstructor
// [기획 제외] 보류된 기능 — 신규 작업/리팩터링 대상 아님. 상세: CLAUDE.md
@Hidden
public class LoginHistoryController implements LoginHistoryControllerDocs {

    private final LoginHistoryService loginHistoryService;

    @Override
    @GetMapping("/login")
    public ResponseEntity<PageResponse<LoginHistoryResponse>> getLoginHistories(
            @ModelAttribute LoginHistorySearchCondition condition,
            @ModelAttribute PagingRequest pagingRequest
    ) {
        PageResponse<LoginHistoryResponse> response = loginHistoryService.getLoginHistories(condition, pagingRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/login/filters/locations")
    public ResponseEntity<List<LocationFilterResponse>> getLocationFilters() {
        List<LocationFilterResponse> locations = loginHistoryService.getLocationOptions();
        return ResponseEntity.ok(locations);
    }
}
