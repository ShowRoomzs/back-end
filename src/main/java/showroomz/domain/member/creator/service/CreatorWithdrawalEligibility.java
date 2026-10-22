package showroomz.domain.member.creator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.member.creator.repository.CreatorMemberQueryRepository;
import showroomz.domain.member.creator.type.CreatorWithdrawalCondition;
import showroomz.global.error.exception.ConditionUnmetException;
import showroomz.global.error.exception.ErrorCode;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 인플루언서 탈퇴 조건 판정식(§51-5 · 53 설계서 4-5) — <b>한 메서드</b>다. 스튜디오 체크리스트(S4) · 어드민 상세의 심사 카드 · 승인
 * 트랜잭션의 재검증이 같은 식을 쓴다 — 신청 뒤 상황이 바뀐 건을 승인하지 않기 위해서다.
 *
 * <ol>
 *   <li>끝나지 않은 공구 — 준비중 · 준비완료 · 진행중 · 중단 예정 + 종료 · 중단됐지만 정산이 없고 미종결 주문이 남은 공구(「종료 후 주문 종결
 *   대기」 · 정산 생성 게이트와 같은 {@link GroupBuySalesReader#readClosure} 리더). 판매 0건 종료 공구는 미종결이 0이라 걸리지 않는다.</li>
 *   <li>끝나지 않은 계약 — 서명 진행중 · 공구 생성 대기.</li>
 *   <li>미정산 — 지급 완료가 아닌 정산(조정 협의 · 분배 실패 포함). 지급이 정지되면 이 조건이 풀리지 않는다 — 「정지를 피하려고 탈퇴」하는
 *   길이 없다(§51-3).</li>
 * </ol>
 *
 * <p>차감(클로백) 대기 · 세금계산서 미발행은 따로 세지 않는다 — ③에 포함되고 같은 사실을 두 줄로 세지 않는다(§51-5).
 */
@Component
@RequiredArgsConstructor
public class CreatorWithdrawalEligibility {

    private final CreatorMemberQueryRepository queries;
    private final GroupBuySalesReader salesReader;

    public record Result(Map<CreatorWithdrawalCondition, Long> counts) {

        public boolean eligible() {
            return counts.values().stream().allMatch(count -> count == 0);
        }

        public long count(CreatorWithdrawalCondition condition) {
            return counts.getOrDefault(condition, 0L);
        }

        /** 미충족 조건만 · 선언 순서 — 409 응답 최상위 {@code unmet}(브랜드 {@code BrandWithdrawalEligibility}와 같은 모양). */
        public Map<String, Long> unmet() {
            Map<String, Long> unmet = new LinkedHashMap<>();
            counts.forEach((condition, count) -> {
                if (count > 0) {
                    unmet.put(condition.name(), count);
                }
            });
            return unmet;
        }

        /**
         * 미충족이면 409 {@code CREATOR_WITHDRAWAL_CONDITION_UNMET} — 응답 {@code {code, message, unmet}}. 메시지에는 건수 문장 뒤에
         * {@code hint}(호출 화면의 안내 · 없으면 null)를 붙인다.
         */
        public void requireEligible(String hint) {
            if (!eligible()) {
                throw new ConditionUnmetException(ErrorCode.CREATOR_WITHDRAWAL_CONDITION_UNMET,
                        "탈퇴 조건이 충족되지 않았습니다 — " + summary() + (hint == null ? "" : hint), unmet());
            }
        }

        /** 409 메시지 — 「진행중 공구 1건 · 계약 0건 · 미정산 0건」. */
        public String summary() {
            return "%s %d건 · %s %d건 · %s %d건".formatted(
                    CreatorWithdrawalCondition.UNFINISHED_GROUP_BUYS.getLabel(), count(CreatorWithdrawalCondition.UNFINISHED_GROUP_BUYS),
                    CreatorWithdrawalCondition.UNFINISHED_CONTRACTS.getLabel(), count(CreatorWithdrawalCondition.UNFINISHED_CONTRACTS),
                    CreatorWithdrawalCondition.UNSETTLED.getLabel(), count(CreatorWithdrawalCondition.UNSETTLED));
        }
    }

    @Transactional(readOnly = true)
    public Result evaluate(Long creatorId) {
        Map<CreatorWithdrawalCondition, Long> counts = new EnumMap<>(CreatorWithdrawalCondition.class);
        long groupBuys = queries.countActiveGroupBuys(creatorId) + awaitingOrderClosureGroupBuyIds(creatorId).size();
        counts.put(CreatorWithdrawalCondition.UNFINISHED_GROUP_BUYS, groupBuys);
        counts.put(CreatorWithdrawalCondition.UNFINISHED_CONTRACTS, queries.countOpenContracts(creatorId));
        counts.put(CreatorWithdrawalCondition.UNSETTLED, queries.countUnsettled(creatorId));
        return new Result(counts);
    }

    /**
     * 「종료 후 주문 종결 대기」 공구 — 종료 · 중단됐지만 정산이 없고 미종결 주문이 남았다. 판정(①)과 스튜디오 조건 내역
     * ({@link CreatorWithdrawalChecklist})이 같은 식을 쓰도록 여기 하나만 둔다.
     */
    @Transactional(readOnly = true)
    public List<Long> awaitingOrderClosureGroupBuyIds(Long creatorId) {
        List<Long> ids = new ArrayList<>();
        for (Long groupBuyId : queries.findClosedGroupBuyIdsWithoutSettlement(creatorId)) {
            boolean unclosed = salesReader.readClosure(groupBuyId)
                    .map(closure -> closure.unclosedCount() > 0)
                    .orElse(false);
            if (unclosed) {
                ids.add(groupBuyId);
            }
        }
        return ids;
    }
}
