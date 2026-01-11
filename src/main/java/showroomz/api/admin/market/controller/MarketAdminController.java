package showroomz.api.admin.market.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.docs.AdminMarketControllerDocs;
import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.admin.market.service.AdminService;
import showroomz.api.admin.market.type.RejectionReasonType;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.seller.auth.DTO.SellerDto;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.api.seller.market.service.MarketService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class MarketAdminController implements AdminMarketControllerDocs {

    private final MarketService marketService;
    private final AdminService adminService;

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
    public ResponseEntity<AdminMarketDto.MarketDetailResponse> getMarketDetail(
            @PathVariable("sellerId") Long sellerId) {
        
        AdminMarketDto.MarketDetailResponse response = adminService.getMarketDetail(sellerId);
        
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
}


