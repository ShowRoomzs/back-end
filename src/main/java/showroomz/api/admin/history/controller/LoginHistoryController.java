package showroomz.api.admin.history.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.docs.LoginHistoryControllerDocs;
import showroomz.api.admin.history.DTO.LocationFilterResponse;
import showroomz.api.admin.history.DTO.LoginHistoryResponse;
import showroomz.api.admin.history.DTO.LoginHistorySearchCondition;
import showroomz.api.admin.history.service.LoginHistoryService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/history")
@RequiredArgsConstructor
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
