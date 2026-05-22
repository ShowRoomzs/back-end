package showroomz.api.admin.creator.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.creator.dto.CreatorApplicationRejectRequest;
import showroomz.api.admin.creator.dto.CreatorApplicationResponse;
import showroomz.api.admin.creator.docs.AdminCreatorApplicationControllerDocs;
import showroomz.api.creator.auth.service.CreatorApplicationService;

@RestController
@RequestMapping("/v1/admin/creator/applications")
@RequiredArgsConstructor
public class AdminCreatorApplicationController implements AdminCreatorApplicationControllerDocs {

    private final CreatorApplicationService creatorApplicationService;

    @Override
    @GetMapping
    public ResponseEntity<Page<CreatorApplicationResponse>> getApplications(
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(creatorApplicationService.getApplications(pageable));
    }

    @Override
    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<Void> approveApplication(@PathVariable Long applicationId) {
        creatorApplicationService.approve(applicationId);
        return ResponseEntity.ok().build();
    }

    @Override
    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<Void> rejectApplication(
            @PathVariable Long applicationId,
            @Valid @RequestBody CreatorApplicationRejectRequest request) {
        creatorApplicationService.reject(applicationId, request);
        return ResponseEntity.ok().build();
    }
}
