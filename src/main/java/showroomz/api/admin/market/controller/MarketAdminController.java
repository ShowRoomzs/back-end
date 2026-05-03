package showroomz.api.admin.market.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.admin.market.docs.AdminMarketManagementControllerDocs;
import showroomz.api.admin.market.service.AdminService;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class MarketAdminController implements AdminMarketManagementControllerDocs {

    private final AdminService adminService;

    @Override
    @GetMapping("/markets/{marketId}")
    public ResponseEntity<AdminMarketDto.MarketAdminDetailResponse> getMarketInfo(
            @PathVariable("marketId") Long marketId) {
        
        AdminMarketDto.MarketAdminDetailResponse response = adminService.getMarketInfo(marketId);
        
        return ResponseEntity.ok(response);
    }
}


