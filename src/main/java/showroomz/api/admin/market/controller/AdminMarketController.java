package showroomz.api.admin.market.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.admin.market.docs.AdminMarketStatusControllerDocs;
import showroomz.api.admin.market.service.AdminMarketService;
import showroomz.domain.market.type.MarketStatus;

import java.util.Map;

@RestController
@RequestMapping("/v1/admin/markets")
@RequiredArgsConstructor
public class AdminMarketController implements AdminMarketStatusControllerDocs {

    private final AdminMarketService adminMarketService;

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
