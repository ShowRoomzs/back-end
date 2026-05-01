package showroomz.api.admin.market.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.admin.market.DTO.AdminSellerDetailResponse;
import showroomz.api.admin.market.DTO.UpdateReviewMemoRequest;
import showroomz.api.admin.market.docs.AdminMarketControllerDocs;
import showroomz.api.admin.market.service.AdminSellerService;
import showroomz.api.admin.market.service.AdminService;
import showroomz.api.seller.auth.DTO.SellerDto;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class SellerAdminController implements AdminMarketControllerDocs {

    private final AdminService adminService;
    private final AdminSellerService adminSellerService;

    @Override
    @GetMapping("/sellers/applications")
    public ResponseEntity<PageResponse<AdminMarketDto.ApplicationResponse>> getMarketApplications(
            @ModelAttribute PagingRequest pagingRequest,
            @ModelAttribute AdminMarketDto.SearchCondition searchCondition) {
        
        // 정렬 기준: 신청일 최신순
        Sort sort = Sort.by(Sort.Direction.DESC, "seller.createdAt");
        Pageable pageable = pagingRequest.toPageable(sort);
        
        PageResponse<AdminMarketDto.ApplicationResponse> response = 
                adminService.getMarketApplications(searchCondition, pageable);
        
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/sellers/{sellerId}")
    public ResponseEntity<AdminSellerDetailResponse> getMarketDetail(
            @PathVariable("sellerId") Long sellerId) {

        AdminSellerDetailResponse response = adminSellerService.getSellerDetail(sellerId);
        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/sellers/{sellerId}/status")
    public ResponseEntity<Void> updateSellerStatus(
            @PathVariable("sellerId") Long sellerId,
            @RequestBody SellerDto.UpdateStatusRequest request) {

        SellerStatus status;
        try {
            status = SellerStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // DTO에서 Enum과 Detail을 꺼내서 전달
        adminService.updateAdminStatus(
                sellerId, 
                status, 
                request.getRejectionReasonType(), 
                request.getRejectionReasonDetail()
        );
        
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/sellers/{sellerId}/review-memo")
    public ResponseEntity<Void> updateReviewMemo(
            @PathVariable("sellerId") Long sellerId,
            @Valid @RequestBody UpdateReviewMemoRequest request) {

        adminService.updateReviewMemo(sellerId, request.getReviewMemo());
        return ResponseEntity.noContent().build();
    }
}
