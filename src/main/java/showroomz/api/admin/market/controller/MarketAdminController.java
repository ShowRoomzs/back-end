package showroomz.api.admin.market.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.docs.AdminMarketControllerDocs;
import showroomz.api.admin.market.service.AdminService;
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
    @GetMapping("/sellers/pending")
    public ResponseEntity<PageResponse<SellerDto.PendingSellerResponse>> getPendingSellers(
            @ModelAttribute PagingRequest pagingRequest) {
        // Seller의 createdAt으로 정렬 (Market에는 createdAt 필드가 없음)
        Sort sort = Sort.by(Sort.Direction.DESC, "seller.createdAt");
        Pageable pageable = pagingRequest.toPageable(sort);
        PageResponse<SellerDto.PendingSellerResponse> response = adminService.getPendingSellers(pageable);
        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/sellers/{sellerId}/status")
    // @io.swagger.v3.oas.annotations.Operation(
    //         parameters = {
    //                 @Parameter(
    //                         name = "sellerId",
    //                         description = "상태를 변경할 판매자(Seller) ID",
    //                         required = true,
    //                         example = "1",
    //                         in = ParameterIn.PATH
    //                 )
    //         }
    // )
    public ResponseEntity<Void> updateSellerStatus(
            @PathVariable("sellerId") Long sellerId,
            @RequestBody SellerDto.UpdateStatusRequest request) {

        SellerStatus status;
        try {
            status = SellerStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        adminService.updateAdminStatus(sellerId, status, request.getRejectionReason());
        return ResponseEntity.noContent().build();
    }
}


