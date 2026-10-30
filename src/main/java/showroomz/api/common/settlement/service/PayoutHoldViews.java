package showroomz.api.common.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.settlement.dto.PayoutHoldView;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.service.PayoutBlockPolicy;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.utils.BusinessCalendar;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 내 지급 행 보류 이유({@link PayoutHoldView}) 조립 — 스튜디오 12 · 파트너 13 의 목록 · 상세 · 내 분배 행이 같은 규칙으로 읽는다(1017
 * 설계서 8-8).
 *
 * <p>사유는 행의 {@code fail_code} 가 있으면 그것, 없으면 지급 보류 판정({@link PayoutBlockPolicy})이 지금 보는 <b>첫 사유</b>다. 확정은
 * 인플루언서 몫을 사유 코드 없이 BLOCKED 로 만들고(해제 경로 — {@code PayoutHoldTransitions} 의 {@code includeUncoded} · 지급 배치의
 * {@code releaseIfUnblocked} — 가 「코드 없는 보류」를 전제로 한다), 정지 해제 · 셀러 해제도 남은 사유가 있으면 코드만 지운다. 저장
 * 방식은 그대로 두고 표시에서 판정을 다시 부른다 — 어드민 정산 상세 {@code rail.blockReasons} 와 같은 출처.
 *
 * <p>원래 예정일은 행에서 읽을 수 없다 — 보류가 행의 예정일을 지운다({@code holdScheduled} · 확정의 BLOCKED 전이). 보류 뒤에도 남는
 * 값에서 채운다: 정산의 지급 예정일(확정일 + N영업일 · 확정 때 한 번 정해지고 바뀌지 않는다 · 해제의 「원래 예정일과 오늘 중 늦은 날」 규칙이
 * 쓰는 값). 사업자 인플루언서 몫만은 브랜드와 날짜가 달라(세금계산서 확인일 + N영업일) 확인 기록에서 다시 계산하고, 확인 전이면 원래
 * 예정일이 없다(null).
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayoutHoldViews {

    private final PayoutBlockPolicy blockPolicy;
    private final SettlementTaxDocumentRepository documentRepository;
    private final BusinessCalendar businessCalendar;
    private final SettlementProperties properties;

    /** 행이 보류가 아니거나 사유를 알 수 없으면 null. */
    public PayoutHoldView of(Settlement settlement, SettlementPayout payout) {
        if (payout == null || payout.getStatus() != PayoutStatus.BLOCKED) {
            return null;
        }
        return reasonOf(settlement, payout)
                .map(reason -> PayoutHoldView.of(reason, originalDueDate(settlement, payout),
                        properties.getPayoutBusinessDays()))
                .orElse(null);
    }

    private Optional<PayoutBlockReason> reasonOf(Settlement settlement, SettlementPayout payout) {
        if (payout.getFailCode() != null) {
            return PayoutBlockReason.fromCode(payout.getFailCode());
        }
        List<PayoutBlockReason> reasons = switch (payout.getPayee()) {
            case CREATOR -> blockPolicy.blockReasons(settlement);
            case BRAND -> blockPolicy.brandBlockReasons(settlement);
            case PLATFORM -> List.of();
        };
        return reasons.stream().findFirst();
    }

    private LocalDate originalDueDate(Settlement settlement, SettlementPayout payout) {
        if (payout.getPayee() == SettlementPayee.CREATOR && settlement.isBusinessCreator()) {
            return documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(settlement.getId(),
                            TaxDocumentType.CREATOR_TAX_INVOICE)
                    .filter(d -> d.getStatus() == TaxDocumentStatus.VERIFIED && d.getVerifiedAt() != null)
                    .map(d -> businessCalendar.addBusinessDays(KstDates.toKstDate(d.getVerifiedAt()),
                            properties.getPayoutBusinessDays()))
                    .orElse(null);
        }
        return settlement.getPayoutDueDate();
    }
}
