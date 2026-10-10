package showroomz.api.common.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.settlement.dto.SettlementPartyDto;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader.AdjustmentSummary;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader.ProposalView;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader;
import showroomz.domain.settlement.adjustment.type.AdjustmentTurn;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.domain.settlement.entity.SettlementHistory;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.repository.SettlementHistoryRepository;
import showroomz.domain.settlement.repository.SettlementItemRepository;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.utils.PersonalDataCipher;
import showroomz.global.utils.SettlementAccountMasker;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 파트너 13 · 스튜디오 12 조회가 함께 쓰는 조각(44 파트너 · 스튜디오 설계서) — 수취자 행 묶음 · 리워드율 표기 · 확정 근거 문장 ·
 * 계좌 마스킹 · 상태 칩 숫자. 두 서피스가 같은 행을 같은 규칙으로 읽게 한 곳에 둔다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementPartyViews {

    private static final List<SettlementEventType> CONFIRM_EVENTS = List.of(SettlementEventType.AUTO_CONFIRMED,
            SettlementEventType.CONFIRMED_BY_AGREEMENT, SettlementEventType.CONFIRMED_BY_EXPIRY);

    private final SettlementPayoutRepository payoutRepository;
    private final SettlementItemRepository itemRepository;
    private final SettlementHistoryRepository historyRepository;
    private final PersonalDataCipher cipher;
    private final SettlementAdjustmentReader adjustmentReader;

    /** 상품별 리워드율 — 「상품별」 주석용. */
    public record RewardRate(String productName, BigDecimal rate) {
    }

    /** 정산 id → 수취자 → 행. */
    public Map<Long, Map<SettlementPayee, SettlementPayout>> payoutsOf(Collection<Long> settlementIds) {
        Map<Long, Map<SettlementPayee, SettlementPayout>> result = new HashMap<>();
        if (settlementIds.isEmpty()) {
            return result;
        }
        for (SettlementPayout payout : payoutRepository.findBySettlementIdIn(settlementIds)) {
            result.computeIfAbsent(payout.getSettlementId(), id -> new EnumMap<>(SettlementPayee.class))
                    .put(payout.getPayee(), payout);
        }
        return result;
    }

    public Map<SettlementPayee, SettlementPayout> payoutsOf(Long settlementId) {
        return payoutsOf(List.of(settlementId)).getOrDefault(settlementId, new EnumMap<>(SettlementPayee.class));
    }

    /** 정산 id → 상품별 리워드율(상품명 순서는 명세와 무관). */
    public Map<Long, List<RewardRate>> rewardRatesOf(Collection<Long> settlementIds) {
        Map<Long, List<RewardRate>> result = new HashMap<>();
        if (settlementIds.isEmpty()) {
            return result;
        }
        for (Object[] row : itemRepository.findRewardRates(settlementIds)) {
            result.computeIfAbsent((Long) row[0], id -> new ArrayList<>())
                    .add(new RewardRate((String) row[2], (BigDecimal) row[1]));
        }
        return result;
    }

    /** 「15%」 · 상품마다 다르면 「상품별」 · 명세가 없으면 null. */
    public static String rewardRateLabel(List<RewardRate> rates) {
        if (rates == null || rates.isEmpty()) {
            return null;
        }
        List<BigDecimal> distinct = rates.stream().map(RewardRate::rate).map(BigDecimal::stripTrailingZeros)
                .distinct().toList();
        return distinct.size() > 1 ? "상품별" : percent(distinct.get(0));
    }

    /** 상품명별 율 하나씩 — 「상품별」일 때의 주석 목록. */
    public static List<RewardRate> distinctByProduct(List<RewardRate> rates) {
        Map<String, RewardRate> byProduct = new LinkedHashMap<>();
        if (rates != null) {
            rates.forEach(rate -> byProduct.putIfAbsent(rate.productName(), rate));
        }
        return List.copyOf(byProduct.values());
    }

    public static String percent(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString() + "%";
    }

    /**
     * 확정 근거 — 「자동 확정 10.06 + 3영업일 · 10.09 한글날 제외」. 확정 이력 detail 의 문장이다(어드민 설계서 3-2 #4) — 공휴일
     * 테이블이 나중에 바뀌어도 확정 때 적힌 문장 그대로다. 확정 전이면 null.
     */
    public String payoutBasis(Long settlementId) {
        return historyRepository.findByTypesLatestFirst(settlementId, CONFIRM_EVENTS).stream()
                .findFirst().map(SettlementHistory::getDetail).orElse(null);
    }

    /** 지시 시점 스냅샷의 계좌 — 뒤 6자리 노출(공통 결정 #14). */
    public String maskedSnapshotAccount(SettlementPayout payout) {
        return payout == null || payout.getAccountNumberEnc() == null ? null
                : SettlementAccountMasker.mask(cipher.decrypt(payout.getAccountNumberEnc()));
    }

    /** 조정 내역 블록(44 이슈 스레드 설계서 1-7) — 협의가 없으면 null. 차례 · 「내 · 상대 최신 제안」은 뷰어 기준이다. */
    public SettlementPartyDto.AdjustmentBlock adjustmentOf(Long settlementId, SettlementParty viewer) {
        return adjustmentReader.findBySettlementId(settlementId).map(a -> adjustmentBlock(a, viewer)).orElse(null);
    }

    private static SettlementPartyDto.AdjustmentBlock adjustmentBlock(AdjustmentSummary a, SettlementParty viewer) {
        AdjustmentTurn turn = SettlementAdjustmentReader.turnFor(a, viewer);
        Optional<ProposalView> mine = a.latestOf(viewer);
        Optional<ProposalView> theirs = a.latestOf(viewer.counterpart());
        return new SettlementPartyDto.AdjustmentBlock(a.adjustmentId(), a.threadId(), a.status().name(),
                a.status().getLabel(), a.requesterType().name(), a.openedAt(), a.deadlineAt(),
                a.remainingBusinessDays(), a.originalRewardAmount(), a.maxRewardAmount(), a.agreedRewardAmount(),
                a.finalRewardAmount(), mine.map(ProposalView::rewardAmount).orElse(null),
                theirs.map(ProposalView::rewardAmount).orElse(null), theirs.map(ProposalView::proposedAt).orElse(null),
                turn.name(), turn.getLabel(), turn.getTone().name(),
                a.proposals().stream().map(p -> new SettlementPartyDto.AdjustmentProposal(p.seq(),
                        p.proposerType().name(), p.proposerType() == viewer, p.rewardAmount(), p.reason(),
                        p.status().name(), p.proposedAt(), p.respondedAt())).toList());
    }

    /** 상태 칩 숫자 — 수취자 화면은 분배 실패를 지급 완료로 접는다(0-4). 0 인 상태도 키가 있다. */
    public static Map<SettlementStatus, Long> partyStatusCounts(List<Object[]> rows) {
        Map<SettlementStatus, Long> counts = new LinkedHashMap<>();
        for (SettlementStatus status : List.of(SettlementStatus.REVIEWING, SettlementStatus.ADJUSTING,
                SettlementStatus.PAYOUT_SCHEDULED, SettlementStatus.PAID)) {
            counts.put(status, 0L);
        }
        for (Object[] row : rows) {
            SettlementStatus status = ((SettlementStatus) row[0]).toPartyStatus();
            counts.merge(status, ((Number) row[1]).longValue(), Long::sum);
        }
        return counts;
    }
}
