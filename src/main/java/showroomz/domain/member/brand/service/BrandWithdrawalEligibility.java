package showroomz.domain.member.brand.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.brand.type.BrandWithdrawalCondition;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.settlement.repository.SettlementClawbackRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.global.error.exception.ConditionUnmetException;
import showroomz.global.error.exception.ErrorCode;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 브랜드 탈퇴 조건 판정식(§51-5 · 52 설계서 4-1) — <b>하나</b>다. 파트너 신청 화면의 체크리스트와 어드민 승인 직전 재검증이 같은 메서드를 쓴다
 * (신청 이후 새 주문 · 조정 요청이 생긴 건을 승인 직전에 잡는다).
 *
 * <ol>
 *   <li>진행 중 · 준비 중 공구 — 준비중 · 준비완료 · 진행중 · 중단 예정. 종료 후 주문 종결 대기는 ③에서 걸린다</li>
 *   <li>체결 전 계약 — 검토 대기 · 검토 반려 · 서명 진행중 · 체결 처리 대기. 작성중은 조건이 아니다(승인 시 함께 삭제)</li>
 *   <li>미종결 주문 — 공구 정산 게이트와 같은 정의</li>
 *   <li>진행 중 정산 — 지급 완료 전 정산 + 회수 대기 차감</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandWithdrawalEligibility {

    private static final Set<ContractStatus> CONTRACT_CONDITION = Set.of(ContractStatus.REVIEW_PENDING,
            ContractStatus.REVIEW_REJECTED, ContractStatus.SIGNING, ContractStatus.CONCLUSION_PENDING);

    private final GroupBuyRepository groupBuyRepository;
    private final ContractRepository contractRepository;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementClawbackRepository clawbackRepository;

    /** 조건별 남은 건수 — 4종 모두 들어 있다(0 포함 · 선언 순서). */
    public Map<BrandWithdrawalCondition, Long> evaluate(Long marketId) {
        Map<BrandWithdrawalCondition, Long> result = new EnumMap<>(BrandWithdrawalCondition.class);
        result.put(BrandWithdrawalCondition.ACTIVE_GROUP_BUYS,
                sum(groupBuyRepository.countByStatus(marketId), GroupBuyStatus.ACTIVE));
        result.put(BrandWithdrawalCondition.CONTRACTS_BEFORE_CONCLUSION,
                sum(contractRepository.countByStatus(marketId), CONTRACT_CONDITION));
        result.put(BrandWithdrawalCondition.UNCLOSED_ORDERS, deliveryGroupRepository.countUnclosedByMarket(marketId));
        result.put(BrandWithdrawalCondition.UNSETTLED, settlementRepository.countUnpaidForMarket(marketId)
                + clawbackRepository.countPendingForMarket(marketId));
        return result;
    }

    /** 미충족이 하나라도 있으면 409 — 응답에 미충족 조건만 싣는다. */
    public void requireEligible(Long marketId) {
        Map<String, Long> unmet = new LinkedHashMap<>();
        evaluate(marketId).forEach((condition, count) -> {
            if (count > 0) {
                unmet.put(condition.name(), count);
            }
        });
        if (!unmet.isEmpty()) {
            throw new ConditionUnmetException(ErrorCode.BRAND_WITHDRAWAL_CONDITION_UNMET, unmet);
        }
    }

    private static long sum(List<Object[]> rows, Set<?> statuses) {
        long total = 0;
        for (Object[] row : rows) {
            if (statuses.contains(row[0])) {
                total += ((Number) row[1]).longValue();
            }
        }
        return total;
    }
}
