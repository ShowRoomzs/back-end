package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * 스튜디오 목록 탭 ↔ 상태 매핑(§27 설계서 1-3).
 *
 * <p><b>{@link ContractTab}과 합치지 않는다.</b> 같은 {@code SIGNING} 탭 코드가 파트너에서는
 * 「서명 진행중 + 체결 처리 대기」 묶음이지만 스튜디오에서는 둘이 쪼개져 각각 서 있다
 * (시안 S1의 탭 바: 「서명 진행중 2」 · 「체결 처리 대기 1」). 하나로 합치고
 * {@code if (surface == CREATOR)}로 갈라놓으면 어느 쪽이 기준인지 읽을 수 없다.
 *
 * <p>작성중 탭은 <b>코드에 존재하지 않는다</b> — 「0건으로 표시」가 아니라 값 자체가 없다(§27-1 #1).
 */
@Getter
@RequiredArgsConstructor
public enum CreatorContractTab {

    /** 도착한 계약 전량 — 상태 집합은 {@link ContractStatus#RECEIVED_BY_CREATOR}와 같다. */
    ALL(ContractStatus.RECEIVED_BY_CREATOR),
    SIGNING(Set.of(ContractStatus.SIGNING)),
    CONCLUSION_PENDING(Set.of(ContractStatus.CONCLUSION_PENDING)),
    CONCLUDED(Set.of(ContractStatus.CONCLUDED)),
    CLOSED(Set.of(ContractStatus.DECLINED, ContractStatus.EXPIRED, ContractStatus.CANCELED));

    private final Set<ContractStatus> statuses;

    /**
     * ALL이라고 해서 상태 조건을 빼지 않는다 — 파트너의 ALL은 「내 브랜드 전량」이지만
     * 스튜디오의 ALL은 「도착한 것 전량」이고, 도착 판정 자체가 상태 집합이다(설계서 1-1).
     */
    public static List<CreatorContractTab> countable() {
        return List.of(values());
    }
}
