package showroomz.domain.member.creator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.member.creator.repository.CreatorSanctionRepository;
import showroomz.domain.settlement.port.CreatorPayoutHoldReader;

/**
 * 정산 포트 {@link CreatorPayoutHoldReader}의 구현(53 설계서 4-3) — 열린 정지 ∧ 지급 정지 사유(정산 정보 불일치). 정지 행의
 * {@code payout_held} 스냅샷을 본다 — 사유별 규칙이 뒤에 바뀌어도 이미 내린 정지의 효과는 그대로다.
 */
@Component
@RequiredArgsConstructor
public class CreatorSanctionReader implements CreatorPayoutHoldReader {

    private final CreatorSanctionRepository sanctions;

    @Override
    public boolean isHeld(Long creatorId) {
        return creatorId != null && sanctions.existsOpenPayoutHold(creatorId);
    }
}
