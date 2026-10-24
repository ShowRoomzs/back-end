package showroomz.domain.contract.service;

import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.util.Set;

/**
 * 해지 가능 판정(1021 설계서 1-4 · 결정 노트 기-05 【잠정】) — 파트너 [해지 요청] · 어드민 [해지 처리] · 공구 화면 버튼이 같은 판정을 쓴다.
 *
 * <p>계약이 성립했고(공구 생성 대기 · 체결완료) 공구가 아직 오픈 전(없음 · 준비중 · 준비완료)이어야 한다. 오픈 뒤는 공구 중단이다.
 */
public final class ContractTerminationPolicy {

    public static final Set<GroupBuyStatus> GROUP_BUY_BEFORE_OPEN = Set.of(GroupBuyStatus.PREPARING, GroupBuyStatus.READY);

    private ContractTerminationPolicy() {
    }

    /** @param groupBuy 계약의 공구 — 공구 생성 전이면 null */
    public static boolean isTerminable(Contract contract, GroupBuy groupBuy) {
        if (!ContractStatus.TERMINABLE.contains(contract.getStatus())) {
            return false;
        }
        return groupBuy == null || GROUP_BUY_BEFORE_OPEN.contains(groupBuy.getStatus());
    }
}
