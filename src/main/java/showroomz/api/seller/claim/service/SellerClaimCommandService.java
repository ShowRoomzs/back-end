package showroomz.api.seller.claim.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import showroomz.api.seller.claim.dto.SellerClaimBatchResponse;
import showroomz.api.seller.claim.dto.SellerClaimDetailResponse;
import showroomz.api.seller.claim.dto.SellerClaimReceiveRequest;
import showroomz.api.seller.claim.dto.SellerClaimRejectRequest;
import showroomz.api.seller.order.service.SellerOrderAccessGuard;
import showroomz.api.seller.order.service.SellerOrderAccessGuard.SellerScope;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 파트너센터 반품·교환 조작(35 설계서 3-3) — 입고 확인 · 검수 통과 · 검수 거절. 브랜드가 하는 일은 이 셋과 재발송 송장뿐이다.
 * 환불은 브랜드가 실행하지 않는다 — 판정이 끝나면 환불 큐에 오르고 집행은 운영자가 한다.
 * 상태 전이는 {@link OrderClaimService}(도메인)가 하고, 여기는 마켓 해석과 응답 조립만 한다.
 */
@Service
@RequiredArgsConstructor
public class SellerClaimCommandService {

    private final SellerOrderAccessGuard accessGuard;
    private final OrderClaimService claimService;
    private final SellerClaimQueryService queryService;

    /**
     * 입고 확인 — 다건 · 행 단위 부분 성공. 내 마켓 것이 아닌 건과 입고 확인할 단계가 아닌 건은 같은 사유로 제외한다
     * (남의 클레임이 있는지 가르지 않는다).
     */
    public SellerClaimBatchResponse receive(String sellerEmail, SellerClaimReceiveRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        LocalDateTime now = LocalDateTime.now();
        List<SellerClaimBatchResponse.Skipped> skipped = new ArrayList<>();
        int succeeded = 0;
        for (Long claimId : new LinkedHashSet<>(request.claimIds())) {
            if (claimService.receive(claimId, scope.market().getId(), scope.sellerId(), now)) {
                succeeded++;
            } else {
                skipped.add(new SellerClaimBatchResponse.Skipped(claimId, ErrorCode.CLAIM_STATE_CHANGED.getCode(),
                        ErrorCode.CLAIM_STATE_CHANGED.getMessage()));
            }
        }
        return new SellerClaimBatchResponse(succeeded, skipped);
    }

    /** 검수 통과 — 단건만 받는다(근거가 건마다 다르다). */
    public SellerClaimDetailResponse pass(String sellerEmail, Long claimId) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        claimService.passInspection(claimId, scope.market().getId(), scope.sellerId(), LocalDateTime.now());
        return queryService.getClaim(sellerEmail, claimId);
    }

    /** 검수 거절 — 단건 · 제출 = 즉시 확정. */
    public SellerClaimDetailResponse reject(String sellerEmail, Long claimId, SellerClaimRejectRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        claimService.rejectInspection(claimId, scope.market().getId(), scope.sellerId(), request.reasonCode(),
                request.detail(), request.evidenceImageUrls(), LocalDateTime.now());
        return queryService.getClaim(sellerEmail, claimId);
    }
}
