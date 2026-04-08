package showroomz.api.admin.productannouncement.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.productannouncement.docs.AdminProductAnnouncementControllerDocs;
import showroomz.api.admin.productannouncement.dto.*;
import showroomz.api.admin.productannouncement.service.AdminProductAnnouncementService;
import showroomz.domain.productannouncement.type.ProductAnnouncementDisplayStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.net.URI;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/v1/admin/product-announcements")
@RequiredArgsConstructor
public class AdminProductAnnouncementController implements AdminProductAnnouncementControllerDocs {

    private final AdminProductAnnouncementService adminProductAnnouncementService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<AdminProductAnnouncementListItem>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "displayStatus", required = false) ProductAnnouncementDisplayStatus displayStatus,
            @RequestParam(value = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(value = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest
    ) {
        return ResponseEntity.ok(adminProductAnnouncementService.search(
                keyword, category, displayStatus, createdFrom, createdTo, pagingRequest));
    }

    @Override
    @PostMapping
    public ResponseEntity<AdminProductAnnouncementCreateResponse> create(
            @Valid @RequestBody AdminProductAnnouncementCreateRequest request
    ) {
        Long id = adminProductAnnouncementService.create(request);
        URI location = URI.create("/v1/admin/product-announcements/" + id);
        return ResponseEntity.created(location).body(new AdminProductAnnouncementCreateResponse(
                id,
                "상품 공지사항이 성공적으로 등록되었습니다."));
    }

    @Override
    @GetMapping("/{announcementId}")
    public ResponseEntity<AdminProductAnnouncementDetailResponse> getDetail(
            @PathVariable("announcementId") Long announcementId) {
        return ResponseEntity.ok(adminProductAnnouncementService.getDetail(announcementId));
    }

    @Override
    @PutMapping("/{announcementId}")
    public ResponseEntity<Void> update(
            @PathVariable("announcementId") Long announcementId,
            @Valid @RequestBody AdminProductAnnouncementUpdateRequest request
    ) {
        adminProductAnnouncementService.update(announcementId, request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{announcementId}")
    public ResponseEntity<Void> delete(@PathVariable("announcementId") Long announcementId) {
        adminProductAnnouncementService.delete(announcementId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/bulk-delete")
    public ResponseEntity<AdminProductAnnouncementBulkResult> bulkDelete(
            @Valid @RequestBody AdminProductAnnouncementBulkDeleteRequest request
    ) {
        int affected = adminProductAnnouncementService.bulkDelete(request);
        return ResponseEntity.ok(new AdminProductAnnouncementBulkResult(affected));
    }

    @Override
    @PatchMapping("/bulk-status")
    public ResponseEntity<AdminProductAnnouncementBulkResult> bulkStatus(
            @Valid @RequestBody AdminProductAnnouncementBulkStatusRequest request
    ) {
        int affected = adminProductAnnouncementService.bulkUpdateStatus(request);
        return ResponseEntity.status(HttpStatus.OK).body(new AdminProductAnnouncementBulkResult(affected));
    }
}
