package showroomz.api.test.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import showroomz.api.test.dto.TestClaimDto;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 테스트 전용: 재발송 송장 추적의 도착 확인(#11 RESHIPPING → COMPLETED)을 대신 일으킨다(58 계획 2-5). 테스트 송장은 택배 조회에 이벤트가
 * 없어 재발송이 끝나지 않고, 그래서 정산이 생기지 않아 정산 조정 이슈 스레드를 실데이터로 볼 수 없었다.
 *
 * <p>추적 배치와 <b>같은 메서드</b>({@link OrderClaimService#applyReshipTracking})에 도착 스냅샷을 넣는다 — 결과(교환 완료 / 반려 반송) ·
 * 구매확정 재시작 · 이력이 실제 도착과 같다.
 */
@Service
@RequiredArgsConstructor
public class TestClaimService {

    private final OrderClaimRepository claimRepository;
    private final OrderClaimService claimService;

    public TestClaimDto.ReshipDeliveredResponse markReshipDelivered(Long claimId) {
        OrderClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        if (claim.getStatus() != ClaimStatus.RESHIPPING) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED,
                    "재발송 중(RESHIPPING)인 클레임만 처리합니다. 현재: " + claim.claimNumber() + " " + claim.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        claimService.applyReshipTracking(claimId, claim.getReshipCarrier(), claim.getReshipTrackingNumber(),
                new TrackSnapshot(now, now, false, false), now);
        OrderClaim done = claimRepository.findById(claimId).orElseThrow();
        if (done.getStatus() != ClaimStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED,
                    "재발송 도착을 반영하지 못했습니다. 현재: " + done.claimNumber() + " " + done.getStatus());
        }
        return new TestClaimDto.ReshipDeliveredResponse(done.getId(), done.claimNumber(), done.getStatus(), done.getResult(),
                done.getReshipDeliveredAt());
    }
}
