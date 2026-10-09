package showroomz.domain.settlement.service;

import org.springframework.stereotype.Component;
import showroomz.domain.settlement.entity.Settlement;

import java.time.LocalDateTime;

/**
 * 증빙 모듈(44 구현 계획서 단계 7) 전의 빈 구현 — 증빙 행을 만들지 않는다. 증빙 서비스가 {@link SettlementTaxDocumentHook}을
 * 구현하면 이 클래스를 지운다.
 */
@Component
public class NoopSettlementTaxDocumentHook implements SettlementTaxDocumentHook {

    @Override
    public void onConfirmed(Settlement settlement, LocalDateTime confirmedAt) {
        // 증빙 모듈 전.
    }

    @Override
    public void onCreatorPaid(Long settlementId, LocalDateTime paidAt) {
        // 증빙 모듈 전.
    }
}
