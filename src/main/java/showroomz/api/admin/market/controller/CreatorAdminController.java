package showroomz.api.admin.market.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.admin.market.docs.AdminCreatorControllerDocs;
import showroomz.api.admin.market.service.AdminService;
import showroomz.api.seller.auth.DTO.SellerDto;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/admin/creators/applications")
@RequiredArgsConstructor
public class CreatorAdminController implements AdminCreatorControllerDocs {

    private final AdminService adminService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<AdminMarketDto.CreatorApplicationResponse>> getCreatorApplications(
            @ModelAttribute PagingRequest pagingRequest,
            @ModelAttribute AdminMarketDto.SearchCondition searchCondition) {

        Sort sort = Sort.by(Sort.Direction.DESC, "seller.createdAt");
        Pageable pageable = pagingRequest.toPageable(sort);

        PageResponse<AdminMarketDto.CreatorApplicationResponse> response =
                adminService.getCreatorApplications(searchCondition, pageable);

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{creatorId}")
    public ResponseEntity<AdminMarketDto.CreatorDetailResponse> getCreatorDetail(
            @PathVariable("creatorId") Long creatorId) {

        AdminMarketDto.CreatorDetailResponse response = adminService.getCreatorDetail(creatorId);

        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/{creatorId}/status")
    public ResponseEntity<Void> updateCreatorStatus(
            @PathVariable("creatorId") Long creatorId,
            @RequestBody SellerDto.UpdateStatusRequest request) {

        SellerStatus status;
        try {
            status = SellerStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        adminService.updateCreatorStatus(
                creatorId,
                status,
                request.getRejectionReasonType(),
                request.getRejectionReasonDetail()
        );

        return ResponseEntity.noContent().build();
    }
}
