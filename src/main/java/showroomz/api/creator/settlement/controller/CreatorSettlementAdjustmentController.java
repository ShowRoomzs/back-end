package showroomz.api.creator.settlement.controller;

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
import showroomz.api.creator.settlement.docs.CreatorSettlementAdjustmentControllerDocs;
import showroomz.api.creator.settlement.service.CreatorSettlementQueryService;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/** 쇼룸 스튜디오 정산 조정 협의(44 이슈 스레드 설계서 3-1) — 당사자 = 나(크리에이터). 규칙은 파트너센터와 같은 서비스다. */
@RestController
@RequestMapping("/v1/creator")
@RequiredArgsConstructor
public class CreatorSettlementAdjustmentController implements CreatorSettlementAdjustmentControllerDocs {

    private final SettlementAdjustmentApiService adjustmentApiService;
    private final CreatorSettlementQueryService queryService;

    @Override
    @GetMapping("/settlements/{settlementId}/adjustment/preview")
    public ResponseEntity<SettlementAdjustmentDto.PreviewResponse> preview(
            @PathVariable Long settlementId, @RequestParam(value = "rewardAmount", required = false) Long rewardAmount) {
        return ResponseEntity.ok(adjustmentApiService.preview(SettlementParty.CREATOR, creatorId(), settlementId,
                rewardAmount));
    }

    @Override
    @PostMapping("/settlements/{settlementId}/adjustment")
    public ResponseEntity<SettlementAdjustmentDto.RequestResponse> request(
            @PathVariable Long settlementId, @Valid @RequestBody SettlementAdjustmentDto.RequestBody body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adjustmentApiService.request(SettlementParty.CREATOR, creatorId(), settlementId, body));
    }

    @Override
    @GetMapping("/threads/{threadId}/adjustment")
    public ResponseEntity<SettlementAdjustmentDto.AdjustmentView> byThread(@PathVariable Long threadId) {
        return ResponseEntity.ok(adjustmentApiService.byThread(SettlementParty.CREATOR, creatorId(), threadId));
    }

    @Override
    @PostMapping("/settlement-adjustments/{adjustmentId}/proposals")
    public ResponseEntity<SettlementAdjustmentDto.AdjustmentView> counter(
            @PathVariable Long adjustmentId, @Valid @RequestBody SettlementAdjustmentDto.CounterBody body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adjustmentApiService.counter(SettlementParty.CREATOR, creatorId(), adjustmentId, body));
    }

    @Override
    @PostMapping("/settlement-adjustments/{adjustmentId}/proposals/{proposalId}/accept")
    public ResponseEntity<SettlementAdjustmentDto.AdjustmentView> accept(@PathVariable Long adjustmentId,
                                                                        @PathVariable Long proposalId) {
        return ResponseEntity.ok(adjustmentApiService.accept(SettlementParty.CREATOR, creatorId(), adjustmentId,
                proposalId));
    }

    @Override
    @PostMapping("/settlement-adjustments/{adjustmentId}/proposals/{proposalId}/reject")
    public ResponseEntity<SettlementAdjustmentDto.AdjustmentView> reject(@PathVariable Long adjustmentId,
                                                                        @PathVariable Long proposalId) {
        return ResponseEntity.ok(adjustmentApiService.reject(SettlementParty.CREATOR, creatorId(), adjustmentId,
                proposalId));
    }

    private Long creatorId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return queryService.resolveCreator(userPrincipal.getUsername()).getId();
    }
}
