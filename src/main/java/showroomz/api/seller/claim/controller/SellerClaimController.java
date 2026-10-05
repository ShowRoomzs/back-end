package showroomz.api.seller.claim.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.claim.docs.SellerClaimControllerDocs;
import showroomz.api.seller.claim.dto.SellerClaimDetailResponse;
import showroomz.api.seller.claim.dto.SellerClaimListItem;
import showroomz.api.seller.claim.dto.SellerClaimSummaryResponse;
import showroomz.api.seller.claim.service.SellerClaimQueryService;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.order.type.ClaimType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.util.Set;

@RestController
@RequestMapping("/v1/seller/claims")
@RequiredArgsConstructor
public class SellerClaimController implements SellerClaimControllerDocs {

    private final SellerClaimQueryService queryService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<SellerClaimListItem>> getClaims(
            @RequestParam(value = "tab", required = false) ClaimTab tab,
            @RequestParam(value = "types", required = false) Set<ClaimType> types,
            @RequestParam(value = "reason", required = false) ClaimReason reason,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(queryService.getClaims(getCurrentSellerEmail(), tab, types, reason, from, to, keyword,
                pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<SellerClaimSummaryResponse> getSummary() {
        return ResponseEntity.ok(queryService.getSummary(getCurrentSellerEmail()));
    }

    @Override
    @GetMapping("/{claimId}")
    public ResponseEntity<SellerClaimDetailResponse> getClaim(@PathVariable Long claimId) {
        return ResponseEntity.ok(queryService.getClaim(getCurrentSellerEmail(), claimId));
    }

    private String getCurrentSellerEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
