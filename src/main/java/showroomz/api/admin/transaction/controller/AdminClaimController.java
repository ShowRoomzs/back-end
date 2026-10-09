package showroomz.api.admin.transaction.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.transaction.docs.AdminClaimControllerDocs;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.api.admin.transaction.service.AdminClaimService;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.claim.dto.SellerClaimSummaryResponse;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.order.type.ClaimType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.time.LocalDate;
import java.util.Set;

@RestController
@RequestMapping("/v1/admin/claims")
@RequiredArgsConstructor
public class AdminClaimController implements AdminClaimControllerDocs {

    private final AdminClaimService claimService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<AdminTransactionDto.ClaimListItem>> getClaims(
            @RequestParam(value = "marketId", required = false) Long marketId,
            @RequestParam(value = "tab", defaultValue = "ALL") ClaimTab tab,
            @RequestParam(value = "types", required = false) Set<ClaimType> types,
            @RequestParam(value = "reason", required = false) ClaimReason reason,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(claimService.getClaims(marketId, tab, types, reason, from, to, keyword, pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<SellerClaimSummaryResponse> getSummary(
            @RequestParam(value = "marketId", required = false) Long marketId) {
        return ResponseEntity.ok(claimService.getSummary(marketId));
    }

    @Override
    @GetMapping("/{claimId}")
    public ResponseEntity<AdminTransactionDto.ClaimDetail> getClaim(@PathVariable Long claimId) {
        return ResponseEntity.ok(claimService.getClaim(claimId));
    }

    @Override
    @PostMapping("/{claimId}/dispute-acceptance")
    public ResponseEntity<AdminTransactionDto.DisputeAcceptResponse> acceptDispute(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long claimId,
            @Valid @RequestBody AdminTransactionDto.DisputeAcceptRequest request) {
        return ResponseEntity.ok(claimService.acceptDispute(AdminOrderController.operatorId(principal), claimId, request));
    }
}
