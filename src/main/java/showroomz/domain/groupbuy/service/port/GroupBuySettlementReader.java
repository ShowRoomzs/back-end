package showroomz.domain.groupbuy.service.port;

import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 정산 포트 — 정산 모듈이 확정한 리워드를 <b>그대로</b> 받는다(30 설계 5-2 · 31 설계 4-5). 공구는 정산 명세를 계산하지 않는다.
 *
 * <p>구현은 정산 모듈의 {@code SettlementGroupBuyGateway}다. 지급 완료 전이면 empty — 응답은 null이 된다(0이 아니다).
 */
public interface GroupBuySettlementReader {

    /** 정산완료 공구의 확정 리워드(공제 전). */
    Optional<Long> readConfirmedReward(Long groupBuyId);

    /** 그 공구의 정산 — 「정산 관리 ↗」 링크(44 어드민 설계서 8-7). 정산이 생기기 전이면 empty. */
    default Optional<Long> readSettlementId(Long groupBuyId) {
        return Optional.empty();
    }

    /** 그 공구의 정산 상태 · 기한 — 종료(ENDED) 공구 상세의 정산 블록(스튜디오 B-10). 정산이 생기기 전이면 empty. */
    default Optional<SettlementSummary> readSummary(Long groupBuyId) {
        return Optional.empty();
    }

    /**
     * @param status         수취자 화면 상태({@link SettlementStatus#toPartyStatus()} — 분배 실패도 그대로 · 2026-11-10)
     * @param reviewDueAt    정산 확인 기한
     * @param ordersClosedAt 주문 종결 시각
     */
    record SettlementSummary(Long settlementId, SettlementStatus status, LocalDateTime reviewDueAt,
                             LocalDateTime ordersClosedAt) {
    }
}
