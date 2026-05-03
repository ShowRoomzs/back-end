package showroomz.api.admin.market.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.admin.market.DTO.MarketAdminDto;
import showroomz.api.admin.market.docs.AdminMarketListControllerDocs;
import showroomz.api.admin.market.docs.AdminMarketMemoControllerDocs;
import showroomz.api.admin.market.docs.AdminMarketStatusControllerDocs;
import showroomz.api.admin.market.service.AdminMarketService;
import showroomz.domain.market.type.MarketStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.Map;

@RestController
@RequestMapping("/v1/admin/markets")
@RequiredArgsConstructor
public class AdminMarketController implements AdminMarketListControllerDocs,
        AdminMarketStatusControllerDocs,
        AdminMarketMemoControllerDocs {

    private final AdminMarketService adminMarketService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<AdminMarketDto.MarketResponse>> getMarkets(
            @ParameterObject @ModelAttribute PagingRequest pagingRequest,
            @ParameterObject @ModelAttribute AdminMarketDto.MarketSearchRequest searchRequest) {

        Sort sort = Sort.by(Sort.Direction.DESC, "seller.createdAt");
        Pageable pageable = pagingRequest.toPageable(sort);

        return ResponseEntity.ok(
                PageResponse.of(adminMarketService.getMarkets(searchRequest, pageable)));
    }

    @Override
    @PatchMapping("/{marketId}/memo")
    public ResponseEntity<Void> updateMarketAdminMemo(
            @PathVariable("marketId") Long marketId,
            @RequestBody MarketAdminDto.UpdateAdminMemoRequest request) {
        adminMarketService.updateMarketAdminMemo(marketId, request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/{marketId}/status")
    public ResponseEntity<Map<String, String>> updateMarketStatus(
            @PathVariable Long marketId,
            @Valid @RequestBody AdminMarketDto.UpdateMarketStatusRequest request) {

        adminMarketService.updateMarketStatus(marketId, request);

        String message = request.getStatus() == MarketStatus.SUSPENDED
                ? "마켓이 정지 처리되었으며 모든 상품이 미노출 전환되었습니다."
                : "마켓이 활성 처리되었으며 상품 노출 상태가 복구되었습니다.";

        return ResponseEntity.ok(Map.of("message", message));
    }
}
