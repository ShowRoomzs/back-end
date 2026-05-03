package showroomz.api.admin.product.inspection.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.product.inspection.docs.AdminProductInspectionControllerDocs;
import showroomz.api.admin.product.inspection.dto.AdminProductInspectionDto;
import showroomz.api.admin.product.inspection.dto.ProductInspectionSearchCondition;
import showroomz.api.admin.product.inspection.service.AdminProductInspectionService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/admin/product-inspections")
@RequiredArgsConstructor
public class AdminProductInspectionController implements AdminProductInspectionControllerDocs {

    private final AdminProductInspectionService adminProductInspectionService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<AdminProductInspectionDto.ListItem>> list(
            @ParameterObject @ModelAttribute ProductInspectionSearchCondition condition,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest
    ) {
        PageResponse<AdminProductInspectionDto.ListItem> response =
                adminProductInspectionService.search(condition, pagingRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{productId}")
    public ResponseEntity<AdminProductInspectionDto.InspectionDetailResponse> detail(@PathVariable Long productId) {
        return ResponseEntity.ok(adminProductInspectionService.getDetail(productId));
    }

    @Override
    @PatchMapping("/{productId}/status")
    public ResponseEntity<AdminProductInspectionDto.UpdateStatusResponse> updateStatus(
            @PathVariable Long productId,
            @Valid @RequestBody AdminProductInspectionDto.UpdateStatusRequest request
    ) {
        return ResponseEntity.ok(adminProductInspectionService.updateStatus(productId, request));
    }

    @Override
    @PatchMapping("/bulk-status")
    public ResponseEntity<AdminProductInspectionDto.BulkUpdateStatusResponse> bulkUpdateStatus(
            @Valid @RequestBody AdminProductInspectionDto.BulkUpdateStatusRequest request
    ) {
        return ResponseEntity.ok(adminProductInspectionService.bulkUpdateStatus(request));
    }
}
