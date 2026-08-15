package showroomz.api.admin.productinquiry.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.productinquiry.docs.AdminProductInquiryControllerDocs;
import showroomz.api.admin.productinquiry.dto.AdminProductInquiryDeleteDecision;
import showroomz.api.admin.productinquiry.dto.AdminProductInquiryDto;
import showroomz.api.admin.productinquiry.service.AdminProductInquiryService;
import showroomz.api.admin.productinquiry.type.AdminProductInquiryStatusFilter;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/product-inquiries")
@RequiredArgsConstructor
public class AdminProductInquiryController implements AdminProductInquiryControllerDocs {

    private final AdminProductInquiryService adminProductInquiryService;

    @Override
    @GetMapping
    public ResponseEntity<AdminProductInquiryDto.ListResponse> getList(
            @RequestParam(value = "status", defaultValue = "ALL") AdminProductInquiryStatusFilter status,
            @RequestParam(value = "type", required = false) ProductInquiryType type,
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest) {
        Pageable pageable = pagingRequest.toPageable(Sort.unsorted());
        return ResponseEntity.ok(adminProductInquiryService.getList(status, type, keyword, pageable));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<AdminProductInquiryDto.SummaryResponse> getSummary() {
        return ResponseEntity.ok(adminProductInquiryService.getSummary());
    }

    @Override
    @GetMapping("/types")
    public ResponseEntity<List<AdminProductInquiryDto.TypeOption>> getTypes() {
        return ResponseEntity.ok(adminProductInquiryService.getTypeOptions());
    }

    @Override
    @GetMapping("/{inquiryId}")
    public ResponseEntity<AdminProductInquiryDto.DetailResponse> getDetail(
            @PathVariable("inquiryId") Long inquiryId,
            @RequestParam(value = "status", defaultValue = "ALL") AdminProductInquiryStatusFilter status,
            @RequestParam(value = "type", required = false) ProductInquiryType type,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(adminProductInquiryService.getDetail(inquiryId, status, type, keyword));
    }

    @Override
    @PostMapping("/{inquiryId}/delete-request/execute")
    public ResponseEntity<Void> executeDelete(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody AdminProductInquiryDeleteDecision.ExecuteRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        adminProductInquiryService.executeDelete(inquiryId, requireOperatorId(principal), request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{inquiryId}/delete-request/reject")
    public ResponseEntity<Void> rejectDeleteRequest(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody AdminProductInquiryDeleteDecision.RejectRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        adminProductInquiryService.rejectDeleteRequest(inquiryId, requireOperatorId(principal), request);
        return ResponseEntity.noContent().build();
    }

    private Long requireOperatorId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return principal.getUserId();
    }
}
