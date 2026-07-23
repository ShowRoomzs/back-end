package showroomz.api.admin.creator.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.creator.dto.CreatorApplicationRejectRequest;
import showroomz.api.admin.creator.dto.CreatorApplicationResponse;
import showroomz.api.admin.creator.docs.AdminCreatorApplicationControllerDocs;
import showroomz.api.creator.auth.service.CreatorApplicationService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/admin/creator/applications")
@RequiredArgsConstructor
public class AdminCreatorApplicationController implements AdminCreatorApplicationControllerDocs {

    private final CreatorApplicationService creatorApplicationService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<CreatorApplicationResponse>> getApplications(
            @ParameterObject @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(creatorApplicationService.getApplications(pagingRequest));
    }

    @Override
    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<Void> approveApplication(@PathVariable("applicationId") Long applicationId) {
        creatorApplicationService.approve(applicationId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<Void> rejectApplication(
            @PathVariable("applicationId") Long applicationId,
            @Valid @RequestBody CreatorApplicationRejectRequest request) {
        creatorApplicationService.reject(applicationId, request);
        return ResponseEntity.noContent().build();
    }
}
