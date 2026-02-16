package showroomz.api.admin.market.controller;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.admin.market.docs.AdminMarketManagementControllerDocs;
import showroomz.api.admin.market.service.AdminService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class MarketAdminController implements AdminMarketManagementControllerDocs {

    private final AdminService adminService;

    @Override
    @GetMapping("/markets")
    public ResponseEntity<PageResponse<AdminMarketDto.MarketResponse>> getMarkets(
            @ParameterObject @ModelAttribute PagingRequest pagingRequest,
            @ParameterObject @ModelAttribute AdminMarketDto.MarketListSearchCondition searchCondition) {

        // 기본 정렬: 입점일(판매자 생성일) 내림차순
        Sort sort = Sort.by(Sort.Direction.DESC, "seller.createdAt");
        Pageable pageable = pagingRequest.toPageable(sort);

        PageResponse<AdminMarketDto.MarketResponse> response =
                adminService.getMarkets(searchCondition, pageable);

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/markets/{marketId}")
    public ResponseEntity<AdminMarketDto.MarketAdminDetailResponse> getMarketInfo(
            @PathVariable("marketId") Long marketId) {
        
        AdminMarketDto.MarketAdminDetailResponse response = adminService.getMarketInfo(marketId);
        
        return ResponseEntity.ok(response);
    }
}


