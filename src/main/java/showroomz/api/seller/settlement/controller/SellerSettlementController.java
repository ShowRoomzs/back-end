package showroomz.api.seller.settlement.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.settlement.docs.SellerSettlementControllerDocs;
import showroomz.api.seller.settlement.dto.SellerSettlementDto;
import showroomz.api.seller.settlement.service.SellerSettlementQueryService;
import showroomz.domain.settlement.service.SettlementStatementExcel;
import showroomz.domain.settlement.type.SettlementPartySort;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@RestController
@RequestMapping("/v1/seller/settlements")
@RequiredArgsConstructor
public class SellerSettlementController implements SellerSettlementControllerDocs {

    private final SellerSettlementQueryService queryService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<SellerSettlementDto.ListItem>> getSettlements(
            @RequestParam(value = "status", required = false) Set<SettlementStatus> status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) SettlementPartySort sort,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(queryService.getSettlements(getCurrentSellerEmail(), status, keyword, sort,
                pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<SellerSettlementDto.Summary> getSummary() {
        return ResponseEntity.ok(queryService.getSummary(getCurrentSellerEmail()));
    }

    @Override
    @GetMapping("/{settlementId}")
    public ResponseEntity<SellerSettlementDto.Detail> getSettlement(@PathVariable Long settlementId) {
        return ResponseEntity.ok(queryService.getDetail(getCurrentSellerEmail(), settlementId));
    }

    @Override
    @GetMapping("/{settlementId}/items")
    public ResponseEntity<PageResponse<SellerSettlementDto.Item>> getItems(@PathVariable Long settlementId,
                                                                          @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(queryService.getItems(getCurrentSellerEmail(), settlementId, pagingRequest));
    }

    @Override
    @GetMapping("/{settlementId}/items/download")
    public ResponseEntity<byte[]> downloadItems(@PathVariable Long settlementId) {
        return xlsx(queryService.downloadItems(getCurrentSellerEmail(), settlementId));
    }

    static ResponseEntity<byte[]> xlsx(SettlementStatementExcel.File file) {
        String encoded = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(SettlementStatementExcel.CONTENT_TYPE))
                .body(file.content());
    }

    private String getCurrentSellerEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
