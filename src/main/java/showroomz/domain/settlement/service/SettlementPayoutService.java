package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.port.SettlementPayoutGateway.LineResult;
import showroomz.domain.settlement.port.SettlementPayoutGateway.Outcome;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutCommand;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutResult;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 3자 분배 지급(44 어드민 설계서 3-3) — 예정일이 된 수취자 행을 정산 단위로 묶어 {@link SettlementPayoutGateway}에 지시한다.
 *
 * <p>선점(트랜잭션) → 게이트웨이 호출(<b>트랜잭션 밖</b>) → 결과 반영(트랜잭션). PG 호출이 DB 트랜잭션을 붙들지 않고, 결과를 모르는
 * 실패는 REQUESTED 로 남겨 결과 조회 배치가 닫는다. 「지급 실행」 · 「앞당기기」 버튼은 어느 서피스에도 없다 — 지급은 이 배치와
 * 운영자 재분배(M3) 둘뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementPayoutService {

    private static final int RELEASE_BATCH = 200;

    private final SettlementPayoutRepository payoutRepository;
    private final SettlementPayoutTransitions transitions;
    private final SettlementPayoutGateway gateway;

    @Transactional(readOnly = true)
    public List<Long> findSettlementIdsDue(LocalDate today, int limit) {
        return payoutRepository.findSettlementIdsDue(today, PageRequest.of(0, limit));
    }

    /** 정산 1건의 예정일 도래 행 지급. 게이트웨이 예외(결과 모름)는 REQUESTED 로 둔다 — PG 결과 조회가 닫는다. */
    public void distribute(Long settlementId, LocalDate today, LocalDateTime now) {
        PayoutCommand command = transitions.claim(settlementId, today, now);
        if (command == null) {
            return;
        }
        PayoutResult result = command.lines().isEmpty() ? new PayoutResult(List.of()) : call(command);
        transitions.applyResults(settlementId, command, result, now);
    }

    /** 보류 재판정(5-4) — 주민등록번호가 등록된 비사업자 건의 인플루언서 행을 지급 예정으로. 지급 배치 앞단에서 부른다. */
    public int releaseBlocked(LocalDate today) {
        int released = 0;
        for (SettlementPayout payout : findBlocked()) {
            try {
                if (transitions.releaseIfUnblocked(payout.getSettlementId(), payout.getId(), today)) {
                    released++;
                }
            } catch (Exception e) {
                log.error("지급 보류 재판정 실패 - payoutId: {}", payout.getId(), e);
            }
        }
        return released;
    }

    @Transactional(readOnly = true)
    public List<SettlementPayout> findBlocked() {
        return payoutRepository.findBlockedCreatorPayouts(PageRequest.of(0, RELEASE_BATCH));
    }

    private PayoutResult call(PayoutCommand command) {
        try {
            return gateway.distribute(command);
        } catch (RuntimeException e) {
            log.error("정산 지급 지시 결과 미확인 — REQUESTED 로 둔다 - settlementId: {}", command.settlementId(), e);
            return new PayoutResult(command.lines().stream()
                    .map(line -> new LineResult(line.payoutId(), Outcome.REQUESTED, null, null, null)).toList());
        }
    }
}
