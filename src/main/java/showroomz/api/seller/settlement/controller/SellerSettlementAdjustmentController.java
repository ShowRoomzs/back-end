package showroomz.api.seller.settlement.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.common.settlement.dto.SettlementAdjustmentDto;
import showroomz.api.common.settlement.service.SettlementAdjustmentApiService;
import showroomz.api.seller.settlement.docs.SellerSettlementAdjustmentControllerDocs;
import showroomz.api.seller.settlement.service.SellerSettlementAccessGuard;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/** 파트너센터 정산 조정 협의(44 이슈 스레드 설계서 3-1) — 당사자 = 내 마켓. 규칙은 스튜디오와 같은 서비스다. */
@RestController
@RequestMapping("/v1/seller")
@RequiredArgsConstructor
public class SellerSettlementAdjustmentController implements SellerSettlementAdjustmentControllerDocs {

    private final SettlementAdjustmentApiService adjustmentApiService;
    private final SellerSettlementAccessGuard accessGuard;

    @Override
    @GetMapping("/settlements/{settlementId}/adjustment/preview")
    public ResponseEntity<SettlementAdjustmentDto.PreviewResponse> preview(
            @PathVariable Long settlementId, @RequestParam(value = "rewardAmount", required = false) Long rewardAmount) {
        return ResponseEntity.ok(adjustmentApiService.preview(SettlementParty.SELLER, marketId(), settlementId,
                rewardAmount));
    }

    @Override
    @PostMapping("/settlements/{settlementId}/adjustment")
    public ResponseEntity<SettlementAdjustmentDto.RequestResponse> request(
            @PathVariable Long settlementId, @Valid @RequestBody SettlementAdjustmentDto.RequestBody body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adjustmentApiService.request(SettlementParty.SELLER, marketId(), settlementId, body));
    }

    @Override
    @GetMapping("/threads/{threadId}/adjustment")
    public ResponseEntity<SettlementAdjustmentDto.AdjustmentView> byThread(@PathVariable Long threadId) {
        return ResponseEntity.ok(adjustmentApiService.byThread(SettlementParty.SELLER, marketId(), threadId));
    }

    @Override
    @PostMapping("/settlement-adjustments/{adjustmentId}/proposals")
    public ResponseEntity<SettlementAdjustmentDto.AdjustmentView> counter(
            @PathVariable Long adjustmentId, @Valid @RequestBody SettlementAdjustmentDto.CounterBody body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adjustmentApiService.counter(SettlementParty.SELLER, marketId(), adjustmentId, body));
    }

    @Override
    @PostMapping("/settlement-adjustments/{adjustmentId}/proposals/{proposalId}/accept")
    public ResponseEntity<SettlementAdjustmentDto.AdjustmentView> accept(@PathVariable Long adjustmentId,
                                                                        @PathVariable Long proposalId) {
        return ResponseEntity.ok(adjustmentApiService.accept(SettlementParty.SELLER, marketId(), adjustmentId,
                proposalId));
    }

    @Override
    @PostMapping("/settlement-adjustments/{adjustmentId}/proposals/{proposalId}/reject")
    public ResponseEntity<SettlementAdjustmentDto.AdjustmentView> reject(@PathVariable Long adjustmentId,
                                                                        @PathVariable Long proposalId) {
        return ResponseEntity.ok(adjustmentApiService.reject(SettlementParty.SELLER, marketId(), adjustmentId,
                proposalId));
    }

    private Long marketId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return accessGuard.resolveMarket(userPrincipal.getUsername()).getId();
    }
}
