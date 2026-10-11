package showroomz.domain.settlement.service;

import showroomz.domain.settlement.entity.Settlement;

import java.time.LocalDateTime;

/**
 * 확정 · 지급 흐름이 증빙(44 어드민 설계서 5절)에 알리는 자리 — 확정 시 세금계산서 행 · 인플루언서 몫 지급 완료 시 원천징수영수증.
 * 확정 · 지급 서비스는 증빙 테이블을 직접 알지 않는다.
 */
public interface SettlementTaxDocumentHook {

    /** 확정(3-2 #3) — 브랜드 세금계산서 발행 대기 · 사업자면 인플루언서 세금계산서 입력 대기 · 이력. 확정 트랜잭션 안에서. */
    void onConfirmed(Settlement settlement, LocalDateTime confirmedAt);

    /** 인플루언서 몫 지급 완료(5-3) — 비사업자면 원천징수영수증 생성 대기 행 + 비동기 생성. 결과 반영 트랜잭션 안에서. */
    void onCreatorPaid(Long settlementId, LocalDateTime paidAt);
}
