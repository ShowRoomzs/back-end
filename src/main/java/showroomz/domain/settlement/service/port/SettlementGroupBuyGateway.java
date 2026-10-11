package showroomz.domain.settlement.service.port;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.service.port.GroupBuySettlementGateway;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Optional;

/**
 * 공구 → 정산 단계 포트 구현(44 어드민 설계서 8-1). 공구 상세 {@code settlement.stage}가 「파생」이 아니라 이 포트 값이 된다
 * ({@code stageSource = PORT}). 리워드 포트는 {@link SettlementGroupBuyReader} — 두 포트를 한 빈에 두지 않는다(포트별로 갈아 끼운다).
 *
 * <p>운영자 정산 확인({@link #confirm})은 폐기된 절차다(§41-1 #6) — 확정은 시스템(자동 · 합의 · 만료)만 하므로 <b>항상 409</b>.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementGroupBuyGateway implements GroupBuySettlementGateway {

    private final SettlementRepository settlementRepository;

    @Override
    public Optional<SettlementStage> readStage(Long groupBuyId) {
        return settlementRepository.findByGroupBuyId(groupBuyId).map(settlement -> switch (settlement.getStatus()) {
            case REVIEWING, ADJUSTING -> SettlementStage.WAITING;
            case PAYOUT_SCHEDULED, PAYOUT_FAILED -> SettlementStage.CONFIRMED;
            case PAID -> SettlementStage.TRANSFERRED;
        });
    }

    /** 운영자 정산 확인 폐기(§41-1 #6 · 0-5) — 확정 버튼은 어느 서피스에도 없다. */
    @Override
    public void confirm(Long groupBuyId, Long operatorId) {
        throw new BusinessException(ErrorCode.GROUP_BUY_SETTLEMENT_NOT_READY);
    }
}
